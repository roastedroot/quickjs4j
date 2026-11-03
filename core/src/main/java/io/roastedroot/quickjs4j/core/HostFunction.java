package io.roastedroot.quickjs4j.core;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** A Java function callable from JS, taking one JSON text per argument and returning JSON. */
public final class HostFunction {
    private final String name;
    private final Function<List<String>, String> fn;

    public HostFunction(String name, Function<List<String>, String> fn) {
        this.name = Identifiers.check(name, "function");
        this.fn = Objects.requireNonNull(fn, "fn");
    }

    public String invoke(List<String> args) {
        return fn.apply(args);
    }

    public String name() {
        return name;
    }
}
