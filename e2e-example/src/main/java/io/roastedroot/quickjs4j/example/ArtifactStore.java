package io.roastedroot.quickjs4j.example;

import io.roastedroot.quickjs4j.annotations.HostFunction;
import io.roastedroot.quickjs4j.annotations.JsModule;
import io.roastedroot.quickjs4j.core.Builtins;
import io.roastedroot.quickjs4j.core.Engine;
import io.roastedroot.quickjs4j.core.Runner;

@JsModule
class ArtifactStore implements AutoCloseable {
    private final Runner runner;

    public ArtifactStore() {
        var builtins = Builtins.builder().add(ArtifactStore_Builtins.toBuiltins(this)).build();
        var engine = Engine.builder().withBuiltins(builtins).build();
        this.runner = Runner.builder().withEngine(engine).build();
    }

    private Artifact artifact;

    public void add(Artifact artifact) {
        this.artifact = artifact;
        runner.compileAndExec("set_validate_result(validate(get_pet()));");
    }

    @HostFunction("get_pet")
    public Artifact get() {
        return artifact;
    }

    private boolean validateResult;

    @HostFunction("set_validate_result")
    public void setValidate(boolean result) {
        validateResult = result;
    }

    @Override
    public void close() {
        runner.close();
    }
}
