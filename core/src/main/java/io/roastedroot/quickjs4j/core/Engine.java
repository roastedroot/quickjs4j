package io.roastedroot.quickjs4j.core;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import run.endive.log.Logger;
import run.endive.log.SystemLogger;
import run.endive.runtime.ByteArrayMemory;
import run.endive.runtime.ImportValues;
import run.endive.runtime.Instance;
import run.endive.runtime.Memory;
import run.endive.runtime.TrapException;
import run.endive.wasi.WasiOptions;
import run.endive.wasi.WasiPreview1;
import run.endive.wasm.types.MemoryLimits;
import run.endive.wasm.types.ValueType;

public final class Engine implements AutoCloseable {
    private static final int ALIGNMENT = 1;

    private static final String NULL = "null";

    // 256 KiB, QuickJS's default
    private static final long DEFAULT_GC_THRESHOLD = 256 * 1024;

    private final ByteArrayOutputStream stdout;
    private final ByteArrayOutputStream stderr;
    private final WasiOptions wasiOpts;

    private final WasiPreview1 wasi;
    private final Instance instance;
    private final Engine_ModuleExports exports;

    private final Map<String, Builtins> builtins;
    private final Map<String, Invokables> invokables;

    private static final String ENGINE_MODULE_NAME = "quickjs4j_engine";
    private static final String MODULE_NAME_FUNC = "module_name";
    private static final String FUNCTION_NAME_FUNC = "function_name";
    private static final String ARGS_FUNC = "args";

    private String invokeModuleName;
    private String invokeFunctionName;
    private String invokeArgs;
    private String invokeResult;

    private final ScriptCache cache;

    private int jsPreludeLineCount;
    private String jsPrelude;
    private String jsSuffix;
    private String jsEpilogue;

    public static Builder builder() {
        return new Builder();
    }

    private Engine(
            Map<String, Builtins> builtins,
            Map<String, Invokables> invokables,
            Function<MemoryLimits, Memory> memoryFactory,
            ScriptCache cache,
            Logger logger,
            ByteArrayOutputStream stdout,
            ByteArrayOutputStream stderr,
            long gcThreshold) {
        this.builtins = builtins;
        this.cache = cache;
        this.stdout = stdout;
        this.stderr = stderr;

        // builtins to make invoke dynamic javascript functions
        builtins.put(
                ENGINE_MODULE_NAME,
                Builtins.builder(ENGINE_MODULE_NAME)
                        .add(MODULE_NAME_FUNC, args -> quote(invokeModuleName))
                        .add(FUNCTION_NAME_FUNC, args -> quote(invokeFunctionName))
                        .add(ARGS_FUNC, args -> invokeArgs)
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
                                    .forEach(
                                            fn ->
                                                    builder.add(
                                                            fn.setResultFunName(),
                                                            args -> {
                                                                invokeResult =
                                                                        args.isEmpty()
                                                                                ? NULL
                                                                                : args.get(0);
                                                                return null;
                                                            }));
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
        exports.initializeRuntime(gcThreshold);
        buildJsPrelude();
        buildJsSuffix();
        buildJsEpilogue();
    }

    private String readJavyString(int ptr, int len) {
        var bytes = instance.memory().readBytes(ptr, len);
        return new String(bytes, UTF_8);
    }

    /** {@code args} and the result are JSON texts. */
    public String invokeGuestFunction(
            String moduleName, String name, List<String> args, String libraryCode) {
        return invokeGuestFunction(moduleName, name, args, libraryCode.getBytes(UTF_8));
    }

    public String invokeGuestFunction(
            String moduleName, String name, List<String> args, byte[] libraryCode) {
        return invokePrecompiledGuestFunction(
                moduleName, name, args, compilePortableGuestFunction(libraryCode));
    }

    // Calls the requested guest function without reading any global the guest code can replace
    private void buildJsEpilogue() {
        var guests = new StringBuilder();
        for (var invokable : invokables.values()) {
            guests.append('[').append(quote(invokable.moduleName())).append("]: {");
            for (var func : invokable.functions()) {
                guests.append('[')
                        .append(quote(func.name()))
                        .append("]: ")
                        .append(func.globalName())
                        .append(", ");
            }
            guests.append("}, ");
        }
        jsEpilogue =
                "{\n"
                        + "const __quickjs4j_invoke = java_invoke;\n"
                        + "const __quickjs4j_engine = (fn) => JSON.parse(__quickjs4j_invoke("
                        + quote(ENGINE_MODULE_NAME)
                        + ", fn, \"[]\"));\n"
                        + "const __quickjs4j_guests = {"
                        + guests
                        + "};\n"
                        + "const __quickjs4j_module = __quickjs4j_engine("
                        + quote(MODULE_NAME_FUNC)
                        + ");\n"
                        + "const __quickjs4j_function = __quickjs4j_engine("
                        + quote(FUNCTION_NAME_FUNC)
                        + ");\n"
                        + "Promise.resolve(__quickjs4j_guests[__quickjs4j_module][__quickjs4j_function]"
                        + "(...__quickjs4j_engine("
                        + quote(ARGS_FUNC)
                        + "))).then((value) => { __quickjs4j_invoke(__quickjs4j_module,"
                        + " __quickjs4j_function + \"_set_result\", JSON.stringify([value])); },"
                        + " (err) => { throw err; });\n"
                        + "}\n";
    }

    // Plan:
    // we compile a static version of the module and we invoke it parametrically using a basic
    // protocol
    // { moduleName: "..", functionName: "..", args: "stringified args" }
    public byte[] compilePortableGuestFunction(byte[] libraryCode) {
        return compilePortableGuestFunction(new String(libraryCode, UTF_8));
    }

    public byte[] compilePortableGuestFunction(String libraryCode) {
        int codePtr = 0;
        try {
            var buf = new StringBuilder();
            buf.append(jsPrelude);
            buf.append(libraryCode);
            buf.append('\n');
            buf.append(jsSuffix);
            buf.append(jsEpilogue);

            codePtr = compileRaw(buf.toString().getBytes(UTF_8));
            return readCompiled(codePtr);
        } finally {
            if (codePtr != 0) {
                free(codePtr);
            }
        }
    }

    private String computeArgs(String moduleName, String name, List<String> args) {
        var module = invokables.get(moduleName);
        if (module == null || module.byName(name) == null) {
            throw new IllegalArgumentException(
                    "Failed to find guest function with name " + name + " in module " + moduleName);
        }
        var json = new StringBuilder("[");
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            var arg = args.get(i);
            json.append(arg == null ? NULL : arg);
        }
        return json.append(']').toString();
    }

