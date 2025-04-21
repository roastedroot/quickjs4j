package io.roastedroot.js;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class JsMachine implements AutoCloseable {
    private final Map<String, byte[]> cache = new HashMap<>();
    private final MessageDigest md;
    private final int timeoutMs;
    private final JsEngine engine;

    private final ExecutorService es;

    private JsMachine(MessageDigest md, JsEngine engine, int timeout) {
        this.md = md;
        this.engine = engine;
        this.es = Executors.newSingleThreadExecutor();
        this.timeoutMs = timeout;
    }

    private String computeKey(byte[] code) {
        byte[] hash = md.digest(code);
        return new String(hash, StandardCharsets.UTF_8);
    }

    public byte[] compile(String code) {
        byte[] codeBytes = code.getBytes(StandardCharsets.UTF_8);
        var key = computeKey(codeBytes);

        if (!cache.containsKey(key)) {
            int codePtr = engine.compile(codeBytes);
            var value = engine.readCompiled(codePtr);
            engine.free(codePtr);
            cache.put(key, value);
            return value;
        } else {
            return cache.get(key);
        }
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
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
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
        private MessageDigest md;
        private JsEngine engine;
        private int timeout = -1;

        public Builder withMessageDigest(MessageDigest md) {
            this.md = md;
            return this;
        }

        public Builder withEngine(JsEngine engine) {
            this.engine = engine;
            return this;
        }

        public Builder withTimeoutMs(int timeoutMs) {
            this.timeout = timeoutMs;
            return this;
        }

        public JsMachine build() {
            if (this.md == null) {
                try {
                    this.md = MessageDigest.getInstance("MD5");
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException("Missing MD5 algorithm on the platform.", e);
                }
            }
            if (this.engine == null) {
                this.engine = JsEngine.builder().build();
            }
            return new JsMachine(this.md, this.engine, this.timeout);
        }
    }
}
