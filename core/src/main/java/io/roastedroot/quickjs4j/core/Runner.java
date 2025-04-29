package io.roastedroot.quickjs4j.core;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class Runner implements AutoCloseable {
    private final int timeoutMs;
    private final Engine engine;

    private final ExecutorService es;

    private Runner(Engine engine, int timeout) {
        this.engine = engine;
        this.es = Executors.newSingleThreadExecutor();
        this.timeoutMs = timeout;
    }

    public byte[] compile(String code) {
        byte[] codeBytes = code.getBytes(StandardCharsets.UTF_8);
        int codePtr = engine.compile(codeBytes);
        var value = engine.readCompiled(codePtr);
        engine.free(codePtr);
        return value;
    }

    public void exec(byte[] jsBytecode) {
        int codePtr = engine.writeCompiled(jsBytecode);
        try {
            var fut = es.submit(() -> this.engine.exec(codePtr));

            if (this.timeoutMs != -1) {
                fut.get(this.timeoutMs, TimeUnit.MILLISECONDS);
            } else {
                fut.get();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Thread interrupted", e);
        } catch (ExecutionException e) {
            // in this case the ExecutionException wraps the underlying Exception
            if (e.getCause() != null) {
                if (e.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) e.getCause();
                } else {
                    throw new RuntimeException(e.getCause());
                }
            } else {
                // fallback
                throw new RuntimeException(e);
            }
        } catch (TimeoutException e) {
            throw new RuntimeException("Timeout while executing", e);
        } finally {
            engine.free(codePtr);
        }
    }

    public void compileAndExec(String code) {
        var compiled = compile(code);
        exec(compiled);
    }

    @Override
    public void close() {
        if (es != null) {
            es.shutdown();
        }
        if (engine != null) {
            engine.close();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Engine engine;
        private int timeout = -1;

        public Builder withEngine(Engine engine) {
            this.engine = engine;
            return this;
        }

        public Builder withTimeoutMs(int timeoutMs) {
            this.timeout = timeoutMs;
            return this;
        }

        public Runner build() {
            if (this.engine == null) {
                this.engine = Engine.builder().build();
            }
            return new Runner(this.engine, this.timeout);
        }
    }
}
