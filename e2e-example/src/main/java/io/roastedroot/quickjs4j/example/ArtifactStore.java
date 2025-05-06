package io.roastedroot.quickjs4j.example;

import io.roastedroot.quickjs4j.annotations.Builtins;
import io.roastedroot.quickjs4j.annotations.GuestFunction;
import io.roastedroot.quickjs4j.annotations.HostFunction;
import io.roastedroot.quickjs4j.annotations.Invokables;
import io.roastedroot.quickjs4j.core.Engine;
import io.roastedroot.quickjs4j.core.Runner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

class ArtifactStore implements AutoCloseable {
    private final JavaApi javaApi;
    private final Runner runner;
    private final JsApi jsApi;

    private AtomicReference<String> lastMessage = new AtomicReference<>();

    @Invokables
    interface JsApi {
        @GuestFunction("validate")
        boolean validate(Artifact artifact);
    }

    @Builtins("apicurio")
    class JavaApi {
        @HostFunction("log")
        void log(String message) {
            System.out.println(message);
            lastMessage.set(message);
        }
    }

    public ArtifactStore() {
        this.javaApi = new JavaApi();
        var engine =
                Engine.builder()
                        .addBuiltins(JavaApi_Builtins.toBuiltins(this.javaApi))
                        .addInvokables(JsApi_Invokables.toInvokables())
                        .build();
        this.runner = Runner.builder().withEngine(engine).build();
        String jsLibrary;
        try {
            jsLibrary =
                    new String(
                            ArtifactStore.class
                                    .getResourceAsStream("/library/dist/out.js")
                                    .readAllBytes(),
                            StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.jsApi = JsApi_Invokables.create(jsLibrary, runner);
    }

    public JsApi api() {
        return this.jsApi;
    }

    public String lastMessage() {
        return this.lastMessage.get();
    }

    @Override
    public void close() {
        runner.close();
    }
}
