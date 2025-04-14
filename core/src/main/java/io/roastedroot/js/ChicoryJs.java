package io.roastedroot.js;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.dylibso.chicory.experimental.hostmodule.annotations.WasmModuleInterface;
import com.dylibso.chicory.runtime.ByteArrayMemory;
import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.runtime.ImportValues;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.wasi.WasiOptions;
import com.dylibso.chicory.wasi.WasiPreview1;
import com.dylibso.chicory.wasm.types.ValueType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@WasmModuleInterface(WasmResource.absoluteFile)
public final class ChicoryJs implements AutoCloseable {
    private final WasiOptions wasiOpts = WasiOptions.builder().inheritSystem().build();
    private final WasiPreview1 wasi = WasiPreview1.builder().withOptions(wasiOpts).build();
    private final Instance instance;
    private final ChicoryJs_ModuleExports exports;

    private final Builtins builtins;
    private final ObjectMapper mapper;

    private static final int ALIGNMENT = 1;

    public static Builder builder() {
        return new Builder();
    }

    private Function<String, String> importFun;

    private long[] importedFunction(Instance instance, long[] args) {
        int ptr = (int) args[0];
        int len = (int) args[1];

        var bytes = instance.memory().readBytes(ptr, len);
        var str = new String(bytes);

        var returnStr = this.importFun.apply(str);
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
    }

    private long[] invoke(Instance instance, long[] args) {
        int proxyPtr = (int) args[0];

        int ptr = (int) args[1];
        int len = (int) args[2];

        var bytes = instance.memory().readBytes(ptr, len);
        this.exports.canonicalAbiFree(ptr, len, ALIGNMENT);
        var argsString = new String(bytes, UTF_8);

        var receiver = builtins.byIndex(proxyPtr);
        if (receiver == null) {
            throw new IllegalArgumentException("Failed to find builtin at index " + proxyPtr);
        }

        var argsList = new ArrayList<Object>();
        try {
            JsonNode tree = mapper.readTree(argsString);

            if (tree.size() != receiver.paramTypes().size()) {
                throw new IllegalArgumentException(
                        "Function "
                                + receiver.name()
                                + " has been invoked with the incorrect number of parameters needs:"
                                + " "
                                + receiver.paramTypes().stream()
                                        .map(Class::getCanonicalName)
                                        .collect(Collectors.joining(", ")));
            }

            for (int i = 0; i < tree.size(); i++) {
                var clazz = receiver.paramTypes().get(i);
                var value = tree.get(i);

                argsList.add(mapper.treeToValue(value, clazz));
            }

            var res = receiver.invoke(argsList);

            var returnStr = mapper.writerFor(receiver.returnType()).writeValueAsString(res);
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

    private ChicoryJs(Function<String, String> importFun, Builtins builtins) {
        // TODO: have proper DI here
        this.mapper = new ObjectMapper();
        // this.mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

        this.builtins = builtins;
        this.importFun = importFun;
        instance =
                Instance.builder(JavyPluginModule.load())
                        .withMemoryFactory(ByteArrayMemory::new)
                        .withMachineFactory(JavyPluginModule::create)
                        .withImportValues(
                                ImportValues.builder()
                                        .addFunction(wasi.toHostFunctions())
                                        .addFunction(
                                                new HostFunction(
                                                        "chicory",
                                                        "imported_function",
                                                        List.of(ValueType.I32, ValueType.I32),
                                                        List.of(ValueType.I32),
                                                        this::importedFunction))
                                        .addFunction(
                                                new HostFunction(
                                                        "chicory",
                                                        "invoke",
                                                        List.of(
                                                                ValueType.I32,
                                                                ValueType.I32,
                                                                ValueType.I32),
                                                        List.of(ValueType.I32),
                                                        this::invoke))
                                        .build())
                        .build();
        exports = new ChicoryJs_ModuleExports(instance);
        exports.initializeRuntime();
    }

    // This function is used to dynamically generate the bindings defined by the Builtins
    private String jsPrelude() {
        var preludeBuilder = new StringBuilder();
        // TODO: if this grows I need a JS writer something
        // TODO: verify JSON.parse
        for (int i = 0; i < builtins.size(); i++) {
            var fun = builtins.byIndex(i);
            preludeBuilder.append(
                    "globalThis."
                            + fun.name()
                            + " = (...args) => { return JSON.parse(java_invoke("
                            + fun.index()
                            + ", JSON.stringify(args) ) ) };\n");
        }
        return preludeBuilder.toString();
    }

    public int compile(String js) {
        byte[] jsCode = (jsPrelude() + js).getBytes(UTF_8);
        var ptr =
                exports.canonicalAbiRealloc(
                        0, // original_ptr
                        0, // original_size
                        ALIGNMENT, // alignment
                        jsCode.length // new size
                        );

        exports.memory().write(ptr, jsCode);
        var aggregatedCodePtr = exports.compileSrc(ptr, jsCode.length);

        return exports.memory().readInt(aggregatedCodePtr); // 32 bit
    }

    public void exec(int codePtr) {
        var codeLength = exports.memory().readInt(codePtr + 4);

        exports.invoke(
                codePtr, // bytecode_ptr
                codeLength, // bytecode_len
                0, // fn_name_ptr
                0 // fn_name_len
                );
    }

    public void free(int codePtr) {
        var codeLength = exports.memory().readInt(codePtr + 4);

        exports.canonicalAbiFree(
                codePtr, // ptr
                codeLength, // length
                ALIGNMENT // alignement
                );
    }

    @Override
    public void close() {
        if (wasi != null) {
            wasi.close();
        }
    }

    public static final class Builder {
        private Builtins builtins;
        private Function<String, String> importedFunction;

        private Builder() {}

        public Builder withBuiltins(Builtins builtins) {
            this.builtins = builtins;
            return this;
        }

        public Builder withImportedFunction(Function<String, String> imprtFn) {
            this.importedFunction = imprtFn;
            return this;
        }

        public ChicoryJs build() {
            return new ChicoryJs(importedFunction, builtins);
        }
    }
}
