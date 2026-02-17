package io.roastedroot.quickjs4j.scripting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.roastedroot.quickjs4j.core.BasicScriptCache;
import io.roastedroot.quickjs4j.core.Engine;
import io.roastedroot.quickjs4j.core.GuestException;
import io.roastedroot.quickjs4j.core.GuestFunction;
import io.roastedroot.quickjs4j.core.Invokables;
import io.roastedroot.quickjs4j.core.Runner;
import io.roastedroot.quickjs4j.core.ScriptCache;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

public class JsScriptEngine extends AbstractScriptEngine
        implements Compilable, Invocable, AutoCloseable {

    private static final String EVAL_WRAPPER =
            "export function quickjsEval(bindings, script) {\n"
                    + "  for (const [key, value] of Object.entries(bindings)) {\n"
                    + "    globalThis[key] = value;\n"
                    + "  }\n"
                    + "  return (0, eval)(script);\n"
                    + "}";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Runner runner;
    private int lastStdoutLength;
    private int lastStderrLength;

    public JsScriptEngine() {
        this(new BasicScriptCache());
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
                                                        List.of(Object.class, String.class),
                                                        Object.class))
                                        .build())
                        .build();
        this.runner = Runner.builder().withEngine(engine).build();
    }

    @Override
    public Object eval(String script, ScriptContext context) throws ScriptException {
        try {
            var result =
                    runner.invokeGuestFunction(
                            "quickjs4jScripting",
                            "quickjsEval",
                            List.of(getBindings(context), script),
                            EVAL_WRAPPER);
            return result;
        } catch (GuestException e) {
            throw new ScriptException(e.getMessage());
        } catch (RuntimeException e) {
            throw new ScriptException(e);
        } finally {
            handleOutput(context);
        }
    }

    @Override
    public Object eval(Reader reader, ScriptContext context) throws ScriptException {
        return eval(readAll(reader), context);
    }

    // Compilable

    @Override
    public CompiledScript compile(String script) throws ScriptException {
        return new JsCompiledScript(this, script);
    }

    @Override
    public CompiledScript compile(Reader reader) throws ScriptException {
        return compile(readAll(reader));
    }

    // Invocable

    @Override
    public Object invokeFunction(String name, Object... args)
            throws ScriptException, NoSuchMethodException {
        String expr = buildCallExpression(name, args);
        try {
            return eval(expr);
        } catch (ScriptException e) {
            if (isNotAFunctionError(e)) {
                throw new NoSuchMethodException(name);
            }
            throw e;
        }
    }

    @Override
    public Object invokeMethod(Object thiz, String name, Object... args)
            throws ScriptException, NoSuchMethodException {
        if (thiz == null) {
            throw new IllegalArgumentException("thiz cannot be null");
        }
        if (!(thiz instanceof String)) {
            throw new IllegalArgumentException(
                    "thiz must be a String representing a JavaScript variable name");
        }
        String objName = (String) thiz;
        String expr = buildCallExpression(objName + "." + name, args);
        try {
            return eval(expr);
        } catch (ScriptException e) {
            if (isNotAFunctionError(e)) {
                throw new NoSuchMethodException(name);
            }
            throw e;
        }
    }

    @Override
    public <T> T getInterface(Class<T> clazz) {
        if (clazz == null || !clazz.isInterface()) {
            throw new IllegalArgumentException("clazz must be a non-null interface");
        }
        return clazz.cast(
                Proxy.newProxyInstance(
                        clazz.getClassLoader(),
                        new Class<?>[] {clazz},
                        (proxy, method, methodArgs) -> {
                            if (method.getDeclaringClass() == Object.class) {
                                return handleObjectMethod(proxy, method, methodArgs);
                            }
                            return invokeFunction(
                                    method.getName(),
                                    methodArgs != null ? methodArgs : new Object[0]);
                        }));
    }

    @Override
    public <T> T getInterface(Object thiz, Class<T> clazz) {
        if (thiz == null) {
            throw new IllegalArgumentException("thiz cannot be null");
        }
        if (!(thiz instanceof String)) {
            throw new IllegalArgumentException(
                    "thiz must be a String representing a JavaScript variable name");
        }
        if (clazz == null || !clazz.isInterface()) {
            throw new IllegalArgumentException("clazz must be a non-null interface");
        }
        return clazz.cast(
                Proxy.newProxyInstance(
                        clazz.getClassLoader(),
                        new Class<?>[] {clazz},
                        (proxy, method, methodArgs) -> {
                            if (method.getDeclaringClass() == Object.class) {
                                return handleObjectMethod(proxy, method, methodArgs);
                            }
                            return invokeMethod(
                                    thiz,
                                    method.getName(),
                                    methodArgs != null ? methodArgs : new Object[0]);
                        }));
    }

    @Override
    public Bindings createBindings() {
        return new SimpleBindings();
    }

    @Override
    public ScriptEngineFactory getFactory() {
        return new JsScriptEngineFactory();
    }

    @Override
    public void close() {
        if (runner != null) {
            runner.close();
        }
    }

    private void handleOutput(ScriptContext context) {
        String stdout = runner.stdout();
        if (stdout != null && stdout.length() > lastStdoutLength) {
            String newOutput = stdout.substring(lastStdoutLength);
            lastStdoutLength = stdout.length();
            Writer writer = context.getWriter();
            if (writer != null) {
                try {
                    writer.write(newOutput);
                    writer.flush();
                } catch (IOException ignored) {
                    // output write failure is non-fatal
                }
            }
        }

        String stderr = runner.stderr();
        if (stderr != null && stderr.length() > lastStderrLength) {
            String newOutput = stderr.substring(lastStderrLength);
            lastStderrLength = stderr.length();
            Writer writer = context.getErrorWriter();
            if (writer != null) {
                try {
                    writer.write(newOutput);
                    writer.flush();
                } catch (IOException ignored) {
                    // output write failure is non-fatal
                }
            }
        }
    }

    private String readAll(Reader reader) throws ScriptException {
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
        return sb.toString();
    }

    private Map<String, Object> getBindings(ScriptContext context) {
        Bindings engineBindings = context.getBindings(ScriptContext.ENGINE_SCOPE);
        Bindings globalBindings = context.getBindings(ScriptContext.GLOBAL_SCOPE);
        Map<String, Object> bindings = new HashMap<>();
        if (globalBindings != null) {
            for (String key : globalBindings.keySet()) {
                if (engineBindings == null || !engineBindings.containsKey(key)) {
                    bindings.put(key, globalBindings.get(key));
                }
            }
        }
        if (engineBindings != null) {
            for (String key : engineBindings.keySet()) {
                bindings.put(key, engineBindings.get(key));
            }
        }
        return bindings;
    }

    private String buildCallExpression(String callable, Object... args) throws ScriptException {
        StringBuilder sb = new StringBuilder();
        sb.append(callable).append("(");
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                try {
                    sb.append(MAPPER.writeValueAsString(args[i]));
                } catch (JsonProcessingException e) {
                    throw new ScriptException("Failed to serialize argument: " + e.getMessage());
                }
            }
        }
        sb.append(")");
        return sb.toString();
    }

    private boolean isNotAFunctionError(ScriptException e) {
        String msg = e.getMessage();
        return msg != null
                && (msg.contains("is not a function")
                        || msg.contains("is not defined")
                        || msg.contains("not a function"));
    }

    private Object handleObjectMethod(Object proxy, java.lang.reflect.Method method, Object[] args)
            throws ReflectiveOperationException {
        if ("equals".equals(method.getName())) {
            return proxy == args[0];
        }
        if ("hashCode".equals(method.getName())) {
            return System.identityHashCode(proxy);
        }
        // toString and any other Object method — invoke on a plain Object
        // to avoid infinite recursion through the proxy
        return method.invoke(new Object(), args);
    }
}
