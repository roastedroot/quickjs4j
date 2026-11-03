package io.roastedroot.quickjs4j.jackson2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.roastedroot.quickjs4j.binding.JsonCodec;
import io.roastedroot.quickjs4j.binding.JsonCodecException;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** {@link JsonCodec} backed by a Jackson 2 {@link ObjectMapper}. */
public final class Jackson2Codec implements JsonCodec {
    private final ObjectMapper mapper;
    private final Map<Type, ObjectReader> readers = new ConcurrentHashMap<>();
    private final Map<Type, ObjectWriter> writers = new ConcurrentHashMap<>();

    public Jackson2Codec() {
        this(new ObjectMapper());
    }

    public Jackson2Codec(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        // untrusted JS must not choose the classes to instantiate
        if (mapper.getDeserializationConfig().getDefaultTyper(mapper.constructType(Object.class))
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
                return mapper.writeValueAsString(value);
            }
            return writers.computeIfAbsent(type, t -> mapper.writerFor(mapper.constructType(t)))
                    .writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new JsonCodecException("Failed to serialize value as " + type, e);
        }
    }

    @Override
    public Object fromJson(String json, Type type) {
        try {
            return readers.computeIfAbsent(
                            type,
                            t ->
                                    mapper.readerFor(mapper.constructType(t))
                                            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS))
                    .readValue(json);
        } catch (JsonProcessingException e) {
            throw new JsonCodecException("Failed to deserialize JSON as " + type, e);
        }
    }
}
