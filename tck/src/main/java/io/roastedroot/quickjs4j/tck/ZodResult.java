package io.roastedroot.quickjs4j.tck;

/** Result of zod's {@code safeParse}, as produced by the bundled zod tests. */
public final class ZodResult {
    public boolean success;
    public String data;
    public ZodError error;
}
