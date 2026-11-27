package io.roastedroot.quickjs4j.jackson2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.roastedroot.quickjs4j.core.JsonCodec;
import io.roastedroot.quickjs4j.core.JsonCodecException;
import java.util.ArrayList;
import java.util.List;

/** {@link JsonCodec} backed by a Jackson 2 {@link ObjectMapper}. */
public final class Jackson2Codec implements JsonCodec {
    private final ObjectMapper mapper;

    private Jackson2Codec(ObjectMapper mapper) {
        this.mapper = mapper;
        // untrusted JS must not choose the classes to instantiate
        if (mapper.getDeserializationConfig().getDefaultTyper(mapper.constructType(Object.class))
                != null) {
            throw new IllegalArgumentException(
                    "ObjectMapper with default typing enabled is not supported");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    @Override
    public String toJson(Object value, Class<?> type) {
        try {
            return mapper.writerFor(type).writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new JsonCodecException("Failed to serialize value as " + type, e);
        }
    }

    @Override
    public List<Object> fromJsonArray(String json, List<Class<?>> types) {
        try {
            var array = mapper.readTree(json);
            if (array == null || !array.isArray()) {
                throw new JsonCodecException("Expected a JSON array");
            }
            var values = new ArrayList<Object>(types.size());
            for (int i = 0; i < types.size(); i++) {
                var element = array.get(i);
                values.add(element == null ? null : mapper.treeToValue(element, types.get(i)));
            }
            return values;
        } catch (JsonProcessingException e) {
            throw new JsonCodecException("Failed to deserialize JSON as " + types, e);
        }
    }

    public static final class Builder {
        private ObjectMapper mapper;

        private Builder() {}

        public Builder withObjectMapper(ObjectMapper mapper) {
            this.mapper = mapper;
            return this;
        }

        public Jackson2Codec build() {
            return new Jackson2Codec(mapper != null ? mapper : new ObjectMapper());
        }
    }
}
