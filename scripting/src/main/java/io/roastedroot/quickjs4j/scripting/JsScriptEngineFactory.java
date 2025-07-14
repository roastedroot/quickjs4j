package io.roastedroot.quickjs4j.scripting;

import io.roastedroot.quickjs4j.core.Runner;
import io.roastedroot.quickjs4j.core.Version;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;

public class JsScriptEngineFactory implements ScriptEngineFactory {

    private static final Map<String, Object> parameters = new HashMap<String, Object>();

    static {
        parameters.put(ScriptEngine.NAME, "quickjs4j");
        parameters.put(ScriptEngine.ENGINE, "QuickJs4J");
        parameters.put(ScriptEngine.ENGINE_VERSION, Version.version);
        parameters.put(ScriptEngine.LANGUAGE, "javascript");
        parameters.put(ScriptEngine.LANGUAGE_VERSION, "unknown");
    }

    @Override
    public String getEngineName() {
        return "quickjs4j";
    }

    @Override
    public String getEngineVersion() {
        return Version.version;
    }

    @Override
    public List<String> getExtensions() {
        return List.of("js", "javascript");
    }

    @Override
    public List<String> getMimeTypes() {
        return List.of("application/x-js", "application/x-javascript");
    }

    @Override
    public List<String> getNames() {
        return List.of("js", "javascript", "quickjs", "quickjs4j");
    }

    @Override
    public String getLanguageName() {
        return "javascript";
    }

    @Override
    public String getLanguageVersion() {
        return "unknown";
    }

    @Override
    public Object getParameter(String key) {
        return parameters.get(key);
    }

    @Override
    public String getMethodCallSyntax(String obj, String m, String... args) {
        String ret = "java_invoke(\"" + obj + "\",\"" + m + "\",JSON.stringify([";
        for (int i = 0; i < args.length; i++) {
            ret += args[i];
            if (i < args.length - 1) {
                ret += ",";
            }
        }
        ret += "]);";
        return ret;
    }

    @Override
    public String getOutputStatement(String toDisplay) {
        return "console.log(\"" + toDisplay + "\");";
    }

    @Override
    public String getProgram(String... statements) {
        String ret = "";
        int len = statements.length;
        for (int i = 0; i < len; i++) {
            ret += statements[i] + ";\n";
        }
        return ret;
    }

    private static Runner runner;
    private static Runner textEncoderRunner;

    @Override
    public ScriptEngine getScriptEngine() {
        var engine = new JsScriptEngine(runner, textEncoderRunner);
        if (runner == null) {
            runner = engine.runner();
        }
        if (textEncoderRunner == null) {
            textEncoderRunner = engine.textEncoderRunner();
        }
        return engine;
    }
}
