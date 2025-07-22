package io.roastedroot.quickjs4j.core;

public interface ScriptCache {
    boolean exists(byte[] code);

    void set(byte[] code, byte[] compiled);

    byte[] get(byte[] code);
}
