package io.roastedroot.quickjs4j.jackson2;

import io.roastedroot.quickjs4j.core.JsonCodec;
import io.roastedroot.quickjs4j.core.JsonCodecProvider;

public final class Jackson2CodecProvider implements JsonCodecProvider {
    @Override
    public JsonCodec create() {
        return Jackson2Codec.builder().build();
    }

    @Override
    public int priority() {
        return 2;
    }
}
