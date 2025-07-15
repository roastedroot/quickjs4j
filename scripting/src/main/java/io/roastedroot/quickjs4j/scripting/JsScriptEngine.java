package io.roastedroot.quickjs4j.scripting;

import io.roastedroot.quickjs4j.core.Engine;
import io.roastedroot.quickjs4j.core.GuestException;
import io.roastedroot.quickjs4j.core.GuestFunction;
import io.roastedroot.quickjs4j.core.Invokables;
import io.roastedroot.quickjs4j.core.Runner;
import io.roastedroot.quickjs4j.core.ScriptCache;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

public class JsScriptEngine extends AbstractScriptEngine {

    private final Runner runner;

    public JsScriptEngine() {
        this(new ScriptCache());
    }

    public JsScriptEngine(ScriptCache cache) {
        var engine =
                Engine.builder()
                        .withCache(cache)
                        .addInvokables(
                                Invokables.builder("quickjs4jScripting")
                                        .add(
                                                new GuestFunction(
                                                        "quickjsEval",
                                                        List.of(Object.class),
                                                        Object.class))
                                        .build())
                        .build();
        this.runner = Runner.builder().withEngine(engine).build();
    }

    @Override
    public Object eval(String script, ScriptContext context) throws ScriptException {
        String scriptWithBindings = injectEval(script);
        try {
            var result =
                    runner.invokeGuestFunction(
                            "quickjs4jScripting",
                            "quickjsEval",
                            List.of(getBindings(context)),
                            scriptWithBindings);
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
        return "export function quickjsEval(bindings) {\n"
                + "  for (const [key, value] of Object.entries(bindings)) {\n"
                + "    globalThis[key] = value;\n"
                + "  };\n"
                + script
                + "\n"
                + "}";
    }

    private Map<String, Object> getBindings(ScriptContext context) {
        // Merge GLOBAL_SCOPE and ENGINE_SCOPE, ENGINE_SCOPE takes precedence
        Bindings engineBindings = context.getBindings(ScriptContext.ENGINE_SCOPE);
        Bindings globalBindings = context.getBindings(ScriptContext.GLOBAL_SCOPE);
        Map<String, Object> bindings = new HashMap<>();
        if (globalBindings != null) {
            for (String key : globalBindings.keySet()) {
                if (engineBindings == null || !engineBindings.containsKey(key)) {
                    bindings.put(key, toJsLiteral(globalBindings.get(key)));
                }
            }
        }
        if (engineBindings != null) {
            for (String key : engineBindings.keySet()) {
                bindings.put(key, toJsLiteral(engineBindings.get(key)));
            }
        }
        return bindings;
    }

    private String toJsLiteral(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        } else {
            return value.toString();
        }
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
    }
}
