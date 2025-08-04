package io.roastedroot.quickjs4j.core;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.dylibso.chicory.annotations.WasmModuleInterface;
import com.dylibso.chicory.log.Logger;
import com.dylibso.chicory.log.SystemLogger;
import com.dylibso.chicory.runtime.ByteArrayMemory;
import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.runtime.ImportValues;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Memory;
import com.dylibso.chicory.runtime.TrapException;
import com.dylibso.chicory.wasi.WasiOptions;
import com.dylibso.chicory.wasi.WasiPreview1;
import com.dylibso.chicory.wasm.types.MemoryLimits;
import com.dylibso.chicory.wasm.types.ValueType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@WasmModuleInterface(WasmResource.absoluteFile)
public final class Engine implements AutoCloseable {
    private static final int ALIGNMENT = 1;
    public static final ObjectMapper DEFAULT_OBJECT_MAPPER = new ObjectMapper();

    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    private final WasiOptions wasiOpts;

    private final WasiPreview1 wasi;
    private final Instance instance;
    private final Engine_ModuleExports exports;

    private final Map<String, Builtins> builtins;
    private final Map<String, Invokables> invokables;
    private final ObjectMapper mapper;

    private final List<Object> javaRefs = new ArrayList<>();

    private static final String ENGINE_MODULE_NAME = "quickjs4j_engine";
    private static final String MODULE_NAME_FUNC = "module_name";
    private static final String FUNCTION_NAME_FUNC = "function_name";
    private static final String ARGS_FUNC = "args";

    private String invokeModuleName;
    private String invokeFunctionName;
    private String invokeArgs;

    private final ScriptCache cache;

    public static Builder builder() {
        return new Builder();
    }

    private Engine(
            Map<String, Builtins> builtins,
            Map<String, Invokables> invokables,
            ObjectMapper mapper,
            Function<MemoryLimits, Memory> memoryFactory,
            ScriptCache cache,
            Logger logger) {
        this.mapper = mapper;
        this.builtins = builtins;
        this.cache = cache;

        // builtins to make invoke dynamic javascript functions
        builtins.put(
                ENGINE_MODULE_NAME,
                Builtins.builder(ENGINE_MODULE_NAME)
                        .addVoidToString(MODULE_NAME_FUNC, () -> invokeModuleName)
                        .addVoidToString(FUNCTION_NAME_FUNC, () -> invokeFunctionName)
                        .addVoidToString(ARGS_FUNC, () -> invokeArgs)
                        .build());

        var wasiOptsBuilder = WasiOptions.builder().withStdout(stdout).withStderr(stderr);

        this.wasiOpts = wasiOptsBuilder.build();
        this.wasi = WasiPreview1.builder().withOptions(this.wasiOpts).withLogger(logger).build();
        // set_result builtins
        invokables.entrySet().stream()
                .forEach(
                        e -> {
                            var builder = Builtins.builder(e.getKey());
                            e.getValue()
                                    .functions()
                                    .forEach(entry -> builder.add(entry.setResultHostFunction()));
                            this.builtins.put(e.getKey(), builder.build());
                        });
        this.invokables = invokables;
        instance =
                Instance.builder(JavyPluginModule.load())
                        .withMemoryFactory(memoryFactory)
                        .withMachineFactory(JavyPluginModule::create)
                        .withImportValues(
                                ImportValues.builder()
                                        .addFunction(wasi.toHostFunctions())
                                        .addFunction(invokeFn)
                                        .build())
                        .build();
        exports = new Engine_ModuleExports(instance);
        exports.initializeRuntime();
    }

    private String readJavyString(int ptr, int len) {
        var bytes = instance.memory().readBytes(ptr, len);
        return new String(bytes, UTF_8);
    }

    public Object invokeGuestFunction(
            String moduleName, String name, List<Object> args, String libraryCode) {
        return invokePrecompiledGuestFunction(
                moduleName, name, args, compilePortableGuestFunction(libraryCode));
    }

    public String invokeFunction() {
        var funInvoke =
                "globalThis[quickjs4j_engine.module_name()][quickjs4j_engine.function_name()](...JSON.parse(quickjs4j_engine.args()))";
        Function<String, String> setResult =
                (value) ->
                        "java_invoke(quickjs4j_engine.module_name(),"
                                + " quickjs4j_engine.function_name() + \"_set_result\","
                                + " JSON.stringify(["
                                + value
                                + "]))";

        return "Promise.resolve("
                + funInvoke
                + ").then((value) => { "
                + setResult.apply("value")
                + " }, (err) => { throw err; })";
    }

