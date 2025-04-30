package io.roastedroot.quickjs4j.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

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
        private List<HostFunction> functions = new ArrayList<>();

        private Builder(String moduleName) {
            this.moduleName = moduleName;
        }

        public Builder add(HostFunction fun) {
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

        public Builder add(HostFunction... functions) {
            for (var fun : functions) {
                this.add(fun);
            }
            return this;
        }

        public Builder addIntIntToInt(String name, BiFunction<Integer, Integer, Integer> fn) {
            return add(
                    new HostFunction(
                            name,
                            List.of(Integer.class, Integer.class),
                            Integer.class,
                            (args) -> fn.apply((Integer) args.get(0), (Integer) args.get(1))));
        }

        public Builder addVoidToInt(String name, Supplier<Integer> fn) {
            return add(
                    new HostFunction(
                            name,
                            List.of(),
                            Integer.class,
                            (args) -> {
                                return fn.get();
                            }));
        }

        public Builder addVoidToString(String name, Supplier<String> fn) {
            return add(
                    new HostFunction(
                            name,
                            List.of(),
                            String.class,
                            (args) -> {
                                return fn.get();
                            }));
        }

        public Builder addIntToVoid(String name, Consumer<Integer> fn) {
            return add(
                    new HostFunction(
                            name,
                            List.of(Integer.class),
                            Void.class,
                            (args) -> {
                                fn.accept((Integer) args.get(0));
                                return null;
                            }));
        }

        public Builder addVoidToVoid(String name, Runnable fn) {
            return add(
                    new HostFunction(
                            name,
                            List.of(),
                            Void.class,
                            (args) -> {
                                fn.run();
                                return null;
                            }));
        }

        public Builder addIntToString(String name, Function<Integer, String> fn) {
            return add(
                    new HostFunction(
                            name,
                            List.of(Integer.class),
                            String.class,
                            (args) -> {
                                return fn.apply((Integer) args.get(0));
                            }));
        }

        public Builder addStringToInt(String name, Function<String, Integer> fn) {
            return add(
                    new HostFunction(
                            name,
                            List.of(String.class),
                            Integer.class,
                            (args) -> {
                                return fn.apply((String) args.get(0));
                            }));
        }

        public Builder addStringToString(String name, Function<String, String> fn) {
            return add(
                    new HostFunction(
                            name,
                            List.of(String.class),
                            String.class,
                            (args) -> {
                                return fn.apply((String) args.get(0));
                            }));
        }

        public Builder addStringToVoid(String name, Consumer<String> fn) {
            return add(
                    new HostFunction(
                            name,
                            List.of(String.class),
                            Integer.class,
                            (args) -> {
                                fn.accept((String) args.get(0));
                                return null;
                            }));
        }

        public Builtins build() {
            var finalFuncs = new HashMap<String, HostFunction>();
            for (HostFunction func : functions) {
                finalFuncs.put(func.name(), func);
            }
            return new Builtins(moduleName, finalFuncs);
        }
    }
}
