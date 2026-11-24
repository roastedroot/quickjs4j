package io.roastedroot.quickjs4j.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

// Core can't depend on the codec modules (reactor cycle), so tests use this minimal codec.
public final class TestJsonCodec implements JsonCodec {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String toJson(Object value, Class<?> type) {
        try {
            return mapper.writerFor(type).writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new JsonCodecException("Failed to serialize " + type, e);
        }
    }

    @Override
    public List<Object> fromJsonArray(String json, List<Class<?>> types) {
        try {
            var array = mapper.readTree(json);
            var values = new ArrayList<Object>(types.size());
            for (int i = 0; i < types.size(); i++) {
                var element = array.get(i);
                values.add(element == null ? null : mapper.treeToValue(element, types.get(i)));
            }
            return values;
        } catch (JsonProcessingException e) {
            throw new JsonCodecException("Failed to deserialize " + types, e);
        }
    }
}
