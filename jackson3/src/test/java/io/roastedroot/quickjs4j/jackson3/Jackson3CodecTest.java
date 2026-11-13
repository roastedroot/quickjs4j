package io.roastedroot.quickjs4j.jackson3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.quickjs4j.binding.Binding;
import io.roastedroot.quickjs4j.binding.JsonCodec;
import io.roastedroot.quickjs4j.binding.JsonCodecException;
import io.roastedroot.quickjs4j.tck.AbstractJsonCodecTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

public class Jackson3CodecTest extends AbstractJsonCodecTest {
    private static final JsonCodec CODEC = new Jackson3Codec();

    public record Pair(String name, int value) {}

    public static final class Unsorted {
        public int b = 1;
        public int a = 2;
    }

    public static final class Primitive {
        public int x;
    }

    @Override
    protected JsonCodec codec() {
        return CODEC;
    }

    @Test
    public void discoveredThroughServiceLoader() {
        assertInstanceOf(Jackson3Codec.class, Binding.create().codec());
    }

    @Test
    public void jsonNodes() {
        var node = (JsonNode) codec().fromJson("{\"a\":[1,\"two\"]}", JsonNode.class);
        assertEquals("two", node.get("a").get(1).asString());
        assertEquals("{\"a\":[1,\"two\"]}", codec().toJson(node, Object.class));
    }

    @Test
    public void annotatedBeans() {
        var point = new AnnotatedPoint(1, 2);
        assertEquals("{\"x\":1,\"y\":2}", codec().toJson(point, AnnotatedPoint.class));
        assertEquals(point, codec().fromJson("{\"x\":1,\"y\":2}", AnnotatedPoint.class));
    }

    @Test
    public void records() {
        var pair = new Pair("answer", 42);
        assertEquals("{\"name\":\"answer\",\"value\":42}", codec().toJson(pair, Pair.class));
        assertEquals(pair, codec().fromJson("{\"value\":42,\"name\":\"answer\"}", Pair.class));
    }

    @Test
    public void jackson2DefaultsByDefault() {
        assertEquals("{\"b\":1,\"a\":2}", codec().toJson(new Unsorted(), Unsorted.class));
        assertEquals(0, ((Primitive) codec().fromJson("{\"x\":null}", Primitive.class)).x);
    }

    // A plain Jackson 3 mapper, e.g. the one Spring Boot 4 configures, changes what JS sees
    @Test
    public void plainJackson3Defaults() {
        var plain = new Jackson3Codec(JsonMapper.builder().build());
        assertEquals("{\"a\":2,\"b\":1}", plain.toJson(new Unsorted(), Unsorted.class));
        assertThrows(
                JsonCodecException.class, () -> plain.fromJson("{\"x\":null}", Primitive.class));
    }

    @Test
    public void jackson2TypesAreRejected() {
        var ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                codec().fromJson(
                                                "{}",
                                                com.fasterxml.jackson.databind.JsonNode.class));
        assertTrue(ex.getMessage().contains("quickjs4j-jackson2"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        codec().toJson(
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
        assertThrows(IllegalArgumentException.class, () -> new Jackson3Codec(mapper));
    }
}
