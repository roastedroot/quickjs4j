package io.roastedroot.quickjs4j.tck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.quickjs4j.binding.Binding;
import io.roastedroot.quickjs4j.binding.HostRef;
import io.roastedroot.quickjs4j.binding.JsonCodec;
import io.roastedroot.quickjs4j.binding.JsonCodecException;
import io.roastedroot.quickjs4j.binding.TypedGuestFunction;
import io.roastedroot.quickjs4j.binding.TypedHostFunction;
import io.roastedroot.quickjs4j.binding.TypedRunner;
import io.roastedroot.quickjs4j.core.Builtins;
import io.roastedroot.quickjs4j.core.Engine;
import io.roastedroot.quickjs4j.core.Invokables;
import io.roastedroot.quickjs4j.core.Runner;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** Java to JS round trips through the engine that every {@link JsonCodec} must support. */
public abstract class AbstractBindingTest {

    protected abstract JsonCodec codec();

    /** How the codec maps an untyped ({@code Object.class}) JSON integer. */
    protected void assertUntypedInteger(int expected, Object actual) {
        assertEquals(Integer.valueOf(expected), actual);
    }

    /** How the codec maps an untyped ({@code Object.class}) JSON decimal. */
    protected void assertUntypedDecimal(double expected, Object actual) {
        assertEquals(Double.valueOf(expected), actual);
    }

