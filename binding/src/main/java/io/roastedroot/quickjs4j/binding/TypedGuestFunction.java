package io.roastedroot.quickjs4j.binding;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;

/** A JS function callable from Java, with arguments and result mapped by a {@link JsonCodec}. */
public final class TypedGuestFunction {
    private final String name;
    private final String globalName;
    private final List<Type> paramTypes;
    private final Type returnType;

    public TypedGuestFunction(String name, List<? extends Type> paramTypes, Type returnType) {
        this(name, name, paramTypes, returnType);
    }

    public TypedGuestFunction(
            String name, String globalName, List<? extends Type> paramTypes, Type returnType) {
        this.name = Objects.requireNonNull(name, "name");
        this.globalName = Objects.requireNonNull(globalName, "globalName");
        this.paramTypes = List.copyOf(paramTypes);
        this.returnType = Objects.requireNonNull(returnType, "returnType");
    }

    public String name() {
        return name;
    }

    public String globalName() {
        return globalName;
    }

    public List<Type> paramTypes() {
        return paramTypes;
    }

    public Type returnType() {
        return returnType;
    }
}
