package io.roastedroot.quickjs4j.jackson2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
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

public class Jackson2CodecTest {
    private final Jackson2Codec codec = Jackson2Codec.builder().build();

    private Object decode(String json, Class<?> type) {
        return codec.fromJsonArray("[" + json + "]", List.of(type)).get(0);
    }

    @Test
    public void discoveredThroughServiceLoader() {
        try (var engine = Engine.builder().build()) {
            assertInstanceOf(Jackson2Codec.class, engine.jsonCodec());
        }
    }

    @Test
    public void engineRoundTrip() {
        var builtins =
                Builtins.builder("from_java")
                        .add(
                                new HostFunction(
                                        "echo",
                                        List.of(AnnotatedPoint.class),
                                        AnnotatedPoint.class,
                                        args -> args.get(0)))
                        .build();
        var invokables =
                Invokables.builder("from_js")
                        .add(
                                new GuestFunction(
                                        "twice",
                                        List.of(AnnotatedPoint.class),
                                        AnnotatedPoint.class))
                        .build();
        var code =
                "function twice(p) { const e = from_java.echo(p); return { x: e.x * 2, y: e.y * 2"
                        + " }; }";

        try (var engine =
                Engine.builder()
                        .withJsonCodec(codec)
                        .addBuiltins(builtins)
                        .addInvokables(invokables)
                        .build()) {
            assertEquals(
                    new AnnotatedPoint(2, 4),
                    engine.invokeGuestFunction(
                            "from_js", "twice", List.of(new AnnotatedPoint(1, 2)), code));
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
        assertEquals("two", node.get("a").get(1).asText());
        assertEquals("{\"a\":[1,\"two\"]}", codec.toJson(node, Object.class));
    }

    @Test
    public void defaultTypingIsRejected() {
        var mapper = new ObjectMapper();
        mapper.activateDefaultTyping(BasicPolymorphicTypeValidator.builder().build());
        assertThrows(
                IllegalArgumentException.class,
                () -> Jackson2Codec.builder().withObjectMapper(mapper).build());
    }
}
