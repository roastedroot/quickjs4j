package io.roastedroot.quickjs4j.jackson3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.quickjs4j.core.Builtins;
import io.roastedroot.quickjs4j.core.Engine;
import io.roastedroot.quickjs4j.core.GuestFunction;
import io.roastedroot.quickjs4j.core.HostFunction;
import io.roastedroot.quickjs4j.core.Invokables;
import io.roastedroot.quickjs4j.core.JsonCodecException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

public class Jackson3CodecTest {
    private final Jackson3Codec codec = Jackson3Codec.builder().build();

    public record Pair(String name, int value) {}

    public static final class Unsorted {
        public int b = 1;
        public int a = 2;
    }

    public static final class Primitive {
        public int x;
    }

    private Object decode(String json, Class<?> type) {
        return codec.fromJsonArray("[" + json + "]", List.of(type)).get(0);
    }

    // quickjs4j-jackson2 is on the test classpath too
    @Test
    public void preferredOverJackson2() {
        try (var engine = Engine.builder().build()) {
            assertInstanceOf(Jackson3Codec.class, engine.jsonCodec());
        }
    }

    @Test
    public void engineRoundTrip() {
        var builtins =
                Builtins.builder("from_java")
                        .add(
                                new HostFunction(
                                        "echo",
                                        List.of(Pair.class),
                                        Pair.class,
                                        args -> args.get(0)))
                        .build();
        var invokables =
                Invokables.builder("from_js")
                        .add(new GuestFunction("twice", List.of(Pair.class), Pair.class))
                        .build();
        var code =
                "function twice(p) { const e = from_java.echo(p); return { name: e.name, value:"
                        + " e.value * 2 }; }";

        try (var engine =
                Engine.builder()
                        .withJsonCodec(codec)
                        .addBuiltins(builtins)
                        .addInvokables(invokables)
                        .build()) {
            assertEquals(
                    new Pair("answer", 42),
                    engine.invokeGuestFunction(
                            "from_js", "twice", List.of(new Pair("answer", 21)), code));
        }
    }

    @Test
    public void arrays() {
        assertEquals(
                List.of(1, "a"),
                codec.fromJsonArray("[1, \"a\", true]", List.of(Integer.class, String.class)));
        assertEquals(
                Arrays.asList(1, null),
                codec.fromJsonArray("[1]", List.of(Integer.class, String.class)));
        for (var invalid : List.of("{}", "1", "[1", "")) {
            assertThrows(
                    JsonCodecException.class,
                    () -> codec.fromJsonArray(invalid, List.of(Integer.class)),
                    invalid);
        }
    }

    @Test
    public void untypedValues() {
        var list = (List<?>) decode("[1,{\"a\":1.5},\"s\",null]", Object.class);
        assertEquals(List.of(1, Map.of("a", 1.5), "s"), list.subList(0, 3));
        assertEquals(null, list.get(3));
        assertEquals("null", codec.toJson(null, Object.class));
    }

    @Test
    public void jsonNodes() {
        var node = (JsonNode) decode("{\"a\":[1,\"two\"]}", JsonNode.class);
        assertEquals("two", node.get("a").get(1).asString());
        assertEquals("{\"a\":[1,\"two\"]}", codec.toJson(node, Object.class));
    }

    @Test
    public void annotatedBeans() {
        var point = new AnnotatedPoint(1, 2);
        assertEquals("{\"x\":1,\"y\":2}", codec.toJson(point, AnnotatedPoint.class));
        assertEquals(point, decode("{\"x\":1,\"y\":2}", AnnotatedPoint.class));
    }

    @Test
    public void jackson2DefaultsByDefault() {
        assertEquals("{\"b\":1,\"a\":2}", codec.toJson(new Unsorted(), Unsorted.class));
        assertEquals(0, ((Primitive) decode("{\"x\":null}", Primitive.class)).x);
    }

    @Test
    public void jackson2TypesAreRejected() {
        var ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> decode("{}", com.fasterxml.jackson.databind.JsonNode.class));
        assertTrue(ex.getMessage().contains("Jackson 2 codec"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        codec.toJson(
                                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance
                                        .objectNode(),
                                Object.class));
    }

    @Test
    public void defaultTypingIsRejected() {
        var mapper =
                JsonMapper.builder()
                        .activateDefaultTyping(BasicPolymorphicTypeValidator.builder().build())
                        .build();
        assertThrows(
                IllegalArgumentException.class,
                () -> Jackson3Codec.builder().withObjectMapper(mapper).build());
    }
}
