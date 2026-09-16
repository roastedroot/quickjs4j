package io.roastedroot.quickjs4j.core;

import java.util.Optional;
import run.endive.runtime.Instance;

/**
 * Service loaded by {@link Engine} to replace the default, build time compiled, javy plugin
 * instance (e.g. with a native one from quickjs4j-redline-experimental).
 */
public interface InstanceBuilderProvider {
    /**
     * @return a builder for the javy plugin module, or empty when not supported on this platform
     */
    Optional<Instance.Builder> builder();
}
