package io.roastedroot.quickjs4j.binding;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** A Java function callable from JS, with arguments and result mapped by a {@link JsonCodec}. */
public final class TypedHostFunction {
    private final String name;
    private final List<Type> paramTypes;
    private final Type returnType;
    private final Function<List<Object>, Object> fn;

    public TypedHostFunction(
            String name,
            List<? extends Type> paramTypes,
            Type returnType,
            Function<List<Object>, Object> fn) {
        this.name = Objects.requireNonNull(name, "name");
        this.paramTypes = List.copyOf(paramTypes);
        this.returnType = Objects.requireNonNull(returnType, "returnType");
        this.fn = Objects.requireNonNull(fn, "fn");
    }

    public Object invoke(List<Object> args) {
        return fn.apply(args);
    }

    public String name() {
        return name;
    }

    public List<Type> paramTypes() {
        return paramTypes;
    }

    public Type returnType() {
        return returnType;
    }
}