    public String invokePrecompiledGuestFunction(
            String moduleName, String name, List<String> args, byte[] compiledCode) {
        int codePtr = 0;
        try {
            this.invokeModuleName = moduleName;
            this.invokeFunctionName = name;
            this.invokeArgs = computeArgs(moduleName, name, args);
            this.invokeResult = null;
            codePtr = writeCompiled(compiledCode);
            exec(codePtr);
            return invokeResult;
        } finally {
            this.invokeResult = null;
            if (codePtr != 0) {
                free(codePtr);
            }
        }
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

        var result = receiver.invoke(splitArgs(argsString));
        var returnBytes = (result == null ? NULL : result).getBytes(UTF_8);

        var returnPtr =
                exports.cabiRealloc(
                        0, // original_ptr
                        0, // original_size
                        ALIGNMENT, // alignment
                        returnBytes.length // new size
                        );
        exports.memory().write(returnPtr, returnBytes);

        var LEN = 8;
        var widePtr =
                exports.cabiRealloc(
                        0, // original_ptr
                        0, // original_size
                        ALIGNMENT, // alignment
                        LEN // new size
                        );

        instance.memory().writeI32(widePtr, returnPtr);
        instance.memory().writeI32(widePtr + 4, returnBytes.length);

        return new long[] {widePtr};
    }

