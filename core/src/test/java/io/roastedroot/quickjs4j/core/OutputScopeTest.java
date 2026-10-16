package io.roastedroot.quickjs4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pins how guest output is scoped, because the answer is not obvious and a caller that gets it wrong
 * gets a diagnostic about somebody else's run.
 *
 * <p>The streams are never cleared implicitly: a failure message is composed from everything buffered,
 * which is what makes it occasionally alarming, and {@link Runner#resetOutput()} is the escape hatch.
 */
public class OutputScopeTest {

    private static final String FIRST_RUN = "console.log('first'); throw new Error('FIRST-RUN');";

    private static final String SECOND_RUN =
            "console.log('second'); throw new Error('SECOND-RUN');";

    private static GuestException runAndFail(Runner runner, String script) {
        return assertThrows(GuestException.class, () -> runner.compileAndExec(script));
    }

    @Test
    public void outputAccumulatesUntilReset() {
        try (Runner runner = Runner.builder().build()) {
            runAndFail(runner, FIRST_RUN);
            GuestException second = runAndFail(runner, SECOND_RUN);

            assertTrue(
                    second.getMessage().contains("FIRST-RUN"),
                    "the earlier run is still buffered, so it is still in the diagnostic");
            assertTrue(second.getMessage().contains("SECOND-RUN"));
            assertTrue(runner.stdout().contains("first"));
            assertTrue(runner.stdout().contains("second"));
        }
    }

    @Test
    public void resetOutputScopesTheNextRun() {
        try (Runner runner = Runner.builder().build()) {
            runAndFail(runner, FIRST_RUN);

            runner.resetOutput();
            GuestException second = runAndFail(runner, SECOND_RUN);

            assertTrue(second.getMessage().contains("SECOND-RUN"));
            assertFalse(
                    second.getMessage().contains("FIRST-RUN"),
                    "after a reset the diagnostic describes one run: " + second.getMessage());
            assertFalse(runner.stderr().contains("FIRST-RUN"));
            assertEquals("second", runner.stdout().strip(), "and so does the console output");
        }
    }

    @Test
    public void resetOutputKeepsTheEngineUsable() {
        try (Runner runner = Runner.builder().build()) {
            runner.resetOutput();

            runner.compileAndExec("console.log('after reset');");

            assertEquals("after reset", runner.stdout().strip());
        }
    }
}
