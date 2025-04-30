package io.roastedroot.quickjs4j.core;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.dylibso.chicory.experimental.hostmodule.annotations.WasmModuleInterface;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@WasmModuleInterface(WasmResource.absoluteFile)
public final class Engine implements AutoCloseable {
    private static final int ALIGNMENT = 1;
    public static final ObjectMapper DEFAULT_OBJECT_MAPPER = new ObjectMapper();

    private final WasiOptions wasiOpts = WasiOptions.builder().inheritSystem().build();
    private final WasiPreview1 wasi = WasiPreview1.builder().withOptions(wasiOpts).build();
    private final Instance instance;
    private final Engine_ModuleExports exports;

    private final Map<String, Builtins> builtins;
    private final Map<String, Invokables> invokables;
    private final ObjectMapper mapper;

    private final List<Object> javaRefs = new ArrayList<>();

    public static Builder builder() {
        return new Builder();
    }

    private String readJavyString(int ptr, int len) {
        var bytes = instance.memory().readBytes(ptr, len);
        // this.exports.canonicalAbiFree(ptr, len, ALIGNMENT);
        return new String(bytes, UTF_8);
    }

    public Object invokeGuestFunction(
            String moduleName, String name, List<Object> args, String libraryCode) {
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
            // TODO: verify if we don't need the params classes ???
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) {
                    paramsStr.append(", ");
                }
                paramsStr.append(mapper.writeValueAsString(args.get(i)));
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        int codePtr = 0;
        try {
            String jsCode =
                    new String(jsPrelude(), UTF_8)
                            + "\n"
                            + libraryCode
                            + "\n"
                            + new String(jsSuffix(), UTF_8)
                            + "\n"
                            +
                            // "console.log(" + moduleName + "." + guestFunction.name() + "(" +
                            // paramsStr.toString() + "));";
                            moduleName
                            + "."
                            + guestFunction.setResultFunName()
                            + "("
                            + moduleName
                            + "."
                            + guestFunction.name()
                            + "("
                            + paramsStr.toString()
                            + "));";

            // TODO: remove me
            //            System.out.println("Debug invoke function: " + jsCode);
            codePtr = compileRaw(jsCode.getBytes(UTF_8));
            exec(codePtr);
        } finally {
            if (codePtr != 0) {
                free(codePtr);
            }
        }

        return invokables.get(moduleName).byName(name).getResult();
    }

    private long[] invokeBuiltin(Instance instance, long[] args) {
        int modulePtr = (int) args[0];
        int moduleLen = (int) args[1];
        int funcPtr = (int) args[2];
        int funcLen = (int) args[3];
        int argsPtr = (int) args[4];
        int argsLen = (int) args[5];
        try {
            String moduleName = readJavyString(modulePtr, moduleLen);
            String funcName = readJavyString(funcPtr, funcLen);
            String argsString = readJavyString(argsPtr, argsLen);

            if (!builtins.containsKey(moduleName)) {
                throw new IllegalArgumentException(
                        "Failed to find builtin module name " + moduleName);
            }
            if (builtins.get(moduleName).byName(funcName) == null) {
                throw new IllegalArgumentException(
                        "Failed to find function with name "
                                + funcName
                                + " in module "
                                + moduleName);
            }
            var receiver = builtins.get(moduleName).byName(funcName);

            var argsList = new ArrayList<>();
            try {
                JsonNode tree = mapper.readTree(argsString);

                if (tree.size() != receiver.paramTypes().size()) {
                    throw new IllegalArgumentException(
                            "Function "
                                    + receiver.name()
                                    + " has been invoked with the incorrect number of parameters"
                                    + " needs: "
                                    + receiver.paramTypes().stream()
                                            .map(Class::getCanonicalName)
                                            .collect(Collectors.joining(", "))
                                    + ", found: "
                                    + tree.size());
                }

                for (int i = 0; i < tree.size(); i++) {
                    var clazz = receiver.paramTypes().get(i);
                    var value = tree.get(i);

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
        } finally {
            // TODO: enabling those frees breaks everything, double-check
            // this.exports.canonicalAbiFree(modulePtr, moduleLen, ALIGNMENT);
            // this.exports.canonicalAbiFree(funcPtr, funcLen, ALIGNMENT);
            // this.exports.canonicalAbiFree(argsPtr, argsLen, ALIGNMENT);
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

    private Engine(
            Map<String, Builtins> builtins,
            Map<String, Invokables> invokables,
            ObjectMapper mapper,
            Function<MemoryLimits, Memory> memoryFactory) {
        this.mapper = mapper;
        this.builtins = builtins;
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

    // This function is used to dynamically generate the bindings defined by the Builtins
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

    // This function is used to dynamically generate the bindings defined by the Invokables
    private byte[] jsSuffix() {
        var suffixBuilder = new StringBuilder();
        for (Map.Entry<String, Invokables> invokable : invokables.entrySet()) {
            //            The object is already defined by the set_result
            //            suffixBuilder.append("globalThis." + invokable.getKey() + " = {};\n");
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
                // TODO: should I consider an additional Rust builtin?
                // exporting like builtins the "set_result" functions, one each function
                // injecting the builtins from the invokables makes it
                //                suffixBuilder.append(
                //                        "globalThis."
                //                                + invokable.getKey()
                //                                + "."
                //                                + func.setResultFunName()
                //                                + " = (arg) => { java_invoke(\""
                //                                + invokable.getKey()
                //                                + "\", \""
                //                                + func.setResultFunName()
                //                                + "\", JSON.stringify(arg)) };\n");
            }
        }
        return suffixBuilder.toString().getBytes();
    }

    public int compile(String js) {
        return compile(js.getBytes(UTF_8));
    }

    public int compile(byte[] js) {
        byte[] prelude = jsPrelude();
        byte[] suffix = jsSuffix();
        byte[] jsCode = new byte[prelude.length + js.length + suffix.length];
        System.arraycopy(prelude, 0, jsCode, 0, prelude.length);
        System.arraycopy(js, 0, jsCode, prelude.length, js.length);
        System.arraycopy(suffix, 0, jsCode, prelude.length + js.length, suffix.length);

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

            // TODO: debug remove me
            // System.out.println("Final JavaScript:\n" + new String(jsCode, UTF_8));

            return aggregatedCodePtr; // 32 bit
        } catch (TrapException e) {
            throw new IllegalArgumentException(
                    "Failed to compile JS code:\n" + new String(jsCode, UTF_8), e);
        }
    }

    // TODO: can this be refactored?
    public int compileRaw(byte[] js) {
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

            // TODO: debug remove me
            //            System.out.println("Final JavaScript RAW:\n" + new String(jsCode, UTF_8));

            return aggregatedCodePtr; // 32 bit
        } catch (TrapException e) {
            throw new IllegalArgumentException(
                    "Failed to compile JS code:\n" + new String(jsCode, UTF_8), e);
        }
    }

    public void exec(int codePtr) {
        var ptr = exports.memory().readInt(codePtr);
        var codeLength = exports.memory().readInt(codePtr + 4);

        exports.invoke(
                ptr, // bytecode_ptr
                codeLength, // bytecode_len
                0, // fn_name_ptr
                0 // fn_name_len
                );
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
    }

    public static final class Builder {
        private List<Builtins> builtins = new ArrayList<>();
        private List<Invokables> invokables = new ArrayList<>();
        private ObjectMapper mapper;
        private Function<MemoryLimits, Memory> memoryFactory;

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
            return new Engine(finalBuiltins, finalInvokables, mapper, memoryFactory);
        }
    }
}
