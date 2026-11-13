package io.roastedroot.quickjs4j.core;

final class Identifiers {
    private Identifiers() {}

    // Names end up in generated JS (globalThis.module.fn) and in JSON strings without escaping.
    static String check(String name, String kind) {
        if (name == null || name.isEmpty() || !Character.isJavaIdentifierStart(name.charAt(0))) {
            throw invalid(name, kind);
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isJavaIdentifierPart(c) || Character.isIdentifierIgnorable(c)) {
                throw invalid(name, kind);
            }
        }
        return name;
    }

    private static IllegalArgumentException invalid(String name, String kind) {
        return new IllegalArgumentException(
                "Invalid " + kind + " name: '" + name + "', it must be a valid identifier");
    }
}
