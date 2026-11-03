package io.roastedroot.quickjs4j.binding;

import io.roastedroot.quickjs4j.core.Builtins;
import io.roastedroot.quickjs4j.core.GuestFunction;
import io.roastedroot.quickjs4j.core.HostFunction;
import io.roastedroot.quickjs4j.core.Invokables;
import io.roastedroot.quickjs4j.core.Runner;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Maps typed Java functions to the engine's JSON text API; use one per engine. */
public final class Binding {
    private static final String NULL = "null";

    private final JsonCodec codec;
    private final List<Object> hostRefs = new ArrayList<>();
    private final Map<String, Map<String, TypedGuestFunction>> guestFunctions = new HashMap<>();

    private Binding(JsonCodec codec) {
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public static Binding create() {
        return new Binding(JsonCodecs.discover());
    }

    public static Binding create(JsonCodec codec) {
        return new Binding(codec);
    }

    public JsonCodec codec() {
        return codec;
    }

    public Builtins builtins(String module, TypedHostFunction... functions) {
        var builder = Builtins.builder(module);
        for (var fn : functions) {
            builder.add(new HostFunction(fn.name(), args -> invokeHost(fn, args)));
        }
        return builder.build();
    }

    public Invokables invokables(String module, TypedGuestFunction... functions) {
        var builder = Invokables.builder(module);
        var signatures = new HashMap<String, TypedGuestFunction>();
        for (var fn : functions) {
            builder.add(new GuestFunction(fn.name(), fn.globalName()));
            signatures.put(fn.name(), fn);
        }
        var invokables = builder.build();
        synchronized (guestFunctions) {
            guestFunctions.put(module, Collections.unmodifiableMap(signatures));
        }
        return invokables;
    }

    public TypedRunner bind(Runner runner) {
        return new TypedRunner(this, runner);
    }

    public List<String> encodeArgs(String module, String name, List<Object> args) {
        var fn = guestFunction(module, name);
        var types = fn.paramTypes();
        if (types.size() != args.size()) {
            throw new IllegalArgumentException(
                    "Guest function should be invoked with the expected "
                            + types.size()
                            + " params, but got: "
                            + args.size());
        }
        var result = new ArrayList<String>(args.size());
        for (int i = 0; i < args.size(); i++) {
            var arg = args.get(i);
            if (types.get(i) == HostRef.class) {
                result.add(Integer.toString(addHostRef(arg)));
            } else {
                result.add(arg == null ? NULL : codec.toJson(arg, Object.class));
            }
        }
        return result;
    }

    public Object decodeResult(String module, String name, String json) {
        var type = guestFunction(module, name).returnType();
        if (json == null || type == Void.class || type == void.class) {
            return null;
        }
        return decode(json, type);
    }

    private String invokeHost(TypedHostFunction fn, List<String> json) {
        var types = fn.paramTypes();
        // missing arguments are null, extra ones are ignored
        var args = new ArrayList<>(types.size());
        for (int i = 0; i < types.size(); i++) {
            args.add(i < json.size() ? decode(json.get(i), types.get(i)) : null);
        }
        var result = fn.invoke(args);

        var type = fn.returnType();
        if (result == null || type == Void.class || type == void.class) {
            return NULL;
        }
        if (type == HostRef.class) {
            return Integer.toString(addHostRef(result));
        }
        return codec.toJson(result, type);
    }

    private Object decode(String json, Type type) {
        if (type == HostRef.class) {
            return resolveHostRefs(codec.fromJson(json, Object.class));
        }
        return codec.fromJson(json, type);
    }

    private Object resolveHostRefs(Object value) {
        if (value instanceof List) {
            var refs = new ArrayList<>();
            for (var elem : (List<?>) value) {
                refs.add(hostRef(elem));
            }
            return refs;
        }
        return hostRef(value);
    }

    private Object hostRef(Object handle) {
        if (handle == null) {
            return null;
        }
        if (handle instanceof Number) {
            var number = (Number) handle;
            var index = number.longValue();
            synchronized (hostRefs) {
                if (number.doubleValue() == index && index >= 0 && index < hostRefs.size()) {
                    return hostRefs.get((int) index);
                }
            }
        }
        throw new IllegalArgumentException("Invalid host reference: " + handle);
    }

    private int addHostRef(Object ref) {
        synchronized (hostRefs) {
            hostRefs.add(ref);
            return hostRefs.size() - 1;
        }
    }

    private TypedGuestFunction guestFunction(String module, String name) {
        Map<String, TypedGuestFunction> functions;
        synchronized (guestFunctions) {
            functions = guestFunctions.get(module);
        }
        var fn = (functions == null) ? null : functions.get(name);
        if (fn == null) {
            throw new IllegalArgumentException(
                    "Failed to find guest function with name " + name + " in module " + module);
        }
        return fn;
    }
}
