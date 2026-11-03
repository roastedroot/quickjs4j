package io.roastedroot.quickjs4j.binding;

import io.roastedroot.quickjs4j.core.Runner;
import java.util.List;
import java.util.Objects;

/** Invokes guest functions with Java values, mapped through a {@link Binding}. */
public final class TypedRunner implements AutoCloseable {
    private final Binding binding;
    private final Runner runner;

    TypedRunner(Binding binding, Runner runner) {
        this.binding = binding;
        this.runner = Objects.requireNonNull(runner, "runner");
    }

    public Object invokeGuestFunction(
            String moduleName, String name, List<Object> args, String libraryCode) {
        var json =
                runner.invokeGuestFunction(
                        moduleName, name, binding.encodeArgs(moduleName, name, args), libraryCode);
        return binding.decodeResult(moduleName, name, json);
    }

    public Object invokePrecompiledGuestFunction(
            String moduleName, String name, List<Object> args, byte[] compiledCode) {
        var json =
                runner.invokePrecompiledGuestFunction(
                        moduleName, name, binding.encodeArgs(moduleName, name, args), compiledCode);
        return binding.decodeResult(moduleName, name, json);
    }

    public Binding binding() {
        return binding;
    }

    public Runner runner() {
        return runner;
    }

    @Override
    public void close() {
        runner.close();
    }
}
