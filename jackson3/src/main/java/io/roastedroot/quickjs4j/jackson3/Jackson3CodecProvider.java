package io.roastedroot.quickjs4j.jackson3;

import io.roastedroot.quickjs4j.core.JsonCodec;
import io.roastedroot.quickjs4j.core.JsonCodecProvider;

public final class Jackson3CodecProvider implements JsonCodecProvider {
    @Override
    public JsonCodec create() {
        return Jackson3Codec.builder().build();
    }

    @Override
    public int priority() {
        return 3;
    }
}
