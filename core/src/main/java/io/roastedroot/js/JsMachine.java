package io.roastedroot.js;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

public final class JsMachine {
    private final Map<String, byte[]> cache = new HashMap<>();
    private final MessageDigest md;
    private final JsEngine engine;

    private JsMachine(MessageDigest md, JsEngine engine) {
        this.md = md;
        this.engine = engine;
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
            this.engine.exec(codePtr);
        } finally {
            engine.free(codePtr);
        }
    }

    public void compileAndExec(String code) {
        var compiled = compile(code);
        exec(compiled);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private MessageDigest md;
        private JsEngine engine;

        public Builder withMessageDigest(MessageDigest md) {
            this.md = md;
            return this;
        }

        public Builder withEngine(JsEngine engine) {
            this.engine = engine;
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
            return new JsMachine(this.md, this.engine);
        }
    }
}
