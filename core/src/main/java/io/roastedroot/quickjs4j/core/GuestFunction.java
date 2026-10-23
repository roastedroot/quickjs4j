package io.roastedroot.quickjs4j.core;

/** A JS function callable from Java, exported from the guest code under {@code globalName}. */
public final class GuestFunction {
    private final String name;
    private final String globalName;

    public GuestFunction(String name) {
        this(name, name);
    }

    public GuestFunction(String name, String globalName) {
        this.name = Identifiers.check(name, "function");
        this.globalName = Identifiers.check(globalName, "global");
    }

    public String name() {
        return name;
    }

    public String globalName() {
        return globalName;
    }

    String setResultFunName() {
        return this.name + "_set_result";
    }
}