    // Plan:
    // we compile a static version of the module and we invoke it parametrically using a basic
    // protocol
    // { moduleName: "..", functionName: "..", args: "stringified args" }
    public byte[] compilePortableGuestFunction(String libraryCode) {
        int codePtr = 0;
        try {
            var invokeFunction = invokeFunction();

            String jsCode =
                    new String(jsPrelude(), UTF_8)
                            + "\n"
                            + libraryCode
                            + "\n"
                            + new String(jsSuffix(), UTF_8)
                            + "\n"
                            + invokeFunction
                            + ";\n";

            // System.out.println(jsCode);

            codePtr = compileRaw(jsCode.getBytes(UTF_8));
            return readCompiled(codePtr);
        } finally {
            if (codePtr != 0) {
                free(codePtr);
            }
        }
    }

    private String computeArgs(String moduleName, String name, List<Object> args) {
        GuestFunction guestFunction = invokables.get(moduleName).byName(name);
        if (guestFunction.paramTypes().size() != args.size()) {
            throw new IllegalArgumentException(
                    "Guest function should be invoked with the expected "
                            + guestFunction.paramTypes().size()
                            + " params, but got: "
                            + args.size());
        }
        StringBuilder paramsStr = new StringBuilder();
        try {
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) {
                    paramsStr.append(", ");
                }
                var clazz = guestFunction.paramTypes().get(i);
                if (clazz == HostRef.class) {
                    javaRefs.add(args.get(i));
                    var ptr = javaRefs.size() - 1;
                    paramsStr.append(mapper.writeValueAsString(ptr));
                } else {
                    paramsStr.append(mapper.writeValueAsString(args.get(i)));
                }
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return "[" + paramsStr + "]";
    }

    public Object invokePrecompiledGuestFunction(
            String moduleName, String name, List<Object> args, byte[] compiledCode) {
        int codePtr = 0;
        try {
            this.invokeModuleName = moduleName;
            this.invokeFunctionName = name;
            this.invokeArgs = computeArgs(moduleName, name, args);
            codePtr = writeCompiled(compiledCode);
            exec(codePtr);
        } finally {
            if (codePtr != 0) {
                free(codePtr);
            }
        }

        return invokables.get(moduleName).byName(name).getResult();
    }

