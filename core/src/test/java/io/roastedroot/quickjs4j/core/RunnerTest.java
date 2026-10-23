package io.roastedroot.quickjs4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

public class RunnerTest {

    @Test
    public void basicUsage() {
        var invoked = new AtomicBoolean(false);
        var builtins =
                Builtins.builder("from_java")
                        .add(
                                "java_check",
                                args -> {
                                    assertEquals(List.of("42"), args);
                                    invoked.set(true);
                                    return null;
                                })
                        .build();
        var jsEngine = Engine.builder().addBuiltins(builtins).build();

        try (var runner = Runner.builder().withEngine(jsEngine).build()) {
            runner.compileAndExec("from_java.java_check(42);");
        }

        assertTrue(invoked.get());
    }

    @Test
    public void fullUsage() {
        var invoked = new AtomicBoolean(false);
        var builtins =
                Builtins.builder("from_java")
                        .add(
                                "java_check",
                                args -> {
                                    assertEquals(List.of("42"), args);
                                    invoked.set(true);
                                    return null;
                                })
                        .build();
        var invokables = Invokables.builder("from_js").add("js_func").build();
        var libraryCode = "function js_func(x, y) { from_java.java_check(x); return x * y; };";
        var jsEngine = Engine.builder().addBuiltins(builtins).addInvokables(invokables).build();

        try (var runner = Runner.builder().withEngine(jsEngine).build()) {
            var result =
                    runner.invokeGuestFunction(
                            "from_js", "js_func", List.of("42", "2"), libraryCode);
            assertEquals("84", result);
        }

        assertTrue(invoked.get());
    }

    @Test
    public void precompiledGuestFunction() {
        var invokables = Invokables.builder("from_js").add("mul").build();
        var jsEngine = Engine.builder().addInvokables(invokables).build();
        var compiled =
                jsEngine.compilePortableGuestFunction("function mul(x, y) { return x * y; }");

        try (var runner = Runner.builder().withEngine(jsEngine).build()) {
            assertEquals(
                    "6",
                    runner.invokePrecompiledGuestFunction(
                            "from_js", "mul", List.of("2", "3"), compiled));
        }
    }

    @Test
    public void minimalDocsExample() {
        try (var runner = Runner.builder().build()) {
            runner.compileAndExec("console.log(\"Hello QuickJs4J!\");");
            System.out.println(runner.stdout());
        }
    }

    @Test
    public void withTimeout() throws Exception {
        var es = Executors.newSingleThreadExecutor();
        var runner = Runner.builder().withTimeoutMs(500).withExecutorService(es).build();

        var ex =
                assertThrows(
                        RuntimeException.class, () -> runner.compileAndExec("while (true) { };"));

        assertTrue(ex.getCause() instanceof TimeoutException);
        assertTrue(
                ex.getMessage().contains("Timeout while executing"),
                "Expected execution timeout, got: " + ex.getMessage());

        // cancel(true) interrupts the WASM execution on timeout, so the thread
        // should be free to accept new work rather than stuck in the infinite loop
        var probe = es.submit(() -> "ok");
        assertEquals("ok", probe.get(5, java.util.concurrent.TimeUnit.SECONDS));

        runner.close();
    }

    @Test
    public void compileTimeout() throws Exception {
        var es = Executors.newSingleThreadExecutor();
        var runner = Runner.builder().withCompilationTimeoutMs(500).withExecutorService(es).build();

        var sb = new StringBuilder();
        for (int i = 0; i < 200_000; i++) {
            sb.append("var v").append(i).append("=").append(i).append(";");
        }
        var script = sb.toString();

        var ex = assertThrows(RuntimeException.class, () -> runner.compile(script));

        assertTrue(ex.getCause() instanceof TimeoutException);
        assertTrue(
                ex.getMessage().contains("Timeout while compiling"),
                "Expected compilation timeout, got: " + ex.getMessage());

        // cancel(true) interrupts the compilation on timeout, so the thread
        // should be free to accept new work rather than stuck compiling
        var probe = es.submit(() -> "ok");
        assertEquals("ok", probe.get(5, java.util.concurrent.TimeUnit.SECONDS));

        runner.close();
    }

    @Test
    public void invokeGuestFunctionTimeout() throws Exception {
        var invokables = Invokables.builder("from_js").add("hang").build();

        var libraryCode = "function hang() { while(true) {} return 1; };";

        var es = Executors.newSingleThreadExecutor();
        var jsEngine = Engine.builder().addInvokables(invokables).build();
        var runner =
                Runner.builder()
                        .withEngine(jsEngine)
                        .withTimeoutMs(500)
                        .withExecutorService(es)
                        .build();

        var ex =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                runner.invokeGuestFunction(
                                        "from_js", "hang", List.of(), libraryCode));

        assertTrue(ex.getCause() instanceof TimeoutException);
        assertTrue(
                ex.getMessage().contains("Timeout while invoking guest function"),
                "Expected execution timeout, got: " + ex.getMessage());

        // Verify the executor thread is free after timeout
        var probe = es.submit(() -> "ok");
        assertEquals("ok", probe.get(5, java.util.concurrent.TimeUnit.SECONDS));

        runner.close();
    }

    @Test
    public void handleExceptionsThrownInJava() {
        var builtins =
                Builtins.builder("from_java")
                        .add(
                                "imported_function",
                                args -> {
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

    @Test
    public void boundedStdoutStopsExecution() throws Exception {
        var boundedStdout = new BoundedByteArrayOutputStream(1024);
        var es = Executors.newSingleThreadExecutor();
        var engine = Engine.builder().withStdout(boundedStdout).build();
        var runner = Runner.builder().withEngine(engine).withExecutorService(es).build();

        // No timeout — the bounded stream should cause the error
        var ex =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                runner.compileAndExec(
                                        "while(true) { console.log('x'.repeat(100)); }"));

        assertTrue(
                ex.getMessage().contains("exceeded limit"),
                "Expected stream limit error, got: " + ex.getMessage());

        // The executor thread should be free after the stream error
        var probe = es.submit(() -> "ok");
        assertEquals("ok", probe.get(5, java.util.concurrent.TimeUnit.SECONDS));

        runner.close();
    }
}
