package io.roastedroot.js;

import java.util.List;
import java.util.function.Function;

public class JsFunction {
    // TODO: check if both name and index are useful
    private final String name;
    private final int index;
    private final List<Class> paramTypes;
    private final Class returnType;

    // function implementation
    private final Function<List<Object>, Object> fn;

    public JsFunction(
            String name,
            int index,
            List<Class> paramTypes,
            Class returnType,
            Function<List<Object>, Object> fn) {
        this.name = name;
        this.index = index;
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

    public int index() {
        return index;
    }

    public List<Class> paramTypes() {
        return paramTypes;
    }

    public Class returnType() {
        return returnType;
    }
}
