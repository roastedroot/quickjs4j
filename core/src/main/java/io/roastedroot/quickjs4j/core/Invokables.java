package io.roastedroot.quickjs4j.core;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A JS module ({@code globalThis.<moduleName>}) of guest functions callable from Java. */
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
        private final Map<String, GuestFunction> functions = new LinkedHashMap<>();

        private Builder(String moduleName) {
            this.moduleName = Identifiers.check(moduleName, "module");
        }

        public Builder add(GuestFunction fun) {
            if (functions.containsKey(fun.name())) {
                throw new IllegalArgumentException(
                        "A function with name: "
                                + fun.name()
                                + " is already defined in the module: "
                                + moduleName);
            }
            functions.put(fun.name(), fun);
            return this;
        }

        public Builder add(GuestFunction... functions) {
            for (var fun : functions) {
                this.add(fun);
            }
            return this;
        }

        public Builder add(String name) {
            return add(new GuestFunction(name));
        }

        public Invokables build() {
            return new Invokables(
                    moduleName, Collections.unmodifiableMap(new LinkedHashMap<>(functions)));
        }
    }
}
