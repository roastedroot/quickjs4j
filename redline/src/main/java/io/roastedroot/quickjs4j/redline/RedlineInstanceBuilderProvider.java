package io.roastedroot.quickjs4j.redline;

import io.roastedroot.quickjs4j.core.InstanceBuilderProvider;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import run.endive.redline.experimental.api.NativeMachineFactoryProvider;
import run.endive.runtime.Instance;

public final class RedlineInstanceBuilderProvider implements InstanceBuilderProvider {

    // empty on unsupported platforms, so that the core engine keeps being used
    @Override
    public Optional<Instance.Builder> builder() {
        var code = JavyPluginModule.loadNativeCode();
        if (code == null) {
            return Optional.empty();
        }
        return discover().map(provider -> provider.builder(JavyPluginModule.load(), code));
    }

    // Replaces NativeMachineFactoryProvider.discover(), in Endive 1.1.0 it only guards next(),
    // but the classpath ServiceLoader loads the provider class in hasNext(), so the Panama
    // runner (compiled for Java 25) fails the discovery on older JDKs.
    static Optional<NativeMachineFactoryProvider> discover() {
        NativeMachineFactoryProvider best = null;
        var it =
                ServiceLoader.load(
                                NativeMachineFactoryProvider.class,
                                RedlineInstanceBuilderProvider.class.getClassLoader())
                        .iterator();
        while (true) {
            NativeMachineFactoryProvider provider;
            try {
                if (!it.hasNext()) {
                    break;
                }
                provider = it.next();
            } catch (ServiceConfigurationError | LinkageError e) {
                // the failed provider has been consumed, move on to the next one
                continue;
            }
            if (best == null || provider.priority() > best.priority()) {
                best = provider;
            }
        }
        return Optional.ofNullable(best);
    }
}
