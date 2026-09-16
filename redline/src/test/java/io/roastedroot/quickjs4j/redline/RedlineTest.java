package io.roastedroot.quickjs4j.redline;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.quickjs4j.core.InstanceBuilderProvider;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

public class RedlineTest {

    @Test
    public void nativeCodeIsUsed() {
        assertTrue(new RedlineInstanceBuilderProvider().builder().isPresent());
    }

    @Test
    public void bestRunnerForTheJdkIsSelected() {
        var provider = RedlineInstanceBuilderProvider.discover().orElseThrow();
        var expected = Runtime.version().feature() >= 25 ? "Panama" : "Jffi";
        assertTrue(
                provider.getClass().getSimpleName().startsWith(expected),
                "unexpected runner: " + provider.getClass().getName());
    }

    @Test
    public void engineDiscoversTheProvider() {
        var providers = ServiceLoader.load(InstanceBuilderProvider.class).stream();
        assertTrue(providers.anyMatch(p -> p.type() == RedlineInstanceBuilderProvider.class));
    }
}
