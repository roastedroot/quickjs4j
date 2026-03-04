package io.roastedroot.quickjs4j.core;

import java.io.ByteArrayOutputStream;

public class BoundedByteArrayOutputStream extends ByteArrayOutputStream {
    private final int maxBytes;

    public BoundedByteArrayOutputStream(int maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    public synchronized void write(int b) {
        if (size() >= maxBytes) {
            throw new RuntimeException("Output stream exceeded limit of " + maxBytes + " bytes");
        }
        super.write(b);
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) {
        if (size() + len > maxBytes) {
            throw new RuntimeException("Output stream exceeded limit of " + maxBytes + " bytes");
        }
        super.write(b, off, len);
    }

    @Override
    public synchronized void writeBytes(byte[] b) {
        if (size() + b.length > maxBytes) {
            throw new RuntimeException("Output stream exceeded limit of " + maxBytes + " bytes");
        }
        super.writeBytes(b);
    }
}
