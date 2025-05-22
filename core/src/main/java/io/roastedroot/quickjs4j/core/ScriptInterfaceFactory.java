package io.roastedroot.quickjs4j.core;

@FunctionalInterface
public interface ScriptInterfaceFactory<T, C> {

    T create(String scriptLibrary, C context);
}