    protected static String resource(String name) {
        try (var in = AbstractBindingTest.class.getResourceAsStream("/quickjs4j-tck/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected static TypedHostFunction fn(
            String name,
            List<? extends Type> params,
            Type ret,
            Function<List<Object>, Object> body) {
        return new TypedHostFunction(name, params, ret, body);
    }

    protected static TypedHostFunction consumer(
            String name, AtomicReference<List<Object>> received, Class<?>... params) {
        return new TypedHostFunction(
                name,
                Arrays.asList(params),
                Void.class,
                args -> {
                    received.set(args);
                    return null;
                });
    }

    protected static void exec(Builtins builtins, String code) {
        try (var runner =
                Runner.builder()
                        .withEngine(Engine.builder().addBuiltins(builtins).build())
                        .build()) {
            runner.compileAndExec(code);
        }
    }

    protected TypedRunner runner(Binding binding, Builtins builtins, Invokables invokables) {
        var engine = Engine.builder();
        if (builtins != null) {
            engine.addBuiltins(builtins);
        }
        return binding.bind(
                Runner.builder().withEngine(engine.addInvokables(invokables).build()).build());
    }

    @Test
    public void callJavaFunctionsFromJS() {
        var received = new AtomicReference<List<Object>>();
        var binding = Binding.create(codec());
        var builtins =
                binding.builtins(
                        "from_java",
                        fn(
                                "add",
                                List.of(Integer.class, Integer.class),
                                Integer.class,
                                args -> (Integer) args.get(0) + (Integer) args.get(1)),
                        consumer("check", received, Integer.class));

        exec(builtins, "from_java.check(from_java.add(40, 2));");

        assertEquals(List.of(42), received.get());
    }

    @Test
    public void differentParamsAndReturns() {
        var received = new AtomicReference<List<Object>>();
        var binding = Binding.create(codec());
        var builtins =
                binding.builtins(
                        "from_java",
                        fn("voidToVoid", List.of(), Void.class, args -> null),
                        fn("voidToString", List.of(), String.class, args -> "func4"),
                        fn(
                                "intToString",
                                List.of(Integer.class),
                                String.class,
                                args -> "funcS" + args.get(0)),
                        fn(
                                "stringToString",
                                List.of(String.class),
                                String.class,
                                args -> "funcS" + args.get(0)),
                        fn("bool", List.of(Boolean.class), Boolean.class, args -> args.get(0)),
                        fn("dbl", List.of(Double.class), Double.class, args -> args.get(0)),
                        fn("lng", List.of(Long.class), Long.class, args -> args.get(0)),
                        consumer(
                                "check",
                                received,
                                Object.class,
                                String.class,
                                String.class,
                                String.class,
                                Boolean.class,
                                Double.class,
                                Long.class));

        exec(
                builtins,
                "from_java.check(from_java.voidToVoid(), from_java.voidToString(),"
                        + " from_java.intToString(11), from_java.stringToString(\"h3110\"),"
                        + " from_java.bool(true), from_java.dbl(2.5),"
                        + " from_java.lng(9007199254740991));");

        assertEquals(
                Arrays.asList(null, "func4", "funcS11", "funcSh3110", true, 2.5, 9007199254740991L),
                received.get());
    }

    @Test
    public void mixedParameters() {
        var received = new AtomicReference<List<Object>>();
        var binding = Binding.create(codec());
        var builtins =
                binding.builtins(
                        "from_java",
                        consumer("myFunc", received, Integer.class, String.class, Integer.class));

        exec(builtins, "from_java.myFunc(123, \"hello my world\", 321);");

        assertEquals(List.of(123, "hello my world", 321), received.get());
    }

    @Test
    public void missingArgumentsAreNullAndExtraOnesIgnored() {
        var received = new AtomicReference<List<Object>>();
        var binding = Binding.create(codec());
        var builtins =
                binding.builtins(
                        "from_java",
                        consumer("java_check", received, Integer.class, Integer.class));

        exec(builtins, "from_java.java_check(1);");
        assertEquals(Arrays.asList(1, null), received.get());

        exec(builtins, "from_java.java_check(1, 2, 3);");
        assertEquals(List.of(1, 2), received.get());

        exec(builtins, "from_java.java_check(undefined, null);");
        assertEquals(Arrays.asList(null, null), received.get());
    }

    @Test
    public void beans() {
        var binding = Binding.create(codec());
        var builtins =
                binding.builtins(
                        "from_java",
                        fn(
                                "double",
                                List.of(Point.class),
                                Point.class,
                                args -> {
                                    var point = (Point) args.get(0);
                                    return new Point(point.x * 2, point.y * 2);
                                }));
        var invokables =
                binding.invokables(
                        "from_js",
                        new TypedGuestFunction("triple", List.of(Point.class), Point.class),
                        new TypedGuestFunction("double", List.of(Point.class), Point.class));
        var code =
                "function double(point) { return from_java.double(point); };\n"
                        + "function triple(point) { return { \"x\": (point.x * 3), \"y\": (point.y"
                        + " * 3) } };\n";

        try (var runner = runner(binding, builtins, invokables)) {
            assertEquals(
                    new Point(2, 6),
                    runner.invokeGuestFunction(
                            "from_js", "double", List.of(new Point(1, 3)), code));
            assertEquals(
                    new Point(9, 6),
                    runner.invokeGuestFunction(
                            "from_js", "triple", List.of(new Point(3, 2)), code));
        }
    }

    @Test
    public void collections() {
        var binding = Binding.create(codec());
        var invokables =
                binding.invokables(
                        "js_api",
                        new TypedGuestFunction(
                                "append", List.of(List.class, Integer.class), List.class),
                        new TypedGuestFunction("points", List.of(Integer.class), Point[].class),
                        new TypedGuestFunction("dict", List.of(Map.class), Map.class));
        var code =
                "function append(arr, elem) { arr.push(elem); return arr; }\n"
                        + "function points(n) { return [...Array(n).keys()].map(i => ({ x: i, y:"
                        + " -i })); }\n"
                        + "function dict(m) { return { ...m, added: true }; }\n";

        try (var runner = runner(binding, null, invokables)) {
            var list =
                    (List<?>)
                            runner.invokeGuestFunction(
                                    "js_api", "append", List.of(List.of(1, 2, 3), 4), code);
            assertEquals(4, list.size());
            assertUntypedInteger(1, list.get(0));
            assertUntypedInteger(4, list.get(3));

            var points = (Point[]) runner.invokeGuestFunction("js_api", "points", List.of(2), code);
            assertEquals(List.of(new Point(0, 0), new Point(1, -1)), Arrays.asList(points));

            var dict =
                    (Map<?, ?>)
                            runner.invokeGuestFunction(
                                    "js_api", "dict", List.of(Map.of("k", "v")), code);
            assertEquals("v", dict.get("k"));
            assertEquals(true, dict.get("added"));
        }
    }

    @Test
    public void untypedResults() {
        var binding = Binding.create(codec());
        var invokables =
                binding.invokables(
                        "from_js",
                        new TypedGuestFunction("evaluate", List.of(String.class), Object.class));
        var code = "function evaluate(script) { return (0, eval)(script); }";

        try (var runner = runner(binding, null, invokables)) {
            assertUntypedInteger(
                    3, runner.invokeGuestFunction("from_js", "evaluate", List.of("1 + 2"), code));
            assertUntypedDecimal(
                    3.14, runner.invokeGuestFunction("from_js", "evaluate", List.of("3.14"), code));
            assertEquals(
                    true, runner.invokeGuestFunction("from_js", "evaluate", List.of("true"), code));
            assertEquals(
                    "s", runner.invokeGuestFunction("from_js", "evaluate", List.of("'s'"), code));
            assertNull(runner.invokeGuestFunction("from_js", "evaluate", List.of("null"), code));
            assertNull(
                    runner.invokeGuestFunction("from_js", "evaluate", List.of("undefined"), code));
            assertInstanceOf(
                    List.class,
                    runner.invokeGuestFunction("from_js", "evaluate", List.of("[1, 2]"), code));
            var map =
                    assertInstanceOf(
                            Map.class,
                            runner.invokeGuestFunction(
                                    "from_js", "evaluate", List.of("({ a: 1 })"), code));
            assertUntypedInteger(1, map.get("a"));
        }
    }

    @Test
    public void hostRefsFromHostFunctions() {
        var created = new AtomicReference<Object>();
        var received = new AtomicReference<List<Object>>();
        var binding = Binding.create(codec());
        var builtins =
                binding.builtins(
                        "from_java",
                        fn(
                                "create",
                                List.of(Integer.class, Integer.class),
                                HostRef.class,
                                args -> {
                                    var point = new Object();
                                    created.set(point);
                                    return point;
                                }),
                        consumer("check", received, HostRef.class, HostRef.class));

        exec(
                builtins,
                "const p1 = from_java.create(10, 20);\n" + "from_java.check(p1, [p1, p1]);\n");

        assertSame(created.get(), received.get().get(0));
        assertEquals(List.of(created.get(), created.get()), received.get().get(1));
    }

    @Test
    public void hostRefsInGuestFunctions() {
        var binding = Binding.create(codec());
        var builtins =
                binding.builtins(
                        "from_java",
                        fn(
                                "get_x",
                                List.of(HostRef.class),
                                Integer.class,
                                args -> ((Point) args.get(0)).x));
        var invokables =
                binding.invokables(
                        "from_js",
                        new TypedGuestFunction("x_even", List.of(HostRef.class), Boolean.class),
                        new TypedGuestFunction(
                                "choose", List.of(HostRef.class, HostRef.class), HostRef.class));
        var code =
                "function x_even(point_ptr) { return from_java.get_x(point_ptr) % 2 === 0; };\n"
                        + "function choose(p1, p2) {"
                        + " return from_java.get_x(p1) < from_java.get_x(p2) ? p1 : p2; };\n";

        try (var runner = runner(binding, builtins, invokables)) {
            assertFalse(
                    (Boolean)
                            runner.invokeGuestFunction(
                                    "from_js", "x_even", List.of(new Point(1, 3)), code));
            assertTrue(
                    (Boolean)
                            runner.invokeGuestFunction(
                                    "from_js", "x_even", List.of(new Point(2, 3)), code));
            var first = new Point(1, 4);
            assertSame(
                    first,
                    runner.invokeGuestFunction(
                            "from_js", "choose", List.of(first, new Point(2, 5)), code));
        }
    }

    @Test
    public void precompiledGuestFunctions() {
        var binding = Binding.create(codec());
        var invokables =
                binding.invokables(
                        "js_api",
                        new TypedGuestFunction(
                                "add", List.of(Integer.class, Integer.class), Integer.class));
        var engine = Engine.builder().addInvokables(invokables).build();
        var compiled = engine.compilePortableGuestFunction("function add(x, y) { return x + y; }");

        try (var runner = binding.bind(Runner.builder().withEngine(engine).build())) {
            assertEquals(
                    43,
                    runner.invokePrecompiledGuestFunction(
                            "js_api", "add", List.of(39, 4), compiled));
        }
    }

    @Test
    public void zodBundle() {
        var tuna = new AtomicReference<List<Object>>();
        var number = new AtomicReference<List<Object>>();
        var binding = Binding.create(codec());
        var builtins =
                binding.builtins(
                        "from_java",
                        consumer("java_check_tuna", tuna, ZodResult.class),
                        consumer("java_check_number", number, ZodResult.class));

        exec(builtins, resource("zod/dist/out.js"));

        var tunaResult = (ZodResult) tuna.get().get(0);
        assertTrue(tunaResult.success);
        assertEquals("tuna", tunaResult.data);

        var numberResult = (ZodResult) number.get().get(0);
        assertFalse(numberResult.success);
        assertEquals("invalid_type", numberResult.error.issues[0].code);
        assertEquals("number", numberResult.error.issues[0].received);
        assertEquals("string", numberResult.error.issues[0].expected);
    }

    @Test
    public void asyncGuestFunction() {
        var binding = Binding.create(codec());
        var invokables =
                binding.invokables(
                        "from_js",
                        new TypedGuestFunction(
                                "validateString", List.of(String.class), ZodResult.class));

        try (var runner = runner(binding, null, invokables)) {
            var result =
                    (ZodResult)
                            runner.invokeGuestFunction(
                                    "from_js",
                                    "validateString",
                                    List.of("tuna"),
                                    resource("zod-async/dist/out.js"));
            assertTrue(result.success);
            assertEquals("tuna", result.data);
        }
    }

    @Test
    public void exceptionsThrownInJava() {
        var binding = Binding.create(codec());
        var builtins =
                binding.builtins(
                        "from_java",
                        fn(
                                "boom",
                                List.of(String.class),
                                String.class,
                                args -> {
                                    throw new IndexOutOfBoundsException("whatever");
                                }));

        var ex =
                assertThrows(
                        IndexOutOfBoundsException.class,
                        () -> exec(builtins, "from_java.boom(\"ciao\");"));
        assertEquals("whatever", ex.getMessage());
    }

    @Test
    public void argumentsThatDoNotMatchTheDeclaredType() {
        var received = new AtomicReference<List<Object>>();
        var binding = Binding.create(codec());
        var builtins = binding.builtins("from_java", consumer("check", received, Point.class));

        assertThrows(JsonCodecException.class, () -> exec(builtins, "from_java.check([1, 2]);"));
        assertThrows(
                JsonCodecException.class,
                () -> exec(builtins, "java_invoke(\"from_java\", \"check\", \"[{} {}]\");"));
        assertNull(received.get());
    }
}
