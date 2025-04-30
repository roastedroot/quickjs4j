package io.roastedroot.quickjs4j.core;

import java.util.List;
import java.util.function.Function;

public class HostFunction {
    private final String name;
    private final List<Class> paramTypes;
    private final Class returnType;

    // function implementation
    private final Function<List<Object>, Object> fn;

    public HostFunction(
            String name,
            List<Class> paramTypes,
            Class returnType,
            Function<List<Object>, Object> fn) {
        this.name = name;
        this.paramTypes = paramTypes;
        this.returnType = returnType;

        this.fn = fn;
    }

    public Object invoke(List<Object> args) {
        return fn.apply(args);
    }

    public String name() {
        return name;
    }

    public List<Class> paramTypes() {
        return paramTypes;
    }

    public Class returnType() {
        return returnType;
    }
}
