package io.roastedroot.quickjs4j.example;

import io.roastedroot.quickjs4j.annotations.HostFunction;
import io.roastedroot.quickjs4j.core.Engine;
import io.roastedroot.quickjs4j.core.Runner;

@InvokableModule("js_artifact_store")
interface JsArtifactStore {

    @GuestFunction("add")
    int add();
}

class MyFinalTightUpThings {
    public ArtifactStore() {
        var builtins = ArtifactStore_Builtins.build();
        var invokables = JsArtifactStore_Invokables.build();

        var engine = Engine.builder().addBuiltins(builtins).addInvokables(invokables).build();
        this.runner = Runner.builder().withEngine(engine).withTimeoutMs(5000).build();
    }
}

@BuiltinModule("artifact_store")
abstract class ArtifactStore implements AutoCloseable {
    private final Runner runner;

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

    public void add(int one, int two) {
        // implementation is generated
        super(one, two);
        ArtifactStore_Impl.add(one, two);
    }

    //    @GuestFunction("add")
    //    public void add(int one, int two) {
    //        // implementation is generated
    //        ArtifactStore_Impl.add(one, two);
    //    }

    @Override
    public void close() {
        runner.close();
    }
}
