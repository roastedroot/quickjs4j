package io.roastedroot.quickjs4j.scripting;

import io.roastedroot.quickjs4j.core.Engine;
import io.roastedroot.quickjs4j.core.GuestException;
import io.roastedroot.quickjs4j.core.GuestFunction;
import io.roastedroot.quickjs4j.core.Invokables;
import io.roastedroot.quickjs4j.core.Runner;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

public class JsScriptEngine extends AbstractScriptEngine {

    private final Runner runner;

    private final Runner textEncoderRunner;

    public JsScriptEngine() {
        this(
                Engine.builder()
                        .addInvokables(
                                Invokables.builder("quickjs4jScripting")
                                        .add(
                                                new GuestFunction(
                                                        "quickjsEval", List.of(), Object.class))
                                        .build())
                        .build());
    }

    public JsScriptEngine(Engine engine) {
        this(Runner.builder().withEngine(engine).build());
    }

    public JsScriptEngine(Runner runner) {
        this.runner = runner;

        var textEncoderEngine =
                Engine.builder()
                        .addInvokables(
                                Invokables.builder("textencoder")
                                        .add(
                                                new GuestFunction(
                                                        "encode",
                                                        List.of(String.class),
                                                        String.class))
                                        .build())
                        .build();
        this.textEncoderRunner = Runner.builder().withEngine(textEncoderEngine).build();
    }

    @Override
    public Object eval(String script, ScriptContext context) throws ScriptException {
        String scriptWithBindings = injectBindings(injectEval(script), context);
        try {
            var result =
                    runner.invokeGuestFunction(
                            "quickjs4jScripting", "quickjsEval", List.of(), scriptWithBindings);
            if (runner.stdout() != null && runner.stdout().length() > 0) {
                System.out.println(runner.stdout());
            }
            if (runner.stderr() != null && runner.stderr().length() > 0) {
                System.err.println(runner.stderr());
            }
            return result;
        } catch (GuestException e) {
            throw new ScriptException(e.getMessage());
        } catch (RuntimeException e) {
            throw new ScriptException(e);
        }
    }

    @Override
    public Object eval(Reader reader, ScriptContext context) throws ScriptException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[1024];
        int n;
        try {
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
        } catch (IOException e) {
            throw new ScriptException(e);
        }
        return eval(sb.toString(), context);
    }

    private String injectEval(String script) {
        return "export function quickjsEval() { " + script + " }";
    }

    private String injectBindings(String script, ScriptContext context) {
        // Merge GLOBAL_SCOPE and ENGINE_SCOPE, ENGINE_SCOPE takes precedence
        Bindings engineBindings = context.getBindings(ScriptContext.ENGINE_SCOPE);
        Bindings globalBindings = context.getBindings(ScriptContext.GLOBAL_SCOPE);
        StringBuilder sb = new StringBuilder();
        if (globalBindings != null) {
            for (String key : globalBindings.keySet()) {
                if (engineBindings == null || !engineBindings.containsKey(key)) {
                    sb.append(jsGlobalDecl(key, globalBindings.get(key)));
                }
            }
        }
        if (engineBindings != null) {
            for (String key : engineBindings.keySet()) {
                sb.append(jsGlobalDecl(key, engineBindings.get(key)));
            }
        }
        sb.append(script);
        return sb.toString();
    }

    private String jsGlobalDecl(String name, Object value) {
        // Only allow valid JS identifiers
        if (!name.matches("[a-zA-Z_$][a-zA-Z0-9_$]*")) {
            return "";
        } else {
            return "globalThis." + name + " = " + toJsLiteral(value) + ";\n";
        }
    }

    private String toJsLiteral(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }

        // here we use the same engine to run the text encoder
        String encoded =
                (String)
                        textEncoderRunner.invokeGuestFunction(
                                "textencoder",
                                "encode",
                                List.of(value.toString()),
                                "function encode(str) { return JSON.stringify(Array.from(new"
                                        + " TextEncoder().encode(str))); }");
        return "new TextDecoder().decode(new Uint8Array(" + encoded + "))";
    }

    @Override
    public Bindings createBindings() {
        return new SimpleBindings();
    }

    @Override
    public ScriptEngineFactory getFactory() {
        return new JsScriptEngineFactory();
    }

    public void close() {
        if (runner != null) {
            runner.close();
        }
        if (textEncoderRunner != null) {
            textEncoderRunner.close();
        }
    }
}
