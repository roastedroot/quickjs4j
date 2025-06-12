package io.roastedroot.quickjs4j.core;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

public class ScriptCache implements AutoCloseable {
    private final HashMap<byte[], byte[]> cache;
    private final MessageDigest messageDigest;

    public ScriptCache() {
        cache = new HashMap<>();
        try {
            messageDigest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean exists(byte[] code) {
        var key = messageDigest.digest(code);
        return cache.containsKey(key);
    }

    public void set(byte[] code, byte[] compiled) {
        var key = messageDigest.digest(code);
        cache.put(key, compiled);
    }

    public byte[] get(byte[] code) {
        var key = messageDigest.digest(code);
        return cache.get(key);
    }

    public void close() {
        cache.clear();
    }
}
