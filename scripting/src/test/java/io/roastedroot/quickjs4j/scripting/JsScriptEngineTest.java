package io.roastedroot.quickjs4j.scripting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import javax.script.Compilable;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;
import org.junit.jupiter.api.Test;

public class JsScriptEngineTest {

    // -- Expression eval (no return keyword needed) --

    @Test
    public void testEvalNumber() throws Exception {
        try (var engine = new JsScriptEngine()) {
            var result = engine.eval("1 + 2");
            assertEquals(3, result);
        }
    }

    @Test
    public void testEvalString() throws Exception {
        try (var engine = new JsScriptEngine()) {
            var result = engine.eval("'hello'");
            assertEquals("hello", result);
        }
    }

    @Test
    public void testEvalBoolean() throws Exception {
        try (var engine = new JsScriptEngine()) {
            assertEquals(true, engine.eval("true"));
            assertEquals(false, engine.eval("false"));
        }
    }

    @Test
    public void testEvalNull() throws Exception {
        try (var engine = new JsScriptEngine()) {
            assertNull(engine.eval("null"));
        }
    }

    @Test
    public void testEvalUndefined() throws Exception {
        try (var engine = new JsScriptEngine()) {
            assertNull(engine.eval("undefined"));
        }
    }

    @Test
    public void testEvalDeclarationReturnsNull() throws Exception {
        try (var engine = new JsScriptEngine()) {
            assertNull(engine.eval("var x = 3"));
        }
    }

    // -- State persistence between evals --

    @Test
    public void testVarPersistsBetweenEvals() throws Exception {
        try (var engine = new JsScriptEngine()) {
            engine.eval("var x = 42");
            assertEquals(42, engine.eval("x"));
        }
    }

    @Test
    public void testFunctionPersistsBetweenEvals() throws Exception {
        try (var engine = new JsScriptEngine()) {
            engine.eval("function add(a, b) { return a + b; }");
            assertEquals(5, engine.eval("add(2, 3)"));
        }
    }

    // -- Typed bindings --

    @Test
    public void testBindingNumber() throws Exception {
        try (var engine = new JsScriptEngine()) {
            engine.put("x", 42);
            assertEquals(42, engine.eval("x"));
        }
    }

    @Test
    public void testBindingDouble() throws Exception {
        try (var engine = new JsScriptEngine()) {
            engine.put("pi", 3.14);
            assertEquals(3.14, engine.eval("pi"));
        }
    }

    @Test
    public void testBindingBoolean() throws Exception {
        try (var engine = new JsScriptEngine()) {
            engine.put("flag", true);
            assertEquals(true, engine.eval("flag"));
        }
    }

    @Test
    public void testBindingString() throws Exception {
        try (var engine = new JsScriptEngine()) {
            engine.put("name", "world");
            assertEquals("hello world", engine.eval("'hello ' + name"));
        }
    }

    @Test
    public void testBindingNull() throws Exception {
        try (var engine = new JsScriptEngine()) {
            engine.put("val", null);
            assertNull(engine.eval("val"));
        }
    }

    @Test
    public void testBindingMultilineString() throws Exception {
        try (var engine = new JsScriptEngine()) {
            engine.put("source", "line1\nline2\n");
            assertEquals("line1\nline2\n", engine.eval("source"));
        }
    }

    @Test
    public void testBindingNumberIsNotString() throws Exception {
        try (var engine = new JsScriptEngine()) {
            engine.put("x", 10);
            assertEquals(20, engine.eval("x + 10"));
        }
    }

    // -- Compilable --

    @Test
    public void testCompileAndEval() throws Exception {
        try (var engine = new JsScriptEngine()) {
            var compiled = ((Compilable) engine).compile("1 + 2");
            assertEquals(3, compiled.eval());
        }
    }

    @Test
    public void testCompileReEvalWithDifferentBindings() throws Exception {
        try (var engine = new JsScriptEngine()) {
            var compiled = ((Compilable) engine).compile("x * 2");
            engine.put("x", 5);
            assertEquals(10, compiled.eval());
            engine.put("x", 7);
            assertEquals(14, compiled.eval());
        }
    }

    // -- Invocable: invokeFunction --

    @Test
    public void testInvokeFunction() throws Exception {
        try (var engine = new JsScriptEngine()) {
            engine.eval("function greet(name) { return 'Hello ' + name; }");
            var result = ((Invocable) engine).invokeFunction("greet", "World");
            assertEquals("Hello World", result);
        }
    }

    @Test
    public void testInvokeFunctionWithMultipleArgs() throws Exception {
        try (var engine = new JsScriptEngine()) {
            engine.eval("function add(a, b) { return a + b; }");
            var result = ((Invocable) engine).invokeFunction("add", 3, 4);
            assertEquals(7, result);
        }
    }

    @Test
    public void testInvokeFunctionNoSuchMethod() throws Exception {
        try (var engine = new JsScriptEngine()) {
            assertThrows(
                    NoSuchMethodException.class,
                    () -> ((Invocable) engine).invokeFunction("nonExistent"));
        }
    }

