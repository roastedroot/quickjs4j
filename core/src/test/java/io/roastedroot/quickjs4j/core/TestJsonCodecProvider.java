package io.roastedroot.quickjs4j.core;

public final class TestJsonCodecProvider implements JsonCodecProvider {
    @Override
    public JsonCodec create() {
        return new TestJsonCodec();
    }

    @Override
    public int priority() {
        return 0;
    }
}