    // Only delimits the elements, they are validated when deserialized
    static List<String> splitArgs(String json) {
        int start = skipWhitespace(json, 0);
        int end = json.length();
        while (end > start && isWhitespace(json.charAt(end - 1))) {
            end--;
        }
        if (end - start < 2 || json.charAt(start) != '[' || json.charAt(end - 1) != ']') {
            throw malformedArgs(json);
        }
        var result = new ArrayList<String>();
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        int elementStart = start + 1;
        for (int i = start + 1; i < end - 1; i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '[' || c == '{') {
                depth++;
            } else if (c == ']' || c == '}') {
                if (--depth < 0) {
                    throw malformedArgs(json);
                }
            } else if (c == ',' && depth == 0) {
                result.add(element(json, elementStart, i));
                elementStart = i + 1;
            }
        }
        if (inString || depth != 0) {
            throw malformedArgs(json);
        }
        if (result.isEmpty() && skipWhitespace(json, elementStart) == end - 1) {
            return List.of();
        }
        result.add(element(json, elementStart, end - 1));
        return Collections.unmodifiableList(result);
    }

    private static String element(String json, int start, int end) {
        start = skipWhitespace(json, start);
        while (end > start && isWhitespace(json.charAt(end - 1))) {
            end--;
        }
        if (start == end) {
            throw malformedArgs(json);
        }
        return json.substring(start, end);
    }

    private static int skipWhitespace(String json, int index) {
        while (index < json.length() && isWhitespace(json.charAt(index))) {
            index++;
        }
        return index;
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    private static IllegalArgumentException malformedArgs(String json) {
        var preview = json.length() > 100 ? json.substring(0, 100) + "..." : json;
        return new IllegalArgumentException("Malformed host function arguments: " + preview);
    }

    private static String quote(String identifier) {
        return identifier == null ? NULL : "\"" + identifier + "\"";
    }

    private final run.endive.runtime.HostFunction invokeFn =
            new run.endive.runtime.HostFunction(
                    "endive",
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

    // Number of lines prepended to user code by the engine
    public int jsPreludeLineCount() {
        return jsPreludeLineCount;
    }

    // This function dynamically generates the global functions defined by the Builtins
    private void buildJsPrelude() {
        var preludeBuilder = new StringBuilder();
        for (Map.Entry<String, Builtins> builtin : builtins.entrySet()) {
            preludeBuilder.append("globalThis." + builtin.getKey() + " = {};\n");
            jsPreludeLineCount++;
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
                jsPreludeLineCount++;
            }
        }
        jsPrelude = preludeBuilder.toString();
    }

    // This function dynamically generates the js handlers for Invokables
    private void buildJsSuffix() {
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
        jsSuffix = suffixBuilder.toString();
    }

    public int compile(String js) {
        return compile(js.getBytes(UTF_8));
    }

    public int compile(byte[] js) {
        byte[] prelude = jsPrelude.getBytes(UTF_8);
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
                exports.cabiRealloc(
                        0, // original_ptr
                        0, // original_size
                        ALIGNMENT, // alignment
                        jsCode.length // new size
                        );

        exports.memory().write(ptr, jsCode);
        try {
            var aggregatedCodePtr = exports.compileSrc(ptr, jsCode.length);
            exports.abiFree(
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
            exports.eval(
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
            throw new RuntimeException("Failed to flush stderr");
        }

        return stderr.toString(UTF_8);
    }

    public void clearOutput() {
        stdout.reset();
        stderr.reset();
    }

    public void free(int codePtr) {
        var ptr = exports.memory().readInt(codePtr);
        var codeLength = exports.memory().readInt(codePtr + 4);

        exports.abiFree(
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
                exports.cabiRealloc(
                        0, // original_ptr
                        0, // original_size
                        ALIGNMENT, // alignment
                        8 // new size
                        );

        var codePtr =
                exports.cabiRealloc(
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
        private Function<MemoryLimits, Memory> memoryFactory;
        private ScriptCache cache;
        private Logger logger;
        private ByteArrayOutputStream stdout;
        private ByteArrayOutputStream stderr;
        private long gcThreshold = DEFAULT_GC_THRESHOLD;

        private Builder() {}

        public Builder addBuiltins(Builtins builtins) {
            this.builtins.add(builtins);
            return this;
        }

        public Builder addInvokables(Invokables invokables) {
            this.invokables.add(invokables);
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

        public Builder withStdout(ByteArrayOutputStream stdout) {
            this.stdout = stdout;
            return this;
        }

        public Builder withStderr(ByteArrayOutputStream stderr) {
            this.stderr = stderr;
            return this;
        }

        /**
         * Bytes allocated before the first GC pass; later passes trigger at 1.5x live memory.
         * {@link Long#MAX_VALUE} turns GC off.
         */
        public Builder withGcThreshold(long bytes) {
            if (bytes < 0) {
                throw new IllegalArgumentException("gcThreshold must be >= 0, got: " + bytes);
            }
            this.gcThreshold = bytes;
            return this;
        }

        public Engine build() {
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
            if (stdout == null) {
                stdout = new ByteArrayOutputStream();
            }
            if (stderr == null) {
                stderr = new ByteArrayOutputStream();
            }
            return new Engine(
                    finalBuiltins,
                    finalInvokables,
                    memoryFactory,
                    cache,
                    logger,
                    stdout,
                    stderr,
                    gcThreshold);
        }
    }
}