    private long[] invokeBuiltin(Instance instance, long[] args) {
        String moduleName = readJavyString((int) args[0], (int) args[1]);
        String funcName = readJavyString((int) args[2], (int) args[3]);
        String argsString = readJavyString((int) args[4], (int) args[5]);

        if (!builtins.containsKey(moduleName)) {
            throw new IllegalArgumentException("Failed to find builtin module name " + moduleName);
        }
        if (builtins.get(moduleName).byName(funcName) == null) {
            throw new IllegalArgumentException(
                    "Failed to find function with name " + funcName + " in module " + moduleName);
        }
        var receiver = builtins.get(moduleName).byName(funcName);

        var argsList = new ArrayList<>();
        try {
            JsonNode tree = mapper.readTree(argsString);

            for (int i = 0; i < receiver.paramTypes().size(); i++) {
                var clazz = receiver.paramTypes().get(i);
                JsonNode value = null;
                if (tree.size() > i) {
                    value = tree.get(i);
                }

                if (clazz == HostRef.class) {
                    argsList.add(javaRefs.get(value.intValue()));
                } else {
                    argsList.add(mapper.treeToValue(value, clazz));
                }
            }

            var res = receiver.invoke(argsList);

            // Converting Java references into pointers for JS
            var returnType = receiver.returnType();
            if (returnType == HostRef.class) {
                returnType = Integer.class;
                if (res instanceof HostRef) {
                    res = ((HostRef) res).pointer();
                } else {
                    javaRefs.add(res);
                    res = javaRefs.size() - 1;
                }
            }

            var returnStr =
                    (returnType == Void.class)
                            ? "null"
                            : mapper.writerFor(returnType).writeValueAsString(res);
            var returnBytes = returnStr.getBytes();

            var returnPtr =
                    exports.canonicalAbiRealloc(
                            0, // original_ptr
                            0, // original_size
                            ALIGNMENT, // alignment
                            returnBytes.length // new size
                            );
            exports.memory().write(returnPtr, returnBytes);

            var LEN = 8;
            var widePtr =
                    exports.canonicalAbiRealloc(
                            0, // original_ptr
                            0, // original_size
                            ALIGNMENT, // alignment
                            LEN // new size
                            );

            instance.memory().writeI32(widePtr, returnPtr);
            instance.memory().writeI32(widePtr + 4, returnBytes.length);

            return new long[] {widePtr};
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private final HostFunction invokeFn =
            new HostFunction(
                    "chicory",
                    "invoke",
                    List.of(
                            ValueType.I32,
                            ValueType.I32,
                            ValueType.I32,
                            ValueType.I32,
                            ValueType.I32,
                            ValueType.I32),
                    List.of(ValueType.I32),
                    this::invokeBuiltin);

    // This function dynamically generates the global functions defined by the Builtins
    private byte[] jsPrelude() {
        var preludeBuilder = new StringBuilder();
        for (Map.Entry<String, Builtins> builtin : builtins.entrySet()) {
            preludeBuilder.append("globalThis." + builtin.getKey() + " = {};\n");
            for (var func : builtins.get(builtin.getKey()).functions()) {
                preludeBuilder.append(
                        "globalThis."
                                + builtin.getKey()
                                + "."
                                + func.name()
                                + " = (...args) => { return JSON.parse(java_invoke(\""
                                + builtin.getKey()
                                + "\", \""
                                + func.name()
                                + "\", JSON.stringify(args))) };\n");
            }
        }
        return preludeBuilder.toString().getBytes();
    }

    // This function dynamically generates the js handlers for Invokables
    private byte[] jsSuffix() {
        var suffixBuilder = new StringBuilder();
        for (Map.Entry<String, Invokables> invokable : invokables.entrySet()) {
            // The object is already defined by the set_result, just add the handlers
            for (var func : invokables.get(invokable.getKey()).functions()) {
                // exporting to global the functions
                suffixBuilder.append(
                        "globalThis."
                                + invokable.getKey()
                                + "."
                                + func.name()
                                + " = "
                                + func.globalName()
                                + ";\n");
            }
        }
        return suffixBuilder.toString().getBytes();
    }

    public int compile(String js) {
        return compile(js.getBytes(UTF_8));
    }

    public int compile(byte[] js) {
        byte[] prelude = jsPrelude();
        byte[] jsCode = new byte[prelude.length + js.length];
        System.arraycopy(prelude, 0, jsCode, 0, prelude.length);
        System.arraycopy(js, 0, jsCode, prelude.length, js.length);

        return compileRaw(jsCode);
    }

    public int compileRaw(byte[] js) {
        if (cache.exists(js)) {
            return writeCompiled(cache.get(js));
        }

        byte[] jsCode = js;

        var ptr =
                exports.canonicalAbiRealloc(
                        0, // original_ptr
                        0, // original_size
                        ALIGNMENT, // alignment
                        jsCode.length // new size
                        );

        exports.memory().write(ptr, jsCode);
        try {
            var aggregatedCodePtr = exports.compileSrc(ptr, jsCode.length);
            exports.canonicalAbiFree(
                    ptr, // ptr
                    jsCode.length, // length
                    ALIGNMENT // alignement
                    );

            // TODO: debug
            // System.out.println("Final JavaScript RAW:\n" + new String(jsCode, UTF_8));

            cache.set(js, readCompiled(aggregatedCodePtr));

            return aggregatedCodePtr; // 32 bit
        } catch (TrapException e) {
            try {
                stderr.flush();
                stdout.flush();
            } catch (IOException ex) {
                throw new RuntimeException("Failed to flush stdout/stderr");
            }

            throw new IllegalArgumentException(
                    "Failed to compile JS code:\n"
                            + new String(jsCode, UTF_8)
                            + "\nstderr: "
                            + stderr.toString(UTF_8)
                            + "\nstdout: "
                            + stdout.toString(UTF_8),
                    e);
        }
    }

    public void exec(int codePtr) {
        var ptr = exports.memory().readInt(codePtr);
        var codeLength = exports.memory().readInt(codePtr + 4);

        try {
            exports.invoke(
                    ptr, // bytecode_ptr
                    codeLength, // bytecode_len
                    0, // fn_name_ptr
                    0 // fn_name_len
                    );
        } catch (TrapException e) {
            try {
                stderr.flush();
                stdout.flush();
            } catch (IOException ex) {
                throw new RuntimeException("Failed to flush stdout/stderr");
            }

            throw new GuestException(
                    "An exception occurred during the execution.\nstderr: "
                            + stderr.toString(UTF_8)
                            + "\nstdout: "
                            + stdout.toString(UTF_8));
        }
    }

    public String stdout() {
        try {
            stdout.flush();
        } catch (IOException ex) {
            throw new RuntimeException("Failed to flush stdout");
        }

        return stdout.toString(UTF_8);
    }

    public String stderr() {
        try {
            stderr.flush();
        } catch (IOException ex) {
            throw new RuntimeException("Failed to flush stdout");
        }

        return stderr.toString(UTF_8);
    }

    public void free(int codePtr) {
        var ptr = exports.memory().readInt(codePtr);
        var codeLength = exports.memory().readInt(codePtr + 4);

        exports.canonicalAbiFree(
                ptr, // ptr
                codeLength, // length
                ALIGNMENT // alignement
                );
    }

    public byte[] readCompiled(int codePtr) {
        var ptr = exports.memory().readInt(codePtr);
        var codeLength = exports.memory().readInt(codePtr + 4);

        return exports.memory().readBytes(ptr, codeLength);
    }

    public int writeCompiled(byte[] jsBytecode) {
        var ptr =
                exports.canonicalAbiRealloc(
                        0, // original_ptr
                        0, // original_size
                        ALIGNMENT, // alignment
                        8 // new size
                        );

        var codePtr =
                exports.canonicalAbiRealloc(
                        0, // original_ptr
                        0, // original_size
                        ALIGNMENT, // alignment
                        jsBytecode.length // new size
                        );

        exports.memory().write(codePtr, jsBytecode);

        exports.memory().writeI32(ptr, codePtr);
        exports.memory().writeI32(ptr + 4, jsBytecode.length);

        return ptr;
    }

    @Override
    public void close() {
        if (wasi != null) {
            wasi.close();
        }
        if (stdout != null) {
            try {
                stdout.flush();
                stdout.close();
            } catch (IOException e) {
                throw new RuntimeException("Failed to close stdout", e);
            }
        }
        if (stderr != null) {
            try {
                stderr.flush();
                stderr.close();
            } catch (IOException e) {
                throw new RuntimeException("Failed to close stderr", e);
            }
        }
    }

    public static final class Builder {
        private List<Builtins> builtins = new ArrayList<>();
        private List<Invokables> invokables = new ArrayList<>();
        private ObjectMapper mapper;
        private Function<MemoryLimits, Memory> memoryFactory;
        private ScriptCache cache;
        private Logger logger;

        private Builder() {}

        public Builder addBuiltins(Builtins builtins) {
            this.builtins.add(builtins);
            return this;
        }

        public Builder addInvokables(Invokables invokables) {
            this.invokables.add(invokables);
            return this;
        }

        public Builder withObjectMapper(ObjectMapper mapper) {
            this.mapper = mapper;
            return this;
        }

        public Builder withMemoryFactory(Function<MemoryLimits, Memory> memoryFactory) {
            this.memoryFactory = memoryFactory;
            return this;
        }

        public Builder withCache(ScriptCache cache) {
            this.cache = cache;
            return this;
        }

        public Builder withLogger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public Engine build() {
            if (mapper == null) {
                mapper = DEFAULT_OBJECT_MAPPER;
            }
            if (memoryFactory == null) {
                memoryFactory = ByteArrayMemory::new;
            }
            Map<String, Builtins> finalBuiltins = new HashMap<>();
            // TODO: any validation to be done here?
            for (var builtin : builtins) {
                finalBuiltins.put(builtin.moduleName(), builtin);
            }
            Map<String, Invokables> finalInvokables = new HashMap<>();
            // TODO: any validation to be done here?
            for (var invokable : invokables) {
                finalInvokables.put(invokable.moduleName(), invokable);
            }
            if (cache == null) {
                cache = new BasicScriptCache();
            }
            if (logger == null) {
                logger = new SystemLogger();
            }
            return new Engine(finalBuiltins, finalInvokables, mapper, memoryFactory, cache, logger);
        }
    }
}
