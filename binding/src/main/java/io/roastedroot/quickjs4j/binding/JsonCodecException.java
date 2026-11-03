package io.roastedroot.quickjs4j.binding;

/** A value could not be mapped to or from JSON. */
public class JsonCodecException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public JsonCodecException(String message, Throwable cause) {
        super(message, cause);
    }

    public JsonCodecException(String message) {
        super(message);
    }
}
