package io.roastedroot.quickjs4j.scripting;

import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

public class JsCompiledScript extends CompiledScript {

    private final JsScriptEngine engine;
    private final String script;

    JsCompiledScript(JsScriptEngine engine, String script) {
        this.engine = engine;
        this.script = script;
    }

    @Override
    public Object eval(ScriptContext context) throws ScriptException {
        return engine.eval(script, context);
    }

    @Override
    public ScriptEngine getEngine() {
        return engine;
    }
}
