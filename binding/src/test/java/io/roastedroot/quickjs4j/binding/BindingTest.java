package io.roastedroot.quickjs4j.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.quickjs4j.core.Engine;
import io.roastedroot.quickjs4j.core.Runner;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class BindingTest {

    private static void exec(Engine engine, String code) {
        try (var runner = Runner.builder().withEngine(engine).build()) {
            runner.compileAndExec(code);
        }
    }

    private static TypedHostFunction recorder(
            String name, AtomicReference<List<Object>> received, Class<?>... paramTypes) {
        return new TypedHostFunction(
                name,
                Arrays.asList(paramTypes),
                Void.class,
                args -> {
                    received.set(args);
                    return null;
                });
    }

    @Test
    public void missingArgumentsAreNullAndExtraOnesIgnored() {
        var received = new AtomicReference<List<Object>>();
        var binding = Binding.create(new StubCodec());
        var engine =
                Engine.builder()
                        .addBuiltins(
                                binding.builtins(
                                        "from_java",
                                        recorder("f", received, Integer.class, String.class)))
                        .build();

        try (var runner = Runner.builder().withEngine(engine).build()) {
            runner.compileAndExec("from_java.f(1);");
            assertEquals(Arrays.asList(1, null), received.get());

            runner.compileAndExec("from_java.f(1, \"two\", 3);");
            assertEquals(List.of(1, "two"), received.get());
        }
    }

    @Test
    public void voidAndNullResultsAreNull() {
        var received = new AtomicReference<List<Object>>();
        var binding = Binding.create(new StubCodec());
        var builtins =
                binding.builtins(
                        "from_java",
                        new TypedHostFunction("none", List.of(), Void.class, args -> "ignored"),
                        new TypedHostFunction("nothing", List.of(), Integer.class, args -> null),
                        recorder("check", received, Object.class, Object.class));

        exec(
                Engine.builder().addBuiltins(builtins).build(),
                "from_java.check(from_java.none(), from_java.nothing());");

        assertEquals(Arrays.asList(null, null), received.get());
    }

    @Test
    public void hostRefsRoundTrip() {
        var first = new Object();
        var second = new Object();
        var received = new AtomicReference<List<Object>>();
        var binding = Binding.create(new StubCodec());
        var builtins =
                binding.builtins(
                        "from_java",
                        new TypedHostFunction("first", List.of(), HostRef.class, args -> first),
                        new TypedHostFunction("second", List.of(), HostRef.class, args -> second),
                        recorder("check", received, HostRef.class, HostRef.class, HostRef.class));

        exec(
                Engine.builder().addBuiltins(builtins).build(),
                "const a = from_java.first(); const b = from_java.second();"
                        + " from_java.check(b, [a, b], null);");

        var args = received.get();
        assertSame(second, args.get(0));
        assertEquals(List.of(first, second), args.get(1));
        assertEquals(null, args.get(2));
    }

    @Test
    public void invalidHostRefsAreRejected() {
        var received = new AtomicReference<List<Object>>();
        var binding = Binding.create(new StubCodec());
        var builtins =
                binding.builtins(
                        "from_java",
                        new TypedHostFunction("ref", List.of(), HostRef.class, args -> "ref"),
                        recorder("check", received, HostRef.class));

        for (var invalid : List.of("1", "-1", "0.5", "\"0\"", "[0, 7]", "true")) {
            var engine = Engine.builder().addBuiltins(builtins).build();
            var ex =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    exec(
                                            engine,
                                            "from_java.ref(); from_java.check(" + invalid + ");"),
                            invalid);
            assertTrue(ex.getMessage().contains("Invalid host reference"), ex.getMessage());
        }
    }

    @Test
    public void guestFunctions() {
        var binding = Binding.create(new StubCodec());
        var invokables =
                binding.invokables(
                        "from_js",
                        new TypedGuestFunction(
                                "add", List.of(Integer.class, Integer.class), Integer.class),
                        new TypedGuestFunction("same", List.of(HostRef.class), HostRef.class),
                        new TypedGuestFunction("noop", List.of(), Void.class));
        var code =
                "function add(x, y) { return x + y; }\n"
                        + "function same(ref) { return ref; }\n"
                        + "function noop() { return 42; }\n";
        var ref = new Object();

        try (var runner =
                binding.bind(
                        Runner.builder()
                                .withEngine(Engine.builder().addInvokables(invokables).build())
                                .build())) {
            assertEquals(42, runner.invokeGuestFunction("from_js", "add", List.of(40, 2), code));
            assertSame(ref, runner.invokeGuestFunction("from_js", "same", List.of(ref), code));
            assertEquals(null, runner.invokeGuestFunction("from_js", "noop", List.of(), code));
            assertEquals(
                    0,
                    runner.invokeGuestFunction("from_js", "add", Arrays.asList(null, null), code));

            var arity =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> runner.invokeGuestFunction("from_js", "add", List.of(1), code));
            assertTrue(arity.getMessage().contains("expected 2 params"));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> runner.invokeGuestFunction("from_js", "missing", List.of(), code));
        }
    }
}
