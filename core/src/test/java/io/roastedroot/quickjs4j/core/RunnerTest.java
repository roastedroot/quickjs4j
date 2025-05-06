package io.roastedroot.quickjs4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

public class RunnerTest {

    @Test
    public void basicUsage() {
        // Arrange
        var invoked = new AtomicBoolean(false);
        var builtins =
                Builtins.builder("from_java")
                        .addIntToVoid(
                                "java_check",
                                (num) -> {
                                    assertEquals(42, num);
                                    invoked.set(true);
                                })
                        .build();
        var jsEngine = Engine.builder().addBuiltins(builtins).build();
        var runner = Runner.builder().withEngine(jsEngine).build();

        // Act
        runner.compileAndExec("from_java.java_check(42);");

        // Assert
        assertTrue(invoked.get());

        runner.close();
    }

    @Test
    public void fullUsage() {
        // Arrange
        var invoked = new AtomicBoolean(false);
        var builtins =
                Builtins.builder("from_java")
                        .addIntToVoid(
                                "java_check",
                                (num) -> {
                                    assertEquals(42, num);
                                    invoked.set(true);
                                })
                        .build();

        var invokables =
                Invokables.builder("from_js")
                        .add(
                                new GuestFunction(
                                        "js_func",
                                        List.of(Integer.class, Integer.class),
                                        Integer.class))
                        .build();

        var libraryCode = "function js_func(x, y) { from_java.java_check(x); return x * y; };";

        var jsEngine = Engine.builder().addBuiltins(builtins).addInvokables(invokables).build();

        var runner = Runner.builder().withEngine(jsEngine).build();

        // Act
        var result =
                (Integer)
                        runner.invokeGuestFunction(
                                "from_js", "js_func", List.of(42, 2), libraryCode);

        // Assert
        assertTrue(invoked.get());
        assertEquals(84, result);

        runner.close();
    }

    @Test
    public void minimalDocsExample() {
        try (var runner = Runner.builder().build()) {
            runner.compileAndExec("console.log(\"Hello QuickJs4J!\");");
        }
    }

    @Test
    public void withTimeout() {
        // Arrange
        var runner = Runner.builder().withTimeoutMs(500).build();

        // Act
        var ex =
                assertThrows(
                        RuntimeException.class, () -> runner.compileAndExec("while (true) { };"));

        // Assert
        assertTrue(ex.getCause() instanceof TimeoutException);

        runner.close();
    }
}
