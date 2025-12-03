package io.roastedroot.quickjs4j.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

public class BasicScriptCache implements ScriptCache, AutoCloseable {
    private static final String DEFAULT_MESSAGE_DIGEST_ALGORITHM = "SHA-256";

    private final HashMap<String, byte[]> cache;
    private final MessageDigest messageDigest;

    public BasicScriptCache() {
        this(DEFAULT_MESSAGE_DIGEST_ALGORITHM);
    }

    public BasicScriptCache(String messageDigestAlgorithm) {
        cache = new HashMap<>();
        try {
            messageDigest = MessageDigest.getInstance(messageDigestAlgorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean exists(byte[] code) {
        var key = messageDigest.digest(code);
        return cache.containsKey(new String(key, StandardCharsets.UTF_8));
    }

    public void set(byte[] code, byte[] compiled) {
        var key = messageDigest.digest(code);
        cache.put(new String(key, StandardCharsets.UTF_8), compiled);
    }

    public byte[] get(byte[] code) {
        var key = messageDigest.digest(code);
        return cache.get(new String(key, StandardCharsets.UTF_8));
    }

    public void close() {
        cache.clear();
    }
}
