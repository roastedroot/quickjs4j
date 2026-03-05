package io.roastedroot.quickjs4j.core;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
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

    private String cacheKey(byte[] code) {
        return Base64.getEncoder().encodeToString(messageDigest.digest(code));
    }

    public boolean exists(byte[] code) {
        return cache.containsKey(cacheKey(code));
    }

    public void set(byte[] code, byte[] compiled) {
        cache.put(cacheKey(code), compiled);
    }

    public byte[] get(byte[] code) {
        return cache.get(cacheKey(code));
    }

    public void close() {
        cache.clear();
    }
}
