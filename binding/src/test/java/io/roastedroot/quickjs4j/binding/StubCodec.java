package io.roastedroot.quickjs4j.binding;

import java.lang.reflect.Type;
import java.util.ArrayList;

// Just enough JSON for ints, plain strings and int arrays.
final class StubCodec implements JsonCodec {

    @Override
    public String toJson(Object value, Type type) {
        if (value instanceof String) {
            return "\"" + value + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        throw new JsonCodecException("unsupported: " + value);
    }

    @Override
    public Object fromJson(String json, Type type) {
        if (json.equals("null")) {
            return null;
        }
        if (json.startsWith("\"")) {
            return json.substring(1, json.length() - 1);
        }
        if (json.startsWith("[")) {
            var list = new ArrayList<Object>();
            for (var elem : json.substring(1, json.length() - 1).split(",")) {
                list.add(fromJson(elem, Object.class));
            }
            return list;
        }
        if (json.equals("true") || json.equals("false")) {
            return Boolean.valueOf(json);
        }
        if (json.contains(".")) {
            return Double.valueOf(json);
        }
        return Integer.valueOf(json);
    }
}
