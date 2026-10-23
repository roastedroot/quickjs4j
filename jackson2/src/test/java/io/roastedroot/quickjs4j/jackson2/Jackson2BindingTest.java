package io.roastedroot.quickjs4j.jackson2;

import io.roastedroot.quickjs4j.binding.JsonCodec;
import io.roastedroot.quickjs4j.tck.AbstractBindingTest;

public class Jackson2BindingTest extends AbstractBindingTest {
    private static final JsonCodec CODEC = new Jackson2Codec();

    @Override
    protected JsonCodec codec() {
        return CODEC;
    }
}
