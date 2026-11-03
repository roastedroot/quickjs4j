package io.roastedroot.quickjs4j.jackson3;

import io.roastedroot.quickjs4j.binding.JsonCodec;
import io.roastedroot.quickjs4j.tck.AbstractBindingTest;

public class Jackson3BindingTest extends AbstractBindingTest {
    private static final JsonCodec CODEC = new Jackson3Codec();

    @Override
    protected JsonCodec codec() {
        return CODEC;
    }
}
