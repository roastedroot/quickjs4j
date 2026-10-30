package io.roastedroot.quickjs4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

public class EngineTest {
    private static final String LINE_SEPARATORS = new String(new char[] {0x2028, 0x2029});

    // JSON.stringify output for a string
    static String json(String str) {
        var sb = new StringBuilder("\"");
        for (char c : str.toCharArray()) {
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }

    static void compileAndExec(Engine engine, String code) {
        var codePtr = engine.compile(code);
        try {
            engine.exec(codePtr);
        } finally {
            engine.free(codePtr);
        }
    }

    static String library() throws Exception {
        return new String(
                EngineTest.class.getResourceAsStream("/library/dist/out.js").readAllBytes(),
                StandardCharsets.UTF_8);
    }

    static Builtins logBuiltins() {
        return Builtins.builder("java_api")
                .add(
                        "log",
                        args -> {
                            System.out.println("LOG - " + args.get(0));
                            return args.get(0);
                        })
                .build();
    }

    @Test
    public void basicUsage() {
        var invoked = new AtomicBoolean(false);
        var builtins =
                Builtins.builder("from_java")
                        .add(
                                "imported_function",
                                args -> {
                                    assertEquals(List.of(json("ciao")), args);
                                    invoked.set(true);
                                    return json("{ received: ciao }");
                                })
                        .build();
        try (var engine = Engine.builder().addBuiltins(builtins).build()) {
            compileAndExec(
                    engine,
                    "console.log(\"hello js world!!!\");"
                            + " console.error(from_java.imported_function(\"ciao\"));");
            assertEquals("{ received: ciao }\n", engine.stderr());
        }

        assertTrue(invoked.get());
    }

    @Test
    public void callJavaFunctionsFromDifferentModules() {
        var result = new AtomicReference<String>();
        var calculator =
                Builtins.builder("calculator")
                        .add(
                                "add",
                                args ->
                                        Integer.toString(
                                                Integer.parseInt(args.get(0))
                                                        + Integer.parseInt(args.get(1))))
                        .build();
        var check =
                Builtins.builder("from_java")
                        .add(
                                "check",
                                args -> {
                                    result.set(args.get(0));
                                    return null;
                                })
                        .build();

        try (var engine = Engine.builder().addBuiltins(calculator).addBuiltins(check).build()) {
            compileAndExec(engine, "from_java.check(calculator.add(40, 2));");
        }

        assertEquals("42", result.get());
    }

    @Test
    public void argumentsAreOneJsonTextEach() {
        var received = new AtomicReference<List<String>>();
        var builtins =
                Builtins.builder("from_java")
                        .add(
                                "f",
                                args -> {
                                    received.set(args);
                                    return null;
                                })
                        .build();

        try (var engine = Engine.builder().addBuiltins(builtins).build()) {
            compileAndExec(
                    engine,
                    "from_java.f(1, 2.5, \"str\", true, null, [1, [2]], {\"k\": {\"n\": 1}});");
            assertEquals(
                    List.of(
                            "1",
                            "2.5",
                            json("str"),
                            "true",
                            "null",
                            "[1,[2]]",
                            "{\"k\":{\"n\":1}}"),
                    received.get());

            compileAndExec(engine, "from_java.f();");
            assertEquals(List.of(), received.get());

            compileAndExec(engine, "from_java.f(undefined, () => 1, Symbol(\"s\"));");
            assertEquals(List.of("null", "null", "null"), received.get());
        }
    }

    @Test
    public void specialCharactersStayInsideArguments() {
        var received = new AtomicReference<List<String>>();
        var builtins =
                Builtins.builder("from_java")
                        .add(
                                "f",
                                args -> {
                                    received.set(args);
                                    return null;
                                })
                        .build();
        var tricky =
                List.of(
                        "a,b]c}d[e{",
                        "line\nbreak",
                        "tab\tquote\"back\\slash\\",
                        "\u001e",
                        LINE_SEPARATORS,
                        "😀");

        try (var engine = Engine.builder().addBuiltins(builtins).build()) {
            compileAndExec(
                    engine,
                    "from_java.f(\"a,b]c}d[e{\", \"line\\nbreak\","
                            + " \"tab\\tquote\\\"back\\\\slash\\\\\", \"\\x1e\","
                            + " \"\\u2028\\u2029\", \"😀\", [\"]\", {\"k\": \"}\\\"\"}]);");
        }

        var expected = new ArrayList<String>();
        for (var str : tricky) {
            expected.add(json(str));
        }
        expected.add("[" + json("]") + ",{" + json("k") + ":" + json("}\"") + "}]");
        assertEquals(expected, received.get());
    }

    @Test
    public void splitArgs() {
        assertEquals(List.of(), Engine.splitArgs("[]"));
        assertEquals(List.of(), Engine.splitArgs(" [ \n ] "));
        assertEquals(List.of("1"), Engine.splitArgs("[1]"));
        assertEquals(
                List.of("1", "\"a,]\\\"\"", "{\"b\": [2, {\"c\": \"}\"}]}", "null"),
                Engine.splitArgs("[ 1 ,\"a,]\\\"\",\n{\"b\": [2, {\"c\": \"}\"}]} , null ]"));
        // element contents are left to the codec to validate
        assertEquals(List.of("{]", "1 2", "nope"), Engine.splitArgs("[{],1 2,nope]"));

        for (var malformed :
                List.of(
                        "",
                        "1",
                        "[",
                        "]",
                        "[1",
                        "1]",
                        "[1,]",
                        "[,1]",
                        "[1,,2]",
                        "[\"open]",
                        "[1]]",
                        "[[1]",
                        "[}]")) {
            var ex =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> Engine.splitArgs(malformed),
                            malformed);
            assertTrue(ex.getMessage().startsWith("Malformed host function arguments"));
        }
    }

