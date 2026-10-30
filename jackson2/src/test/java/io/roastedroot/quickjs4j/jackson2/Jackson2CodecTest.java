package io.roastedroot.quickjs4j.jackson2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import io.roastedroot.quickjs4j.binding.Binding;
import io.roastedroot.quickjs4j.binding.JsonCodec;
import io.roastedroot.quickjs4j.tck.AbstractJsonCodecTest;
import org.junit.jupiter.api.Test;

public class Jackson2CodecTest extends AbstractJsonCodecTest {
    private static final JsonCodec CODEC = new Jackson2Codec();

    @Override
    protected JsonCodec codec() {
        return CODEC;
    }

    @Test
    public void discoveredThroughServiceLoader() {
        assertInstanceOf(Jackson2Codec.class, Binding.create().codec());
    }

    @Test
    public void jsonNodes() {
        var node = (JsonNode) codec().fromJson("{\"a\":[1,\"two\"]}", JsonNode.class);
        assertEquals("two", node.get("a").get(1).asText());
        assertEquals("{\"a\":[1,\"two\"]}", codec().toJson(node, Object.class));
    }

    @Test
    public void annotatedBeans() {
        var point = new AnnotatedPoint(1, 2);
        assertEquals("{\"x\":1,\"y\":2}", codec().toJson(point, AnnotatedPoint.class));
        assertEquals(point, codec().fromJson("{\"x\":1,\"y\":2}", AnnotatedPoint.class));
    }

    @Test
    public void defaultTypingIsRejected() {
        var mapper = new ObjectMapper();
        mapper.activateDefaultTyping(BasicPolymorphicTypeValidator.builder().build());
        assertThrows(IllegalArgumentException.class, () -> new Jackson2Codec(mapper));
    }
}