    // -- Invocable: invokeMethod --

    @Test
    public void testInvokeMethod() throws Exception {
        try (var engine = new JsScriptEngine()) {
            engine.eval("var calc = { add: function(a, b) { return a + b; } }");
            var result = ((Invocable) engine).invokeMethod("calc", "add", 2, 3);
            assertEquals(5, result);
        }
    }

    @Test
    public void testInvokeMethodNoSuchMethod() throws Exception {
        try (var engine = new JsScriptEngine()) {
            engine.eval("var obj = {}");
            assertThrows(
                    NoSuchMethodException.class,
                    () -> ((Invocable) engine).invokeMethod("obj", "missing"));
        }
    }

    @Test
    public void testInvokeMethodNullThiz() throws Exception {
        try (var engine = new JsScriptEngine()) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ((Invocable) engine).invokeMethod(null, "foo"));
        }
    }

    @Test
    public void testInvokeMethodNonStringThiz() throws Exception {
        try (var engine = new JsScriptEngine()) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ((Invocable) engine).invokeMethod(42, "foo"));
        }
    }

    // -- getInterface --

    public interface Calculator {
        Object add(int a, int b);

        Object multiply(int a, int b);
    }

    @Test
    public void testGetInterface() throws Exception {
        try (var engine = new JsScriptEngine()) {
            engine.eval("function add(a, b) { return a + b; }");
            engine.eval("function multiply(a, b) { return a * b; }");
            Calculator calc = ((Invocable) engine).getInterface(Calculator.class);
            assertEquals(5, calc.add(2, 3));
            assertEquals(6, calc.multiply(2, 3));
        }
    }

    @Test
    public void testGetInterfaceWithThiz() throws Exception {
        try (var engine = new JsScriptEngine()) {
            engine.eval(
                    "var math = {"
                            + " add: function(a, b) { return a + b; },"
                            + " multiply: function(a, b) { return a * b; }"
                            + "}");
            Calculator calc = ((Invocable) engine).getInterface("math", Calculator.class);
            assertEquals(7, calc.add(3, 4));
            assertEquals(12, calc.multiply(3, 4));
        }
    }

    // -- AutoCloseable --

    @Test
    public void testTryWithResources() throws Exception {
        Object result;
        try (var engine = new JsScriptEngine()) {
            result = engine.eval("1 + 1");
        }
        assertEquals(2, result);
    }

    // -- ScriptContext output redirection --

    @Test
    public void testOutputRedirection() throws Exception {
        try (var engine = new JsScriptEngine()) {
            ScriptContext ctx = new SimpleScriptContext();
            StringWriter out = new StringWriter();
            ctx.setWriter(out);
            ctx.setBindings(engine.createBindings(), ScriptContext.ENGINE_SCOPE);
            engine.eval("console.log('hello from js')", ctx);
            assertTrue(out.toString().contains("hello from js"));
        }
    }

    @Test
    public void testStderrRedirection() throws Exception {
        try (var engine = new JsScriptEngine()) {
            ScriptContext ctx = new SimpleScriptContext();
            StringWriter err = new StringWriter();
            ctx.setErrorWriter(err);
            ctx.setBindings(engine.createBindings(), ScriptContext.ENGINE_SCOPE);
            engine.eval("console.error('error from js')", ctx);
            assertTrue(err.toString().contains("error from js"));
        }
    }

    // -- Error handling --

    @Test
    public void testSyntaxError() throws Exception {
        try (var engine = new JsScriptEngine()) {
            assertThrows(ScriptException.class, () -> engine.eval("function {{{"));
        }
    }

    @Test
    public void testRuntimeError() throws Exception {
        try (var engine = new JsScriptEngine()) {
            assertThrows(ScriptException.class, () -> engine.eval("undeclaredVar"));
        }
    }

    // -- SPI discovery --

    @Test
    public void testSpiByName() throws Exception {
        var mgr = new ScriptEngineManager();
        ScriptEngine engine = mgr.getEngineByName("quickjs4j");
        assertNotNull(engine);
        assertInstanceOf(JsScriptEngine.class, engine);
    }

    @Test
    public void testSpiByExtension() throws Exception {
        var mgr = new ScriptEngineManager();
        ScriptEngine engine = mgr.getEngineByExtension("js");
        assertNotNull(engine);
        assertInstanceOf(JsScriptEngine.class, engine);
    }

    @Test
    public void testSpiByMimeType() throws Exception {
        var mgr = new ScriptEngineManager();
        ScriptEngine engine = mgr.getEngineByMimeType("application/x-javascript");
        assertNotNull(engine);
        assertInstanceOf(JsScriptEngine.class, engine);
    }

    // -- Factory method call syntax --

    @Test
    public void testMethodCallSyntax() throws Exception {
        var factory = new JsScriptEngineFactory();
        String syntax = factory.getMethodCallSyntax("obj", "method", "a", "b");
        assertEquals("obj.method(a, b)", syntax);
    }
}
