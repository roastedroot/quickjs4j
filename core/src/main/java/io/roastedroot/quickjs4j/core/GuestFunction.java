package io.roastedroot.quickjs4j.core;

import java.util.List;

public class GuestFunction {
    private final String name;
    // TODO: this, eventually, enables decoupling from global definitions
    private final String globalName;
    private final List<Class> paramTypes;
    private final Class returnType;
    private final HostFunction setResultHostFunction;

    // this is surely not thread safe ...
    private Object result;

    public GuestFunction(String name, List<Class> paramTypes, Class returnType) {
        this(name, name, paramTypes, returnType);
    }

    public GuestFunction(String name, String globalName, List<Class> paramTypes, Class returnType) {
        this.name = name;
        this.globalName = globalName;
        this.paramTypes = paramTypes;
        this.returnType = returnType;
        this.setResultHostFunction =
                new HostFunction(
                        setResultFunName(),
                        List.of(returnType),
                        Void.class,
                        (args) -> result = args.get(0));
    }

    public String name() {
        return name;
    }

    public String globalName() {
        return globalName;
    }

    public List<Class> paramTypes() {
        return paramTypes;
    }

    public Class returnType() {
        return returnType;
    }

    public String setResultFunName() {
        return this.name + "_set_result";
    }

    public HostFunction setResultHostFunction() {
        return this.setResultHostFunction;
    }

    public Object getResult() {
        return this.result;
    }
}
