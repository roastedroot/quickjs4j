package io.roastedroot.quickjs4j.core;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** A JS module ({@code globalThis.<moduleName>}) of Java functions callable from JS. */
public final class Builtins {
    private final String moduleName;
    private final Map<String, HostFunction> functions;

    private Builtins(String moduleName, Map<String, HostFunction> functions) {
        this.moduleName = moduleName;
        this.functions = functions;
    }

    public HostFunction byName(String name) {
        return functions.get(name);
    }

    public Collection<HostFunction> functions() {
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
        private final Map<String, HostFunction> functions = new LinkedHashMap<>();

        private Builder(String moduleName) {
            this.moduleName = Identifiers.check(moduleName, "module");
        }

        public Builder add(HostFunction fun) {
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

        public Builder add(HostFunction... functions) {
            for (var fun : functions) {
                this.add(fun);
            }
            return this;
        }

        public Builder add(String name, Function<List<String>, String> fn) {
            return add(new HostFunction(name, fn));
        }

        public Builtins build() {
            return new Builtins(
                    moduleName, Collections.unmodifiableMap(new LinkedHashMap<>(functions)));
        }
    }
}
