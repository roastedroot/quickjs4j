package io.roastedroot.quickjs4j.core;

/** Found with ServiceLoader when no codec is set; the highest priority wins. */
public interface JsonCodecProvider {
    JsonCodec create();

    int priority();
}
