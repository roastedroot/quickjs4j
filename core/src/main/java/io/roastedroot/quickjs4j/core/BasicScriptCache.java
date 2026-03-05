package io.roastedroot.quickjs4j.core;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

public class BasicScriptCache implements ScriptCache, AutoCloseable {
    private static final String DEFAULT_MESSAGE_DIGEST_ALGORITHM = "SHA-256";

    private final HashMap<ByteBuffer, byte[]> cache;
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

    private ByteBuffer key(byte[] code) {
        return ByteBuffer.wrap(messageDigest.digest(code));
    }

    public boolean exists(byte[] code) {
        return cache.containsKey(key(code));
    }

    public void set(byte[] code, byte[] compiled) {
        cache.put(key(code), compiled);
    }

    public byte[] get(byte[] code) {
        return cache.get(key(code));
    }

    public void close() {
        cache.clear();
    }
}
