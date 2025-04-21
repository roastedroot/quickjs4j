package io.roastedroot.js;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsMachineTest {

    @Test
    public void basicUsage() {
        // Arrange
        var invoked = new AtomicBoolean(false);
        var builtins =
                Builtins.builder()
                        .addIntToVoid("java_check", (num) -> {
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
    }
}
