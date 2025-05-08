package io.roastedroot.quickjs4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    public void complexTypes() {
        // Arrange
        var builtins =
                Builtins.builder("from_java")
                        .add(
                                new HostFunction(
                                        "double",
                                        List.of(Point.class),
                                        Point.class,
                                        (args) -> {
                                            var point = (Point) args.get(0);

                                            return new Point(point.x() * 2, point.y() * 2);
                                        }))
                        .build();

        var invokables =
                Invokables.builder("from_js")
                        .add(new GuestFunction("triple", List.of(Point.class), Point.class))
                        .add(new GuestFunction("double", List.of(Point.class), Point.class))
                        .build();

        var doubleFunc = "function double(point) { return from_java.double(point); };\n";
        var tripleFunc =
                "function triple(point) { return { \"x\": (point.x * 3),\"y\": (point.y * 3) }"
                        + " };\n";

        var libraryCode = doubleFunc + tripleFunc;

        var jsEngine = Engine.builder().addBuiltins(builtins).addInvokables(invokables).build();
        var runner = Runner.builder().withEngine(jsEngine).build();

        // Act
        var doubleResult =
                (Point)
                        runner.invokeGuestFunction(
                                "from_js", "double", List.of(new Point(1, 3)), libraryCode);
        var tripleResult =
                (Point)
                        runner.invokeGuestFunction(
                                "from_js", "triple", List.of(new Point(3, 2)), libraryCode);

        // Assert
        assertEquals(new Point(2, 6), doubleResult);
        assertEquals(new Point(9, 6), tripleResult);

        runner.close();
    }

    @Test
    public void hostRefs() {
        // Arrange
        var builtins =
                Builtins.builder("from_java")
                        .add(
                                new HostFunction(
                                        "create",
                                        List.of(Integer.class, Integer.class),
                                        HostRef.class,
                                        (args) -> {
                                            var x = (Integer) args.get(0);
                                            var y = (Integer) args.get(1);
                                            return new Point(x, y);
                                        }))
                        .add(
                                new HostFunction(
                                        "get_x",
                                        List.of(HostRef.class),
                                        Integer.class,
                                        (args) -> {
                                            var point = (Point) args.get(0);
                                            return point.x();
                                        }))
                        .add(
                                new HostFunction(
                                        "get_y",
                                        List.of(HostRef.class),
                                        Integer.class,
                                        (args) -> {
                                            var point = (Point) args.get(0);
                                            return point.y();
                                        }))
                        .build();

        var invokables =
                Invokables.builder("from_js")
                        .add(new GuestFunction("x_even", List.of(HostRef.class), Boolean.class))
                        .add(
                                new GuestFunction(
                                        "choose",
                                        List.of(HostRef.class, HostRef.class),
                                        HostRef.class))
                        .build();

        var xEvenFunc =
                "function x_even(point_ptr) { return from_java.get_x(point_ptr) % 2 === 0; };\n";

        var chooseFunc =
                "function choose(point_ptr1, point_ptr2) { "
                        + "if (from_java.get_x(point_ptr1) < from_java.get_x(point_ptr2)) { "
                        + "return point_ptr1; } else { return point_ptr2; } };\n";

        var libraryCode = xEvenFunc + chooseFunc;

        var jsEngine = Engine.builder().addBuiltins(builtins).addInvokables(invokables).build();
        var runner = Runner.builder().withEngine(jsEngine).build();

        // Act
        var xEven1 =
                (Boolean)
                        runner.invokeGuestFunction(
                                "from_js", "x_even", List.of(new Point(1, 3)), libraryCode);
        var xEven2 =
                (Boolean)
                        runner.invokeGuestFunction(
                                "from_js", "x_even", List.of(new Point(2, 3)), libraryCode);
        var choose1 =
                (Point)
                        runner.invokeGuestFunction(
                                "from_js",
                                "choose",
                                List.of(new Point(1, 4), new Point(2, 5)),
                                libraryCode);
        var choose2 =
                (Point)
                        runner.invokeGuestFunction(
                                "from_js",
                                "choose",
                                List.of(new Point(10, 20), new Point(2, 5)),
                                libraryCode);

        // Assert
        assertFalse(xEven1);
        assertTrue(xEven2);
        assertEquals(new Point(1, 4), choose1);
        assertEquals(new Point(2, 5), choose2);

        runner.close();
    }

    @Test
    public void minimalDocsExample() {
        try (var runner = Runner.builder().build()) {
            runner.compileAndExec("console.log(\"Hello QuickJs4J!\");");
            System.out.println(runner.stdout());
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

    @Test
    public void handleExceptionsThrownInJava() {
        var builtins =
                Builtins.builder("from_java")
                        .addStringToString(
                                "imported_function",
                                (str) -> {
                                    throw new IndexOutOfBoundsException("whatever");
                                })
                        .build();
        var engine = Engine.builder().addBuiltins(builtins).build();
        var runner = Runner.builder().withEngine(engine).build();

        var exception =
                assertThrows(
                        IndexOutOfBoundsException.class,
                        () -> runner.compileAndExec("from_java.imported_function(\"ciao\");"));

        assertEquals("whatever", exception.getMessage());

        runner.close();
    }

    @Test
    public void failToCompileJs() {
        var engine = Engine.builder().build();
        var runner = Runner.builder().withEngine(engine).build();

        var exception =
                assertThrows(IllegalArgumentException.class, () -> runner.compile("1' \" 2 ..."));

        assertTrue(exception.getMessage().contains("Failed to compile JS code"));

        engine.close();
    }
}
