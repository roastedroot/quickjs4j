package io.roastedroot.quickjs4j.core;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class Runner implements AutoCloseable {
    private final int timeoutMs;
    private final int compilationTimeoutMs;
    private final Engine engine;
    private final ExecutorService es;

    private Runner(Engine engine, int timeout, int compilationTimeout, ExecutorService es) {
        this.engine = engine;
        this.es = es;
        this.timeoutMs = timeout;
        this.compilationTimeoutMs = compilationTimeout;
    }

    public byte[] compile(String code) {
        return compile(code.getBytes(StandardCharsets.UTF_8));
    }

    public byte[] compile(byte[] code) {
        return submitWithTimeout(
                () -> {
                    int codePtr = engine.compile(code);
                    try {
                        return engine.readCompiled(codePtr);
                    } finally {
                        engine.free(codePtr);
                    }
                },
                this.compilationTimeoutMs,
                "Timeout while compiling");
    }

    public void exec(byte[] jsBytecode) {
        submitWithTimeout(
                () -> {
                    int codePtr = engine.writeCompiled(jsBytecode);
                    try {
                        this.engine.exec(codePtr);
                    } finally {
                        engine.free(codePtr);
                    }
                    return null;
                },
                this.timeoutMs,
                "Timeout while executing");
    }

    public void compileAndExec(String code) {
        var compiled = compile(code);
        exec(compiled);
    }

    public Object invokeGuestFunction(
            String moduleName, String name, List<Object> args, String libraryCode) {
        return submitWithTimeout(
                () -> engine.invokeGuestFunction(moduleName, name, args, libraryCode),
                this.timeoutMs,
                "Timeout while invoking guest function");
    }

    public String stdout() {
        return this.engine.stdout();
    }

    public String stderr() {
        return this.engine.stderr();
    }

    @Override
    public void close() {
        if (es != null) {
            // shutdownNow interrupts running tasks, which matters when no timeout
            // is configured and close() is called while a task is still executing
            es.shutdownNow();
        }
        if (engine != null) {
            engine.close();
        }
    }

    private <T> T submitWithTimeout(Callable<T> task, int timeout, String timeoutMessage) {
        Future<T> fut = es.submit(task);
        try {
            if (timeout != -1) {
                return fut.get(timeout, TimeUnit.MILLISECONDS);
            } else {
                return fut.get();
            }
        } catch (TimeoutException e) {
            fut.cancel(true);
            throw new RuntimeException(timeoutMessage, e);
        } catch (InterruptedException e) {
            throw new RuntimeException("Thread interrupted", e);
        } catch (ExecutionException e) {
            if (e.getCause() != null) {
                sneakyThrow(e.getCause());
            }
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Engine engine;
        private int timeout = -1;
        private int compilationTimeout = -1;
        private ExecutorService es;

        public Builder withExecutorService(ExecutorService es) {
            this.es = es;
            return this;
        }

        public Builder withEngine(Engine engine) {
            this.engine = engine;
            return this;
        }

        public Builder withTimeoutMs(int timeoutMs) {
            this.timeout = timeoutMs;
            return this;
        }

        public Builder withCompilationTimeoutMs(int compilationTimeoutMs) {
            this.compilationTimeout = compilationTimeoutMs;
            return this;
        }

        public Runner build() {
            if (this.engine == null) {
                this.engine = Engine.builder().build();
            }
            if (this.es == null) {
                this.es = Executors.newSingleThreadExecutor();
            }
            return new Runner(this.engine, this.timeout, this.compilationTimeout, this.es);
        }
    }
}
