package io.roastedroot.quickjs4j.binding;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Supplier;

/** Finds the {@link JsonCodec} to use when none is passed explicitly. */
public final class JsonCodecs {
    /** Selects the codec class when several are available. */
    public static final String CODEC_PROPERTY = "quickjs4j.json.codec";

    private static volatile JsonCodec discovered;

    private JsonCodecs() {}

    public static JsonCodec discover() {
        var codec = discovered;
        if (codec == null) {
            synchronized (JsonCodecs.class) {
                codec = discovered;
                if (codec == null) {
                    codec = select(System.getProperty(CODEC_PROPERTY), available());
                    discovered = codec;
                }
            }
        }
        return codec;
    }

    private static Map<String, Supplier<JsonCodec>> available() {
        var result = new LinkedHashMap<String, Supplier<JsonCodec>>();
        ServiceLoader.load(JsonCodec.class, JsonCodec.class.getClassLoader()).stream()
                .forEach(provider -> result.put(provider.type().getName(), provider));
        return result;
    }

    static JsonCodec select(String requested, Map<String, Supplier<JsonCodec>> available) {
        if (requested != null) {
            var provider = available.get(requested);
            if (provider == null) {
                throw new IllegalStateException(
                        "JSON codec "
                                + requested
                                + " requested by the "
                                + CODEC_PROPERTY
                                + " system property is not available, found: "
                                + available.keySet());
            }
            return provider.get();
        }
        if (available.isEmpty()) {
            throw new IllegalStateException(
                    "No JSON codec found: add io.roastedroot:quickjs4j-jackson2 or"
                            + " io.roastedroot:quickjs4j-jackson3 to the classpath, or pass a"
                            + " codec to Binding.create(JsonCodec)");
        }
        if (available.size() > 1) {
            throw new IllegalStateException(
                    "Multiple JSON codecs found "
                            + available.keySet()
                            + ": pass one to Binding.create(JsonCodec) or select it with the "
                            + CODEC_PROPERTY
                            + " system property");
        }
        return available.values().iterator().next().get();
    }
}
