package io.roastedroot.quickjs4j.core;

public class GuestException extends RuntimeException {
    public GuestException() {}

    public GuestException(String message) {
        super(message);
    }

    public GuestException(String message, Throwable cause) {
        super(message, cause);
    }

    public GuestException(Throwable cause) {
        super(cause);
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }
}