    @Test
    public void returnedJsonIsParsedInJs() {
        var received = new AtomicReference<List<String>>();
        var builtins =
                Builtins.builder("from_java")
                        .add("obj", args -> "{\"a\":[1,\"two\"],\"b\":null}")
                        .add("nothing", args -> null)
                        .add(
                                "check",
                                args -> {
                                    received.set(args);
                                    return null;
                                })
                        .build();

        try (var engine = Engine.builder().addBuiltins(builtins).build()) {
            compileAndExec(
                    engine,
                    "const o = from_java.obj();"
                            + " from_java.check(o.a[1], o.b, from_java.nothing());");
        }

        assertEquals(List.of(json("two"), "null", "null"), received.get());
    }

    @Test
    public void rawJavaInvokeText() {
        var received = new AtomicReference<List<String>>();
        var builtins =
                Builtins.builder("from_java")
                        .add(
                                "f",
                                args -> {
                                    received.set(args);
                                    return "1";
                                })
                        .build();

        try (var engine = Engine.builder().addBuiltins(builtins).build()) {
            compileAndExec(engine, "java_invoke(\"from_java\", \"f\", \"[ not, json ]\");");
            assertEquals(List.of("not", "json"), received.get());

            var ex =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    compileAndExec(
                                            engine,
                                            "java_invoke(\"from_java\", \"f\", \"not json\");"));
            assertTrue(ex.getMessage().contains("Malformed host function arguments"));
        }
    }

    @Test
    public void unknownHostFunction() {
        try (var engine = Engine.builder().build()) {
            var ex =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> compileAndExec(engine, "java_invoke(\"nope\", \"f\", \"[]\");"));
            assertTrue(ex.getMessage().contains("nope"));
        }
    }

    @Test
    public void largePayloadRoundTrip() {
        var received = new AtomicReference<String>();
        var builtins =
                Builtins.builder("from_java")
                        .add("echo", args -> args.get(0))
                        .add(
                                "check",
                                args -> {
                                    received.set(args.get(0));
                                    return null;
                                })
                        .build();

        try (var engine = Engine.builder().addBuiltins(builtins).build()) {
            compileAndExec(
                    engine,
                    "const big = \"x,\\\"]\".repeat(500000);"
                            + " from_java.check(from_java.echo(big) === big);");
        }

        assertEquals("true", received.get());
    }

    @Test
    public void invalidNamesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Builtins.builder("bad-module"));
        assertThrows(IllegalArgumentException.class, () -> Invokables.builder("1module"));
        assertThrows(IllegalArgumentException.class, () -> new HostFunction("a\"b", args -> null));
        assertThrows(IllegalArgumentException.class, () -> new GuestFunction("f", "a b"));
        assertThrows(IllegalArgumentException.class, () -> new GuestFunction(""));
    }

    @Test
    public void duplicateNamesAreRejected() {
        var builtins = Builtins.builder("from_java").add("f", args -> null);
        assertThrows(IllegalArgumentException.class, () -> builtins.add("f", args -> null));

        var invokables = Invokables.builder("from_js").add("f");
        assertThrows(IllegalArgumentException.class, () -> invokables.add("f"));
    }

    @Test
    public void callJavaFunctionsFromJSNegativeCheck() {
        var builtins =
                Builtins.builder("from_java")
                        .add(
                                "check",
                                args -> {
                                    assertEquals("43", args.get(0));
                                    return null;
                                })
                        .build();

        try (var engine = Engine.builder().addBuiltins(builtins).build()) {
            assertThrows(
                    AssertionFailedError.class,
                    () -> compileAndExec(engine, "from_java.check(42);"));
        }
    }

    @Test
    public void useBundledJS() throws Exception {
        var myCow =
                " ______________\n"
                        + "< my Moooodule >\n"
                        + " --------------\n"
                        + "        \\   ^__^\n"
                        + "         \\  (oo)\\_______\n"
                        + "            (__)\\       )\\/\\\n"
                        + "                ||----w |\n"
                        + "                ||     ||";
        var checked = new AtomicBoolean(false);
        var builtins =
                Builtins.builder("from_java")
                        .add("java_text", args -> json("my Moooodule"))
                        .add(
                                "java_check",
                                args -> {
                                    assertEquals(json(myCow), args.get(0));
                                    checked.set(true);
                                    return null;
                                })
                        .build();
        var jsSource =
                new String(
                        EngineTest.class.getResourceAsStream("/cowsay/dist/out.js").readAllBytes(),
                        StandardCharsets.UTF_8);

        try (var engine = Engine.builder().addBuiltins(builtins).build()) {
            compileAndExec(engine, jsSource);
        }

        assertTrue(checked.get());
    }

    @Test
    public void useRawBundledLibrary() throws Exception {
        var result = new AtomicReference<String>();
        var builtins =
                Builtins.builder("java_api")
                        .add(
                                "setResult",
                                args -> {
                                    result.set(args.get(0));
                                    return null;
                                })
                        .add("log", args -> args.get(0))
                        .build();

        try (var engine = Engine.builder().addBuiltins(builtins).build()) {
            compileAndExec(
                    engine,
                    library()
                            + "\nglobalThis.jsFunctions = {};"
                            + "\nglobalThis.jsFunctions.calculator = { add };");
            compileAndExec(engine, "java_api.setResult(jsFunctions.calculator.add(1, 2));");
        }

        assertEquals("3", result.get());
    }

    @Test
    public void invokeGuestFunctions() throws Exception {
        var invokables = Invokables.builder("js_api").add("add").add("append").build();

        try (var engine =
                Engine.builder().addBuiltins(logBuiltins()).addInvokables(invokables).build()) {
            assertEquals(
                    "42",
                    engine.invokeGuestFunction("js_api", "add", List.of("39", "3"), library()));
            assertEquals(
                    "[1,2,3,4]",
                    engine.invokeGuestFunction(
                            "js_api", "append", List.of("[1,2,3]", "4"), library()));
            assertEquals(
                    "\"ab\"",
                    engine.invokeGuestFunction(
                            "js_api", "add", List.of(json("a"), json("b")), library()));
        }
    }

    @Test
    public void guestFunctionResults() {
        var invokables =
                Invokables.builder("from_js")
                        .add("nothing")
                        .add("identity")
                        .add(new GuestFunction("aliased", "renamed"))
                        .build();
        var code =
                "function nothing() {}\n"
                        + "function identity(x) { return x; }\n"
                        + "function renamed() { return { \"fn\": \"renamed\" }; }\n";

        try (var engine = Engine.builder().addInvokables(invokables).build()) {
            assertEquals("null", engine.invokeGuestFunction("from_js", "nothing", List.of(), code));
            var nullArg = new ArrayList<String>();
            nullArg.add(null);
            assertEquals("null", engine.invokeGuestFunction("from_js", "identity", nullArg, code));
            assertEquals(
                    "{\"a\":\"\\u001e\"}",
                    engine.invokeGuestFunction(
                            "from_js", "identity", List.of("{\"a\":\"\\u001e\"}"), code));
            assertEquals(
                    "{\"fn\":\"renamed\"}",
                    engine.invokeGuestFunction("from_js", "aliased", List.of(), code));
        }
    }

    @Test
    public void unknownGuestFunction() {
        var invokables = Invokables.builder("from_js").add("f").build();

        try (var engine = Engine.builder().addInvokables(invokables).build()) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> engine.invokeGuestFunction("from_js", "g", List.of(), "function g() {}"));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> engine.invokeGuestFunction("nope", "f", List.of(), "function f() {}"));
        }
    }

    @Test
    public void asyncGuestFunction() {
        var invokables = Invokables.builder("from_js").add("twice").build();
        var code = "async function twice(x) { return await Promise.resolve(x * 2); }";

        try (var engine = Engine.builder().addInvokables(invokables).build()) {
            assertEquals("42", engine.invokeGuestFunction("from_js", "twice", List.of("21"), code));
        }
    }

    @Test
    public void guestFunctionCanReplaceEngineGlobals() {
        var invokables = Invokables.builder("from_js").add("hide").build();
        var code =
                "function hide() {\n"
                        + "  globalThis.java_invoke = undefined;\n"
                        + "  globalThis.quickjs4j_engine = undefined;\n"
                        + "  globalThis.from_js = undefined;\n"
                        + "  return 42;\n"
                        + "}\n";

        for (var jsGlobals : new boolean[] {true, false}) {
            try (var engine =
                    Engine.builder().addInvokables(invokables).withJsGlobals(jsGlobals).build()) {
                assertEquals("42", engine.invokeGuestFunction("from_js", "hide", List.of(), code));
            }
        }
    }

    @Test
    public void guestFunctionsWithoutJsGlobals() {
        var invokables = Invokables.builder("from_js").add("add").add("twice").build();
        var code =
                "function add(a, b) { return a + b; }\n"
                        + "async function twice(x) { return await Promise.resolve(x * 2); }\n";

        try (var engine = Engine.builder().addInvokables(invokables).withJsGlobals(false).build()) {
            assertEquals(
                    "5", engine.invokeGuestFunction("from_js", "add", List.of("2", "3"), code));
            assertEquals("42", engine.invokeGuestFunction("from_js", "twice", List.of("21"), code));
            var compiled = engine.compilePortableGuestFunction(code);
            assertEquals(
                    "7",
                    engine.invokePrecompiledGuestFunction(
                            "from_js", "add", List.of("3", "4"), compiled));
        }
    }

    @Test
    public void withoutJsGlobalsNothingIsGenerated() {
        var builtins = Builtins.builder("from_java").add("shout", args -> args.get(0)).build();
        var invokables = Invokables.builder("from_js").add("probe").build();
        var code =
                "function probe() {\n"
                        + "  return [typeof quickjs4j_engine, typeof globalThis.from_js,"
                        + " typeof globalThis.from_java];\n"
                        + "}\n";

        try (var engine =
                Engine.builder().addBuiltins(builtins).addInvokables(invokables).build()) {
            assertEquals(
                    "[\"object\",\"object\",\"object\"]",
                    engine.invokeGuestFunction("from_js", "probe", List.of(), code));
        }
        try (var engine =
                Engine.builder()
                        .addBuiltins(builtins)
                        .addInvokables(invokables)
                        .withJsGlobals(false)
                        .build()) {
            assertEquals(0, engine.jsPreludeLineCount());
            assertEquals(
                    "[\"undefined\",\"undefined\",\"undefined\"]",
                    engine.invokeGuestFunction("from_js", "probe", List.of(), code));
        }
    }

    @Test
    public void withoutJsGlobalsBuiltinsAreReachedThroughJavaInvoke() {
        var builtins = Builtins.builder("from_java").add("shout", args -> args.get(0)).build();

        try (var engine = Engine.builder().addBuiltins(builtins).withJsGlobals(false).build()) {
            compileAndExec(
                    engine,
                    "console.log(JSON.parse(java_invoke(\"from_java\", \"shout\","
                            + " JSON.stringify([\"x\"]))));");
            assertEquals("x\n", engine.stdout());
            assertThrows(
                    GuestException.class, () -> compileAndExec(engine, "from_java.shout(\"x\");"));
        }
    }

    @Test
    public void enableCachingOfCompiledJS() {
        var engine = Engine.builder().build();
        var codePtr = engine.compile("console.log(\"hello world!\")");
        var jsBytecode = engine.readCompiled(codePtr);
        engine.free(codePtr);
        engine.close();

        try (var runtimeEngine = Engine.builder().build()) {
            var runtimeCodePtr = runtimeEngine.writeCompiled(jsBytecode);
            runtimeEngine.exec(runtimeCodePtr);
            runtimeEngine.free(runtimeCodePtr);
            assertEquals("hello world!\n", runtimeEngine.stdout());
        }
    }

    @Test
    public void precompiledGuestFunction() throws Exception {
        var invokables = Invokables.builder("js_api").add("add").build();

        byte[] compiled;
        try (var buildingEngine =
                Engine.builder().addBuiltins(logBuiltins()).addInvokables(invokables).build()) {
            compiled = buildingEngine.compilePortableGuestFunction(library());
        }

        try (var runningEngine =
                Engine.builder().addBuiltins(logBuiltins()).addInvokables(invokables).build()) {
            assertEquals(
                    "43",
                    runningEngine.invokePrecompiledGuestFunction(
                            "js_api", "add", List.of("39", "4"), compiled));
            assertEquals(
                    "3",
                    runningEngine.invokePrecompiledGuestFunction(
                            "js_api", "add", List.of("1", "2"), compiled));
        }
    }

    @Test
    public void handleExceptionsThrownInJava() {
        var builtins =
                Builtins.builder("from_java")
                        .add(
                                "imported_function",
                                args -> {
                                    throw new IndexOutOfBoundsException("whatever");
                                })
                        .build();

        try (var engine = Engine.builder().addBuiltins(builtins).build()) {
            var exception =
                    assertThrows(
                            IndexOutOfBoundsException.class,
                            () -> compileAndExec(engine, "from_java.imported_function(\"ciao\");"));
            assertEquals("whatever", exception.getMessage());
        }
    }

    @Test
    public void handleExceptionsThrownInJs() {
        try (var engine = Engine.builder().build()) {
            var exception =
                    assertThrows(
                            GuestException.class,
                            () -> compileAndExec(engine, "throw \"hello exception\";"));
            assertTrue(exception.getMessage().contains("hello exception"));
        }
    }

    @Test
    public void handleExceptionsThrownInGuestFunctions() {
        var invokables = Invokables.builder("from_js").add("fail").build();

        try (var engine = Engine.builder().addInvokables(invokables).build()) {
            var exception =
                    assertThrows(
                            GuestException.class,
                            () ->
                                    engine.invokeGuestFunction(
                                            "from_js",
                                            "fail",
                                            List.of(),
                                            "function fail() { throw new Error(\"boom\"); }"));
            assertTrue(exception.getMessage().contains("boom"));
        }
    }

    @Test
    public void failToCompileJs() {
        try (var engine = Engine.builder().build()) {
            var exception =
                    assertThrows(
                            IllegalArgumentException.class, () -> engine.compile("1' \" 2 ..."));
            assertTrue(exception.getMessage().contains("Failed to compile JS code"));
        }
    }

    @Test
    public void supportAsyncAwait() {
        try (var engine = Engine.builder().build()) {
            compileAndExec(
                    engine,
                    "function debug() {\n"
                            + "  return Promise.resolve(\"foo\");\n"
                            + "}\n"
                            + "console.log(await debug());");
            assertEquals("foo\n", engine.stdout());
        }
    }

    @Test
    public void engineArgsOutsideOfInvocation() {
        var received = new AtomicReference<List<String>>();
        var builtins =
                Builtins.builder("from_java")
                        .add(
                                "check",
                                args -> {
                                    received.set(args);
                                    return null;
                                })
                        .build();

        try (var engine = Engine.builder().addBuiltins(builtins).build()) {
            compileAndExec(
                    engine,
                    "from_java.check(quickjs4j_engine.module_name(), quickjs4j_engine.args());");
        }

        assertEquals(List.of("null", "null"), received.get());
    }

    @Test
    public void gcThresholdRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> Engine.builder().withGcThreshold(-1));
    }

    @Test
    public void jsPreludeLineCountGrowsWithEveryRegisteredBuiltin() {
        var builtins = Builtins.builder("from_java").add("shout", args -> args.get(0)).build();

        try (var engine = Engine.builder().addBuiltins(builtins).build()) {
            assertEquals(6, engine.jsPreludeLineCount());
        }
    }
}
