package io.roastedroot.quickjs4j.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

final class JsonCodecs {
    private static volatile JsonCodec discovered;

    private JsonCodecs() {}

    static JsonCodec discover() {
        var codec = discovered;
        if (codec == null) {
            synchronized (JsonCodecs.class) {
                codec = discovered;
                if (codec == null) {
                    var providers = new ArrayList<JsonCodecProvider>();
                    ServiceLoader.load(
                                    JsonCodecProvider.class,
                                    JsonCodecProvider.class.getClassLoader())
                            .forEach(providers::add);
                    codec = select(providers).create();
                    discovered = codec;
                }
            }
        }
        return codec;
    }

    static JsonCodecProvider select(List<JsonCodecProvider> providers) {
        if (providers.isEmpty()) {
            throw new IllegalStateException(
                    "No JSON codec found: add io.roastedroot:quickjs4j-jackson2 or"
                            + " io.roastedroot:quickjs4j-jackson3");
        }
        var sorted = new ArrayList<>(providers);
        sorted.sort(Comparator.comparingInt(JsonCodecProvider::priority).reversed());
        if (sorted.size() > 1 && sorted.get(0).priority() == sorted.get(1).priority()) {
            throw new IllegalStateException(
                    "Multiple JSON codecs with the same priority: "
                            + sorted.get(0).getClass().getName()
                            + ", "
                            + sorted.get(1).getClass().getName()
                            + "; use Engine.Builder.withJsonCodec");
        }
        return sorted.get(0);
    }
}
