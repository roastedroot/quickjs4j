package io.roastedroot.quickjs4j.core;

import java.util.List;

/** Maps values to and from JSON; implementations must be thread-safe. */
public interface JsonCodec {

    /** {@code value} may be {@code null}; {@code Object.class} means its runtime type. */
    String toJson(Object value, Class<?> type);

    /** One value per type, null if missing; Object.class decodes to Map, List or a scalar. */
    List<Object> fromJsonArray(String json, List<Class<?>> types);
}
