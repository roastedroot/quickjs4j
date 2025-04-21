package io.roastedroot.js;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

public class JsMachineTest {

    @Test
    public void basicUsage() {
        // Arrange
        var invoked = new AtomicBoolean(false);
        var builtins =
                Builtins.builder()
                        .addIntToVoid(
                                "java_check",
                                (num) -> {
                                    assertEquals(42, num);
                                    invoked.set(true);
                                })
                        .build();
        var jsEngine = JsEngine.builder().withBuiltins(builtins).build();
        var machine = JsMachine.builder().withEngine(jsEngine).build();

        // Act
        machine.compileAndExec("java_check(42);");

        // Assert
        assertTrue(invoked.get());

        machine.close();
    }

    @Test
    public void withTimeout() {
        // Arrange
        var machine = JsMachine.builder().withTimeoutMs(500).build();

        // Act
        var ex =
                assertThrows(
                        RuntimeException.class, () -> machine.compileAndExec("while (true) { };"));

        // Assert
        assertTrue(ex.getCause() instanceof TimeoutException);

        machine.close();
    }
}
