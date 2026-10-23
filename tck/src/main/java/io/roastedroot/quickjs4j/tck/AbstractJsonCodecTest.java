package io.roastedroot.quickjs4j.tck;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.quickjs4j.binding.JsonCodec;
import io.roastedroot.quickjs4j.binding.JsonCodecException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Contract every {@link JsonCodec} must fulfil. */
public abstract class AbstractJsonCodecTest {
    // U+2028 and U+2029: valid in JSON strings, line terminators in older JS
    private static final String LINE_SEPARATORS = new String(new char[] {0x2028, 0x2029});

    protected abstract JsonCodec codec();

    /** How the codec maps an untyped ({@code Object.class}) JSON integer. */
    protected void assertUntypedInteger(int expected, Object actual) {
        assertEquals(Integer.valueOf(expected), actual);
    }

    /** How the codec maps an untyped ({@code Object.class}) JSON decimal. */
    protected void assertUntypedDecimal(double expected, Object actual) {
        assertEquals(Double.valueOf(expected), actual);
    }

    private Object roundTrip(Object value, Type type) {
        return codec().fromJson(codec().toJson(value, type), type);
    }

    @Test
    public void scalars() {
        assertEquals(42, codec().fromJson("42", Integer.class));
        assertEquals(42L, codec().fromJson("42", Long.class));
        assertEquals(1.5, codec().fromJson("1.5", Double.class));
        assertEquals(true, codec().fromJson("true", Boolean.class));
        assertEquals("str", codec().fromJson("\"str\"", String.class));

        assertEquals("42", codec().toJson(42, Integer.class));
        assertEquals("true", codec().toJson(true, Boolean.class));
        assertEquals("\"str\"", codec().toJson("str", String.class));
    }

    @Test
    public void nullForReferenceTypes() {
        assertNull(codec().fromJson("null", Integer.class));
        assertNull(codec().fromJson("null", String.class));
        assertNull(codec().fromJson("null", Point.class));
        assertNull(codec().fromJson("null", Object.class));
    }

    @Test
    public void strings() {
        for (var str :
                List.of(
                        "",
                        "quote\" backslash\\ slash/",
                        "line\nbreak\ttab",
                        "separator\u001e",
                        LINE_SEPARATORS,
                        "emoji 😀",
                        "nul\u0000")) {
            assertEquals(str, roundTrip(str, String.class));
        }
        assertEquals("😀", codec().fromJson("\"\\ud83d\\ude00\"", String.class));
    }

    @Test
    public void beans() {
        var point = new Point(1, -2);
        assertEquals(point, roundTrip(point, Point.class));
        assertEquals(point, codec().fromJson("{\"y\":-2,\"x\":1}", Point.class));
        assertEquals(point, codec().fromJson(codec().toJson(point, Object.class), Point.class));
    }

    @Test
    public void arrays() {
        var points = new Point[] {new Point(1, 2), new Point(3, 4)};
        assertArrayEquals(points, (Point[]) roundTrip(points, Point[].class));
        assertArrayEquals(new int[] {1, 2, 3}, (int[]) codec().fromJson("[1,2,3]", int[].class));
        assertArrayEquals(
                new byte[] {0, 1, -1, 127},
                (byte[]) roundTrip(new byte[] {0, 1, -1, 127}, byte[].class));
    }

    @Test
    public void enums() {
        assertEquals("\"GREEN\"", codec().toJson(Color.GREEN, Color.class));
        assertEquals(Color.RED, codec().fromJson("\"RED\"", Color.class));
    }

    @Test
    public void untyped() {
        assertUntypedInteger(42, codec().fromJson("42", Object.class));
        assertUntypedDecimal(1.5, codec().fromJson("1.5", Object.class));
        assertEquals(true, codec().fromJson("true", Object.class));
        assertEquals("str", codec().fromJson("\"str\"", Object.class));

        var list = assertInstanceOf(List.class, codec().fromJson("[1,\"a\",null]", Object.class));
        assertEquals(3, list.size());
        assertUntypedInteger(1, list.get(0));
        assertEquals("a", list.get(1));
        assertNull(list.get(2));

        var map =
                assertInstanceOf(
                        Map.class,
                        codec().fromJson("{\"a\":{\"b\":[2]},\"c\":null}", Object.class));
        assertEquals(2, map.size());
        var nested = assertInstanceOf(Map.class, map.get("a"));
        assertUntypedInteger(2, ((List<?>) nested.get("b")).get(0));
        assertTrue(map.containsKey("c"));
    }

    @Test
    public void rawCollections() {
        var list = assertInstanceOf(List.class, codec().fromJson("[1,2]", List.class));
        assertUntypedInteger(2, list.get(1));
        var map = assertInstanceOf(Map.class, codec().fromJson("{\"k\":\"v\"}", Map.class));
        assertEquals("v", map.get("k"));
        assertEquals("[1,2]", codec().toJson(List.of(1, 2), List.class));
    }

    @Test
    public void trailingContentIsRejected() {
        for (var json : List.of("1 2", "{} {}", "[1] x", "\"a\"\"b\"", "null null")) {
            assertThrows(
                    JsonCodecException.class, () -> codec().fromJson(json, Object.class), json);
        }
        assertThrows(JsonCodecException.class, () -> codec().fromJson("1 2", Integer.class));
        assertThrows(JsonCodecException.class, () -> codec().fromJson("{} {}", Point.class));
    }

    @Test
    public void malformedJsonIsRejected() {
        for (var json : List.of("", "{", "[1,", "nope", "{\"a\" 1}", "'single'")) {
            assertThrows(
                    JsonCodecException.class, () -> codec().fromJson(json, Object.class), json);
        }
    }

    @Test
    public void typeMismatchIsRejected() {
        assertThrows(JsonCodecException.class, () -> codec().fromJson("\"abc\"", Integer.class));
        assertThrows(JsonCodecException.class, () -> codec().fromJson("[1]", Point.class));
        assertThrows(JsonCodecException.class, () -> codec().fromJson("{}", int[].class));
    }

    @Test
    public void deepNestingIsRejected() {
        var deep = "[".repeat(100_000) + "]".repeat(100_000);
        assertThrows(JsonCodecException.class, () -> codec().fromJson(deep, Object.class));
    }
}
