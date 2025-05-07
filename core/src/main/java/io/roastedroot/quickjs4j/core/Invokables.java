package io.roastedroot.quickjs4j.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Invokables {
    private final String moduleName;
    private final Map<String, GuestFunction> functions;

    private Invokables(String moduleName, Map<String, GuestFunction> functions) {
        this.moduleName = moduleName;
        this.functions = functions;
    }

    public GuestFunction byName(String name) {
        return functions.get(name);
    }

    public Collection<GuestFunction> functions() {
        return functions.values();
    }

    public String moduleName() {
        return moduleName;
    }

    public static Builder builder(String moduleName) {
        return new Builder(moduleName);
    }

    public static final class Builder {
        private final String moduleName;
        private List<GuestFunction> functions = new ArrayList<>();

        private Builder(String moduleName) {
            this.moduleName = moduleName;
        }

        public Builder add(GuestFunction fun) {
            if (functions.contains(fun.name())) {
                throw new IllegalArgumentException(
                        "A function with name: "
                                + fun.name()
                                + " is already defined in the module: "
                                + moduleName);
            }
            functions.add(fun);
            return this;
        }

        public Builder add(GuestFunction... functions) {
            for (var fun : functions) {
                this.add(fun);
            }
            return this;
        }

        public Invokables build() {
            var finalFuncs = new HashMap<String, GuestFunction>();
            for (GuestFunction func : functions) {
                finalFuncs.put(func.name(), func);
            }
            return new Invokables(moduleName, finalFuncs);
        }
    }
}
