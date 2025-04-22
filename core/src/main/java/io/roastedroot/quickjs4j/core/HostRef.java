package io.roastedroot.quickjs4j.core;

import java.util.Objects;

public final class HostRef {
    private final int ptr;
    private final Object ref;

    private HostRef(int ptr, Object ref) {
        this.ptr = ptr;
        this.ref = ref;
    }

    public int pointer() {
        return ptr;
    }

    public Object reference() {
        return ref;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HostRef)) {
            return false;
        }
        HostRef ref = (HostRef) o;
        return ptr == ref.ptr && Objects.equals(ref, ref.ref);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ptr, ref);
    }
}
