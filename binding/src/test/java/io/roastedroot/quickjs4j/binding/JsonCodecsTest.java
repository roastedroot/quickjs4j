package io.roastedroot.quickjs4j.binding;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

public class JsonCodecsTest {
    private final JsonCodec first = new StubCodec();
    private final JsonCodec second = new StubCodec();

    private Map<String, Supplier<JsonCodec>> available(String... names) {
        var result = new LinkedHashMap<String, Supplier<JsonCodec>>();
        var codecs = new JsonCodec[] {first, second};
        for (int i = 0; i < names.length; i++) {
            var codec = codecs[i];
            result.put(names[i], () -> codec);
        }
        return result;
    }

    @Test
    public void noCodec() {
        var ex = assertThrows(IllegalStateException.class, () -> JsonCodecs.select(null, Map.of()));
        assertTrue(ex.getMessage().contains("quickjs4j-jackson2"));
        assertTrue(ex.getMessage().contains("quickjs4j-jackson3"));
    }

    @Test
    public void singleCodec() {
        assertSame(first, JsonCodecs.select(null, available("a.Codec")));
    }

    @Test
    public void multipleCodecs() {
        var ex =
                assertThrows(
                        IllegalStateException.class,
                        () -> JsonCodecs.select(null, available("a.Codec", "b.Codec")));
        assertTrue(ex.getMessage().contains("a.Codec"));
        assertTrue(ex.getMessage().contains("b.Codec"));
        assertTrue(ex.getMessage().contains(JsonCodecs.CODEC_PROPERTY));
    }

    @Test
    public void codecSelectedByProperty() {
        assertSame(second, JsonCodecs.select("b.Codec", available("a.Codec", "b.Codec")));
    }

    @Test
    public void codecSelectedByPropertyIsMissing() {
        var ex =
                assertThrows(
                        IllegalStateException.class,
                        () -> JsonCodecs.select("c.Codec", available("a.Codec", "b.Codec")));
        assertTrue(ex.getMessage().contains("c.Codec"));
    }

    @Test
    public void nothingOnTheClasspath() {
        assertThrows(IllegalStateException.class, Binding::create);
    }
}
