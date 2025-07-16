package io.roastedroot.quickjs4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
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

    @Test
    public void withExecutorService() {
        try (var runner =
                Runner.builder().withExecutorService(Executors.newCachedThreadPool()).build()) {
            runner.compileAndExec("console.log('something something');");
        }
    }

    @JsonDeserialize(using = JsonDeserializer.None.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DemoProps {
        @JsonProperty("name")
        public String name;

        @JsonProperty("count")
        public int count;

        public DemoProps(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }

    @Test
    public void demoSSR() throws Exception {
        // Arrange
        var invokables =
                Invokables.builder("from_js")
                        .add(
                                new GuestFunction(
                                        "ssr",
                                        List.of(String.class, DemoProps.class),
                                        String.class))
                        .build();

        var libraryCode =
                RunnerTest.class.getResourceAsStream("/ssr-demo/dist/out.js").readAllBytes();

        var jsEngine = Engine.builder().addInvokables(invokables).build();
        var runner = Runner.builder().withEngine(jsEngine).build();

        var jsx =
                "(props) => {\n"
                    + "    const { name, count } = props;\n"
                    + "    return (\n"
                    + "      <div style={{ padding: \"20px\", fontFamily: \"sans-serif\" }}>\n"
                    + "        <h1>Welcome, {name}!</h1>\n"
                    + "        {count > 0\n"
                    + "          ? <p>You have {count} new {count === 1 ? \"message\" :"
                    + " \"messages\"}.</p>\n"
                    + "          : <p>No new messages.</p>}\n"
                    + "        <ul>\n"
                    + "          {[...Array(count)].map((_, i) => (\n"
                    + "            <li key={i}>Message #{i + 1}</li>\n"
                    + "          ))}\n"
                    + "        </ul>\n"
                    + "        <footer style={{ marginTop: \"10px\", fontSize: \"0.8em\", color:"
                    + " \"#666\" }}>\n"
                    + "          Generated at {new Date().toLocaleTimeString()}\n"
                    + "        </footer>\n"
                    + "      </div>\n"
                    + "    );\n"
                    + "  }";

        // Act
        var before1 = System.nanoTime();
        var result1 =
                (String)
                        runner.invokeGuestFunction(
                                "from_js",
                                "ssr",
                                List.of(jsx, new DemoProps("Alice", 3)),
                                new String(libraryCode, StandardCharsets.UTF_8));
        var after1 = System.nanoTime();
        System.out.println("with compilation: " + (after1 - before1) / 1_000_000 + " s");

        // Assert
        System.out.println("result1: " + result1);
        assertTrue(result1.contains("Welcome, <!-- -->Alice<!-- -->!"));

        var before2 = System.nanoTime();
        var result2 =
                (String)
                        runner.invokeGuestFunction(
                                "from_js",
                                "ssr",
                                List.of(jsx, new DemoProps("Bob", 0)),
                                new String(libraryCode, StandardCharsets.UTF_8));
        var after2 = System.nanoTime();
        System.out.println("with cached compilation: " + (after2 - before2) / 1_000_000 + " s");

        System.out.println("result2: " + result2);
        assertTrue(result2.contains("Welcome, <!-- -->Bob<!-- -->!"));

        System.out.println("stdout:");
        System.out.println(runner.stdout());

        System.err.println("stderr:");
        System.err.println(runner.stderr());

        runner.close();
    }
}
