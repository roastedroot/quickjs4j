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
import java.util.List;
import java.util.function.Function;

@WasmModuleInterface(WasmResource.absoluteFile)
public final class ChicoryJs implements AutoCloseable {
    private final WasiOptions wasiOpts = WasiOptions.builder().inheritSystem().build();
    private final WasiPreview1 wasi = WasiPreview1.builder().withOptions(wasiOpts).build();
    private final Instance instance;
    private final ChicoryJs_ModuleExports exports;

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

    private ChicoryJs(Function<String, String> importFun) {
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
                                        .build())
                        .build();
        exports = new ChicoryJs_ModuleExports(instance);
        exports.initializeRuntime();
    }

    public int compile(String js) {
        byte[] jsCode = (js).getBytes(UTF_8);
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
        private Function<String, String> importedFunction;

        private Builder() {}

        public Builder withImportedFunction(Function<String, String> imprtFn) {
            this.importedFunction = imprtFn;
            return this;
        }

        public ChicoryJs build() {
            return new ChicoryJs(importedFunction);
        }
    }
}
