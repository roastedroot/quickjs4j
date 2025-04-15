package io.roastedroot.js;

import java.util.Objects;

public final class JavaRef {
    private final int ptr;
    private final Object ref;

    private JavaRef(int ptr, Object ref) {
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
        if (!(o instanceof JavaRef)) {
            return false;
        }
        JavaRef javaRef = (JavaRef) o;
        return ptr == javaRef.ptr && Objects.equals(ref, javaRef.ref);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ptr, ref);
    }
}
