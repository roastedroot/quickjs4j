package io.roastedroot.js;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class Builtins {
    private final JsFunction[] functions;
    private final Map<String, Integer> indexes;

    private Builtins(JsFunction[] functions, Map<String, Integer> indexes) {
        this.functions = functions;
        this.indexes = indexes;
    }

    public JsFunction byIndex(int index) {
        return functions[index];
    }

    public int size() {
        return functions.length;
    }

    public JsFunction byName(String name) {
        if (!indexes.containsKey(name)) {
            return null;
        }
        return functions[indexes.get(name)];
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<JsFunction> functions = new ArrayList<>();

        public Builder add(String name, JsFunction fun) {
            // Early verify the order?
            // TODO: check if someone might want to leave holes
            assert (fun.index() == functions.size());
            functions.add(fun);
            return this;
        }

        public Builder addIntIntToInt(String name, BiFunction<Integer, Integer, Integer> fn) {
            return add(
                    name,
                    new JsFunction(
                            name,
                            functions.size(),
                            List.of(Integer.class, Integer.class),
                            Integer.class,
                            (args) -> fn.apply((Integer) args.get(0), (Integer) args.get(1))));
        }

        public Builder addVoidToInt(String name, Supplier<Integer> fn) {
            return add(
                    name,
                    new JsFunction(
                            name,
                            functions.size(),
                            List.of(),
                            Integer.class,
                            (args) -> {
                                return fn.get();
                            }));
        }

        public Builder addVoidToString(String name, Supplier<String> fn) {
            return add(
                    name,
                    new JsFunction(
                            name,
                            functions.size(),
                            List.of(),
                            String.class,
                            (args) -> {
                                return fn.get();
                            }));
        }

        public Builder addIntToVoid(String name, Consumer<Integer> fn) {
            return add(
                    name,
                    new JsFunction(
                            name,
                            functions.size(),
                            List.of(Integer.class),
                            Void.class,
                            (args) -> {
                                fn.accept((Integer) args.get(0));
                                return null;
                            }));
        }

        public Builder addVoidToVoid(String name, Runnable fn) {
            return add(
                    name,
                    new JsFunction(
                            name,
                            functions.size(),
                            List.of(),
                            Void.class,
                            (args) -> {
                                fn.run();
                                return null;
                            }));
        }

        public Builder addIntToString(String name, Function<Integer, String> fn) {
            return add(
                    name,
                    new JsFunction(
                            name,
                            functions.size(),
                            List.of(Integer.class),
                            String.class,
                            (args) -> {
                                return fn.apply((Integer) args.get(0));
                            }));
        }

        public Builder addStringToInt(String name, Function<String, Integer> fn) {
            return add(
                    name,
                    new JsFunction(
                            name,
                            functions.size(),
                            List.of(String.class),
                            Integer.class,
                            (args) -> {
                                return fn.apply((String) args.get(0));
                            }));
        }

        public Builder addStringToString(String name, Function<String, String> fn) {
            return add(
                    name,
                    new JsFunction(
                            name,
                            functions.size(),
                            List.of(String.class),
                            String.class,
                            (args) -> {
                                return fn.apply((String) args.get(0));
                            }));
        }

        public Builder addStringToVoid(String name, Consumer<String> fn) {
            return add(
                    name,
                    new JsFunction(
                            name,
                            functions.size(),
                            List.of(String.class),
                            Integer.class,
                            (args) -> {
                                fn.accept((String) args.get(0));
                                return null;
                            }));
        }

        public Builtins build() {
            var finalFuncs = new JsFunction[functions.size()];
            var indexes = new HashMap<String, Integer>();
            for (int i = 0; i < functions.size(); i++) {
                finalFuncs[i] = functions.get(i);
                indexes.put(finalFuncs[i].name(), i);
            }
            return new Builtins(finalFuncs, indexes);
        }
    }
}
