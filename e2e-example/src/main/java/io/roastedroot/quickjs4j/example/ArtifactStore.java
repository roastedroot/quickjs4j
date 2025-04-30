package io.roastedroot.quickjs4j.example;

import io.roastedroot.quickjs4j.annotations.HostFunction;
import io.roastedroot.quickjs4j.annotations.JsModule;
import io.roastedroot.quickjs4j.core.Builtins;
import io.roastedroot.quickjs4j.core.Engine;
import io.roastedroot.quickjs4j.core.Runner;

@JsModule("pippo")
class ArtifactStore implements AutoCloseable {
    private final Runner runner;

    public ArtifactStore() {
        var builtins = Builtins.builder().add(ArtifactStore_Builtins.toBuiltins(this)).build();
        var engine = Engine.builder().addBuiltins(builtins).build();
        this.runner = Runner.builder().withEngine(engine).build();
    }

    private Artifact artifact;

    public void add(Artifact artifact) {
        this.artifact = artifact;
        runner.compileAndExec("set_validate_result(validate(get_artifact()));");
    }

    @HostFunction("get_artifact")
    public Artifact get() {
        return artifact;
    }

    private boolean validateResult;

    @HostFunction("set_validate_result")
    public void setValidate(boolean result) {
        validateResult = result;
    }

    @GuestFunction("add")
    public void add(int one, int two) {
        // implementation is generated
        ArtifactStore_Impl.add(one, two);
    }

    @Override
    public void close() {
        runner.close();
    }
}
