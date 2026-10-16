package io.roastedroot.quickjs4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The line offset is the only thing a source-map aware layer needs from the engine: it is subtracted from
 * every line QuickJS reports to get back to the caller's own line.
 *
 */
public class JsPreludeLineCountTest {

    private static final Pattern FRAME = Pattern.compile("function\\.mjs:(\\d+):(\\d+)");

    /** The line QuickJS reported for the first stack frame in the guest's error output. */
    private static int firstReportedLine(Runner runner) {
        Matcher matcher = FRAME.matcher(runner.stderr());
        if (!matcher.find()) {
            throw new IllegalStateException("no position reported: " + runner.stderr());
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static Invokables oneGuestFunction() {
        return Invokables.builder("app")
                .add(new GuestFunction("boom", List.<Class>of(), Void.class))
                .build();
    }

    private static Runner runnerWith(Invokables invokables) {
        return Runner.builder()
                .withEngine(Engine.builder().addInvokables(invokables).build())
                .build();
    }

    @Test
    public void countsTheEngineModuleAndItsFunctions() {
        try (Engine engine = Engine.builder().build()) {
            // "globalThis.quickjs4j_engine = {}" plus module_name, function_name and args.
            assertEquals(4, engine.jsPreludeLineCount());
        }
    }

    @Test
    public void growsWithEveryRegisteredBuiltin() {
        Builtins builtins =
                Builtins.builder("from_java")
                        .addStringToString("shout", value -> value.toUpperCase())
                        .build();

        try (Engine engine = Engine.builder().addBuiltins(builtins).build()) {
            // One more module and one more function.
            assertEquals(6, engine.jsPreludeLineCount());
        }
    }

    @Test
    public void growsWithEveryInvokableFunction() {
        Invokables invokables =
                Invokables.builder("app")
                        .add(new GuestFunction("first", List.<Class>of(), Void.class))
                        .add(new GuestFunction("second", List.<Class>of(), Void.class))
                        .build();

        try (Engine engine = Engine.builder().addInvokables(invokables).build()) {
            // The module itself, plus the generated set_result function for each guest function.
            assertEquals(7, engine.jsPreludeLineCount());
        }
    }

    @Test
    public void countsLineBreaksOfEveryFlavour() {
        assertEquals(0, Engine.countLineBreaks(null));
        assertEquals(0, Engine.countLineBreaks(""));
        assertEquals(0, Engine.countLineBreaks("one line, no break"));

        assertEquals(1, Engine.countLineBreaks("a\n"));
        assertEquals(2, Engine.countLineBreaks("a\nb\n"));

        assertEquals(2, Engine.countLineBreaks("a\r\nb\r\n"), "\\r\\n is one break, not two");
        assertEquals(2, Engine.countLineBreaks("a\rb\r"), "a lone \\r is a break of its own");
        assertEquals(3, Engine.countLineBreaks("a\nb\r\nc\n"), "mixed endings");
    }

    /**
     * Every position carries the jsPrelude's offset, so a source-map consumer subtracts that one number.
     */
    @Test
    public void reportedPositionsCarryTheJsPreludeOffset() {
        Invokables invokables = oneGuestFunction();

        int viaCompile;
        try (Runner runner = runnerWith(invokables)) {
            assertThrows(
                    GuestException.class,
                    () -> runner.compileAndExec("throw new Error('compiled');"));
            viaCompile = firstReportedLine(runner);
            assertEquals(
                    runner.jsPreludeLineCount() + 1,
                    viaCompile,
                    "a throw on the caller's line 1 is reported one line past the jsPrelude");
        }

        int viaPortableCompile;
        try (Runner runner = runnerWith(invokables)) {
            assertThrows(
                    GuestException.class,
                    () ->
                            runner.invokeGuestFunction(
                                    "app",
                                    "boom",
                                    List.of(),
                                    "function boom() { throw new Error('portable'); }"));
            viaPortableCompile = firstReportedLine(runner);
            assertEquals(
                    runner.jsPreludeLineCount() + 2,
                    viaPortableCompile,
                    "the portable path writes one separating line of its own");
        }

        assertEquals(viaCompile + 1, viaPortableCompile, "the two differ by that separator");
    }
}
