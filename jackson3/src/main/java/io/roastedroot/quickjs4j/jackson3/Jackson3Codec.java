package io.roastedroot.quickjs4j.jackson3;

import io.roastedroot.quickjs4j.binding.JsonCodec;
import io.roastedroot.quickjs4j.binding.JsonCodecException;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

/** {@link JsonCodec} backed by a Jackson 3 {@link ObjectMapper}. */
public final class Jackson3Codec implements JsonCodec {
    private static final String JACKSON2_PACKAGE = "com.fasterxml.jackson.databind.";

    private final ObjectMapper mapper;
    private final Map<Type, ObjectReader> readers = new ConcurrentHashMap<>();
    private final Map<Type, ObjectWriter> writers = new ConcurrentHashMap<>();

    /** Uses Jackson 2 defaults, as the Jackson 2 codec. */
    public Jackson3Codec() {
        this(JsonMapper.builderWithJackson2Defaults().build());
    }

    public Jackson3Codec(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        // untrusted JS must not choose the classes to instantiate
        if (mapper.deserializationConfig().getDefaultTyper(mapper.constructType(Object.class))
                != null) {
            throw new IllegalArgumentException(
                    "ObjectMapper with default typing enabled is not supported");
        }
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    @Override
    public String toJson(Object value, Type type) {
        try {
            if (type == Object.class) {
                javaType(value.getClass());
                return mapper.writeValueAsString(value);
            }
            return writers.computeIfAbsent(type, t -> mapper.writerFor(javaType(t)))
                    .writeValueAsString(value);
        } catch (JacksonException e) {
            throw new JsonCodecException("Failed to serialize value as " + type, e);
        }
    }

    @Override
    public Object fromJson(String json, Type type) {
        try {
            return readers.computeIfAbsent(
                            type,
                            t ->
                                    mapper.readerFor(javaType(t))
                                            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS))
                    .readValue(json);
        } catch (JacksonException e) {
            throw new JsonCodecException("Failed to deserialize JSON as " + type, e);
        }
    }

    private JavaType javaType(Type type) {
        var javaType = mapper.constructType(type);
        if (javaType.getRawClass().getName().startsWith(JACKSON2_PACKAGE)) {
            throw new IllegalArgumentException(
                    type.getTypeName()
                            + " is a Jackson 2 type, use io.roastedroot:quickjs4j-jackson2"
                            + " instead");
        }
        return javaType;
    }
}
