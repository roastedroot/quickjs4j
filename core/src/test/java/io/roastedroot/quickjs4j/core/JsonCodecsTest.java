package io.roastedroot.quickjs4j.core;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

public class JsonCodecsTest {

    private static JsonCodecProvider provider(int priority) {
        return new JsonCodecProvider() {
            @Override
            public JsonCodec create() {
                throw new UnsupportedOperationException();
            }

            @Override
            public int priority() {
                return priority;
            }
        };
    }

    @Test
    public void noCodec() {
        var ex = assertThrows(IllegalStateException.class, () -> JsonCodecs.select(List.of()));
        assertTrue(ex.getMessage().contains("io.roastedroot:quickjs4j-jackson2"));
        assertTrue(ex.getMessage().contains("io.roastedroot:quickjs4j-jackson3"));
    }

    @Test
    public void singleCodec() {
        var provider = provider(0);
        assertSame(provider, JsonCodecs.select(List.of(provider)));
    }

    @Test
    public void highestPriorityWins() {
        var jackson2 = provider(2);
        var jackson3 = provider(3);
        assertSame(jackson3, JsonCodecs.select(List.of(jackson2, jackson3)));
        assertSame(jackson3, JsonCodecs.select(List.of(jackson3, jackson2)));
    }

    @Test
    public void samePriorityIsAmbiguous() {
        var ex =
                assertThrows(
                        IllegalStateException.class,
                        () -> JsonCodecs.select(List.of(provider(1), provider(1), provider(0))));
        assertTrue(ex.getMessage().contains("withJsonCodec"));
    }

    @Test
    public void discoveredThroughServiceLoader() {
        try (var engine = Engine.builder().build()) {
            assertInstanceOf(TestJsonCodec.class, engine.jsonCodec());
        }
    }

    @Test
    public void explicitCodec() {
        var codec = new TestJsonCodec();
        try (var engine = Engine.builder().withJsonCodec(codec).build()) {
            assertSame(codec, engine.jsonCodec());
        }
    }
}
