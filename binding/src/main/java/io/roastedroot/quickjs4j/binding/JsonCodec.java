package io.roastedroot.quickjs4j.binding;

import java.lang.reflect.Type;

/** Maps a single value to and from JSON; implementations must be thread-safe. */
public interface JsonCodec {

    /** {@code Object.class} means the runtime type of the non-null {@code value}. */
    String toJson(Object value, Type type);

    /** Must reject trailing content. */
    Object fromJson(String json, Type type);
}
