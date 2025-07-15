package io.roastedroot.quickjs4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

public class EngineTest {

    @Test
    public void basicUsage() {
        // Arrange
        var invoked = new AtomicBoolean(false);
        var builtins =
                Builtins.builder("from_java")
                        .addStringToString(
                                "imported_function",
                                (str) -> {
                                    assertEquals("ciao", str);
                                    invoked.set(true);
                                    return "{ received: " + str + " }";
                                })
                        .build();
        var engine = Engine.builder().addBuiltins(builtins).build();

        // Act
        var codePtr =
                engine.compile(
                        "console.log(\"hello js world!!!\");"
                                + " console.error(from_java.imported_function(\"ciao\"));");
        engine.exec(codePtr);
        engine.free(codePtr);
        engine.close();

        // Assert
        assertTrue(invoked.get());
    }

    public static int add(int a, int b) {
        return a + b;
    }

    public static Consumer<Integer> check(int expected) {
        return (v) -> assertEquals(expected, v);
    }

    @Test
    public void callJavaFunctionsFromJS() {
        var builtins = Builtins.builder("java").addIntIntToInt("add", EngineTest::add).build();

        var engine = Engine.builder().build().builder().addBuiltins(builtins).build();

        var codePtr = engine.compile("java.add(40, 1);");
        engine.exec(codePtr);
        engine.free(codePtr);
        engine.close();
    }

    @Test
    public void callJavaFunctionsFromDifferentBundlesFromJS() {
        var calculatorBuiltins =
                Builtins.builder("calculator").addIntIntToInt("add", EngineTest::add).build();
        var checkBuiltins =
                Builtins.builder("from_java").addIntToVoid("check", EngineTest.check(42)).build();

        var engine =
                Engine.builder().addBuiltins(calculatorBuiltins).addBuiltins(checkBuiltins).build();

        var codePtr = engine.compile("from_java.check(calculator.add(40, 2));");
        engine.exec(codePtr);
        engine.free(codePtr);
        engine.close();
    }

    @Test
    public void callJavaFunctionsFromJSNegativeCheck() {
        var builtins =
                Builtins.builder("from_java")
                        .addIntIntToInt("add", EngineTest::add)
                        .addIntToVoid("check", EngineTest.check(43))
                        .build();

        var engine = Engine.builder().addBuiltins(builtins).build();

        var codePtr = engine.compile("from_java.check(from_java.add(40, 2));");

        assertThrows(AssertionError.class, () -> engine.exec(codePtr));
        engine.free(codePtr);
        engine.close();
    }

    boolean func1Called;

    void func1() {
        func1Called = true;
    }

    int func2Called;

    void func2(int a) {
        func2Called = a;
    }

    String func3Called;

    void func3(String a) {
        func3Called = a;
    }

    String func4Called;

    String func4() {
        func4Called = "func4";
        return func4Called;
    }

    int func5Called;

    String func5(int a) {
        func5Called = a;
        return "funcS" + a;
    }

    String func6Called;

    String func6(String a) {
        func6Called = a;
        return "funcS" + a;
    }

    static void compileAndExec(Engine jsRunner, String code) {
        var codePtr = jsRunner.compile(code);
        jsRunner.exec(codePtr);
        jsRunner.free(codePtr);
        jsRunner.close();
    }

    @Test
    public void callJavaFunctionsFromJSWithDifferentParamsAndReturns() {
        final AtomicReference<String> toCheck = new AtomicReference<>();
        var builtins =
                Builtins.builder("from_java")
                        .addVoidToVoid("func1", this::func1)
                        .addIntToVoid("func2", this::func2)
                        .addStringToVoid("func3", this::func3)
                        .addVoidToString("func4", this::func4)
                        .addIntToString("func5", this::func5)
                        .addStringToString("func6", this::func6)
                        .addStringToVoid("check", str -> assertEquals(toCheck.get(), str))
                        .build();

        var engine = Engine.builder().addBuiltins(builtins).build();

        compileAndExec(engine, "from_java.func1();");
        assertTrue(func1Called);

        compileAndExec(engine, "from_java.func2(10);");
        assertEquals(10, func2Called);

        compileAndExec(engine, "from_java.func3(\"h3110\");");
        assertEquals("h3110", func3Called);

        toCheck.set("func4");
        compileAndExec(engine, "from_java.check(from_java.func4());");
        assertEquals("func4", func4Called);

        compileAndExec(engine, "from_java.func5(11);");
        assertEquals(11, func5Called);

        // negative - needs to be last as the runtime needs a restart after exception
        toCheck.set("myFunc");
        assertThrows(
                AssertionFailedError.class,
                () -> compileAndExec(engine, "from_java.check(from_java.func4());"));
    }

    @Test
    public void callJavaFunctionsWithMixedParameters() {
        var expectedX = 123;
        var expectedY = "hello my world";
        var expectedZ = 321;
        var builtins =
                Builtins.builder("from_java")
                        .add(
                                new HostFunction(
                                        "myFunc",
                                        List.of(Integer.class, String.class, Integer.class),
                                        Void.class,
                                        (args) -> {
                                            var x = (Integer) args.get(0);
                                            var y = (String) args.get(1);
                                            var z = (Integer) args.get(2);

                                            assertEquals(expectedX, x);
                                            assertEquals(expectedY, y);
                                            assertEquals(expectedZ, z);
                                            return null;
                                        }))
                        .build();

        var engine = Engine.builder().addBuiltins(builtins).build();

        compileAndExec(
                engine,
                String.format(
                        "from_java.myFunc(%d, \"%s\", %d);", expectedX, expectedY, expectedZ));
    }

    private static class User {
        final String name;
        final String surname;
        final int age;

        public User(String name, String surname, int age) {
            this.name = name;
            this.surname = surname;
            this.age = age;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof User)) {
                return false;
            }
            User user = (User) o;
            return age == user.age
                    && Objects.equals(name, user.name)
                    && Objects.equals(surname, user.surname);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, surname, age);
        }
    }

    @Test
    public void callJavaFunctionsUsingJavaRefs() {
        var expectedUser = new User("alice", "bobstrom", 23);
        var builtins =
                Builtins.builder("from_java")
                        .add(
                                new HostFunction(
                                        "getUser",
                                        List.of(String.class, String.class, Integer.class),
                                        HostRef.class,
                                        (args) -> {
                                            var name = (String) args.get(0);
                                            var surname = (String) args.get(1);
                                            var age = (Integer) args.get(2);

                                            return new User(name, surname, age);
                                        }))
                        .add(
                                new HostFunction(
                                        "checkUser",
                                        List.of(HostRef.class),
                                        Void.class,
                                        (args) -> {
                                            var user = (User) args.get(0);

                                            assertEquals(expectedUser, user);
                                            return null;
                                        }))
                        .build();

        var engine = Engine.builder().addBuiltins(builtins).build();

        compileAndExec(
                engine,
                String.format(
                        "const user = from_java.getUser(\"%s\", \"%s\", %d);\n"
                                + "from_java.checkUser(user);",
                        expectedUser.name, expectedUser.surname, expectedUser.age));
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
        var builtins =
                Builtins.builder("from_java")
                        .addVoidToString("java_text", () -> "my Moooodule")
                        .addStringToVoid("java_check", (str) -> assertEquals(myCow, str))
                        .build();
        var engine = Engine.builder().addBuiltins(builtins).build();

        var jsSource =
                new String(
                        EngineTest.class.getResourceAsStream("/cowsay/dist/out.js").readAllBytes(),
                        StandardCharsets.UTF_8);

        compileAndExec(engine, jsSource);
    }

    public static class ZodResult {
        @JsonProperty("success")
        boolean success;

        @JsonProperty("data")
        String data;

        @JsonProperty("error")
        ZodError error;
    }

    public static class ZodError {
        @JsonProperty("name")
        String name;

        @JsonProperty("issues")
        ZodIssue[] issues;
    }

    public static class ZodIssue {
        @JsonProperty("code")
        String code;

        @JsonProperty("path")
        String[] path;

        @JsonProperty("expected")
        String expected;

        @JsonProperty("received")
        String received;

        @JsonProperty("message")
        String message;
    }

    @Test
    public void useBundledTS() throws Exception {
        var builtins =
                Builtins.builder("from_java")
                        .add(
                                new HostFunction(
                                        "java_check_tuna",
                                        List.of(ZodResult.class),
                                        Void.class,
                                        (args) -> {
                                            ZodResult res = (ZodResult) args.get(0);

                                            assertTrue(res.success);
                                            assertEquals("tuna", res.data);

                                            return null;
                                        }))
                        .add(
                                new HostFunction(
                                        "java_check_number",
                                        List.of(ZodResult.class),
                                        Void.class,
                                        (args) -> {
                                            ZodResult res = (ZodResult) args.get(0);

                                            assertFalse(res.success);
                                            assertEquals("invalid_type", res.error.issues[0].code);
                                            assertEquals("number", res.error.issues[0].received);
                                            assertEquals("string", res.error.issues[0].expected);

                                            return null;
                                        }))
                        .build();
        var engine = Engine.builder().addBuiltins(builtins).build();

        var jsSource = EngineTest.class.getResourceAsStream("/zod/dist/out.js").readAllBytes();

        var codePtr = engine.compile(jsSource);
        engine.exec(codePtr);
        engine.free(codePtr);
        engine.close();
    }

    @Test
    public void useRawBundledLibrary() throws Exception {
        AtomicInteger result = new AtomicInteger();

        var builtins =
                Builtins.builder("java_api")
                        .add(
                                new HostFunction(
                                        "setResult",
                                        List.of(Integer.class),
                                        Void.class,
                                        (args) -> {
                                            result.set((Integer) args.get(0));
                                            return null;
                                        }),
                                new HostFunction(
                                        "log",
                                        List.of(String.class),
                                        String.class,
                                        (args) -> {
                                            String str = (String) args.get(0);

                                            System.out.println("LOG - " + str);
                                            return str;
                                        }))
                        .build();
        var engine = Engine.builder().addBuiltins(builtins).build();

        var jsLibrarySource =
                EngineTest.class.getResourceAsStream("/library/dist/out.js").readAllBytes();

        // expected usage
        // compile and load the "library" code
        var libCodePtr =
                engine.compile(
                        new String(jsLibrarySource, StandardCharsets.UTF_8)
                                // this code needs to be generated based on @GuestFunction generated
                                // code
                                + "\nglobalThis.jsFunctions = {};"
                                + "\nglobalThis.jsFunctions."
                                + "calculator"
                                + " = { add };");
        engine.exec(libCodePtr);
        engine.free(libCodePtr);

        // this is the code that will be generated by @GuestFunction
        var userCodePtr = engine.compile("java_api.setResult(jsFunctions.calculator.add(1, 2));");
        engine.exec(userCodePtr);
        engine.free(userCodePtr);

        engine.close();

        assertEquals(3, result.get());
    }

    @Test
    public void useInvokablesBundledLibrary() throws Exception {
        var builtins =
                Builtins.builder("java_api")
                        .add(
                                new HostFunction(
                                        "log",
                                        List.of(String.class),
                                        String.class,
                                        (args) -> {
                                            String str = (String) args.get(0);

                                            System.out.println("LOG - " + str);
                                            return str;
                                        }))
                        .build();
        var invokables =
                Invokables.builder("js_api")
                        .add(
                                new GuestFunction(
                                        "add",
                                        List.of(Integer.class, Integer.class),
                                        Integer.class))
                        .build();

        var engine = Engine.builder().addBuiltins(builtins).addInvokables(invokables).build();

        var jsLibrarySource =
                EngineTest.class.getResourceAsStream("/library/dist/out.js").readAllBytes();

        // this is the code that will be generated by @GuestFunction
        var result =
                (Integer)
                        engine.invokeGuestFunction(
                                "js_api",
                                "add",
                                List.of(39, 3),
                                new String(jsLibrarySource, StandardCharsets.UTF_8));

        engine.close();

        assertEquals(42, result);
    }

    @Test
    public void enableCachingOfCompiledJS() throws Exception {
        // Build QuickJs instance
        var engine = Engine.builder().build();

        var jsSource = "console.log(\"hello world!\")";
        var codePtr = engine.compile(jsSource);

        var jsBytecode = engine.readCompiled(codePtr);
        engine.free(codePtr);
        engine.close();

        // Runtime QuickJs instance
        var runtimeEngine = Engine.builder().build();
        var runtimeCodePtr = runtimeEngine.writeCompiled(jsBytecode);
        runtimeEngine.exec(runtimeCodePtr);
        runtimeEngine.free(runtimeCodePtr);
        runtimeEngine.close();
    }

    @Test
    public void useInvokablesBundledLibraryWithCache() throws Exception {
        var builtins =
                Builtins.builder("java_api")
                        .add(
                                new HostFunction(
                                        "log",
                                        List.of(String.class),
                                        String.class,
                                        (args) -> {
                                            String str = (String) args.get(0);

                                            System.out.println("LOG - " + str);
                                            return str;
                                        }))
                        .build();
        var invokables =
                Invokables.builder("js_api")
                        .add(
                                new GuestFunction(
                                        "add",
                                        List.of(Integer.class, Integer.class),
                                        Integer.class))
                        .build();

        var buildingEngine =
                Engine.builder().addBuiltins(builtins).addInvokables(invokables).build();

        var jsLibrarySource =
                EngineTest.class.getResourceAsStream("/library/dist/out.js").readAllBytes();

        // this is the code that will be generated by @GuestFunction
        var jsCompiledCode =
                buildingEngine.compilePortableGuestFunction(
                        new String(jsLibrarySource, StandardCharsets.UTF_8));
        // the separation is good, how things match together is not
        buildingEngine.close();

        // Now running on a separate engine without performing any compilation
        var runningEngine =
                Engine.builder().addBuiltins(builtins).addInvokables(invokables).build();
        var result =
                (Integer)
                        runningEngine.invokePrecompiledGuestFunction(
                                "js_api", "add", List.of(39, 4), jsCompiledCode);

        runningEngine.close();

        assertEquals(43, result);
    }

    @Test
    public void apicurioBeforeVsAfter() throws Exception {
        var builtins =
                Builtins.builder("java_api")
                        .add(
                                new HostFunction(
                                        "info",
                                        List.of(String.class),
                                        Void.class,
                                        (args) -> {
                                            String str = (String) args.get(0);

                                            System.out.println("LOG - info - " + str);
                                            return str;
                                        }),
                                new HostFunction(
                                        "debug",
                                        List.of(String.class),
                                        Void.class,
                                        (args) -> {
                                            String str = (String) args.get(0);

                                            System.out.println("LOG - debug - " + str);
                                            return str;
                                        }))
                        .build();
        var invokables =
                Invokables.builder("ArtifactTypeScriptProvider_Invokables")
                        .add(
                                new GuestFunction(
                                        "testCompatibility", List.of(String.class), JsonNode.class))
                        .build();

        var engine = Engine.builder().addBuiltins(builtins).addInvokables(invokables).build();

        var jsLibrarySource =
                EngineTest.class
                        .getResourceAsStream("/apicurio-numbers/js-artifact-types-test.js")
                        .readAllBytes();

        var before1 = System.currentTimeMillis();
        var result1 =
                (JsonNode)
                        engine.invokeGuestFunction(
                                "ArtifactTypeScriptProvider_Invokables",
                                "testCompatibility",
                                List.of("zoobar"),
                                new String(jsLibrarySource));
        var after1 = System.currentTimeMillis();
        var before2 = System.currentTimeMillis();
        var result2 =
                (JsonNode)
                        engine.invokeGuestFunction(
                                "ArtifactTypeScriptProvider_Invokables",
                                "testCompatibility",
                                List.of("barbaz"),
                                new String(jsLibrarySource));
        var after2 = System.currentTimeMillis();

        engine.close();

        assertTrue(result1.get("incompatibleDifferences").isArray());
        assertTrue(result2.get("incompatibleDifferences").isArray());
        var mapper = new ObjectMapper();
        assertEquals(mapper.writeValueAsString(result1), mapper.writeValueAsString(result2));

        System.out.println("Approx consumed time first run: " + (after1 - before1));
        System.out.println("Approx consumed time second run: " + (after2 - before2));
    }

    @Test
    public void getNumbersOnApicurioUseCase() throws Exception {
        var before = System.currentTimeMillis();
        var builtins =
                Builtins.builder("java_api")
                        .add(
                                new HostFunction(
                                        "info",
                                        List.of(String.class),
                                        Void.class,
                                        (args) -> {
                                            String str = (String) args.get(0);

                                            System.out.println("LOG - info - " + str);
                                            return str;
                                        }),
                                new HostFunction(
                                        "debug",
                                        List.of(String.class),
                                        Void.class,
                                        (args) -> {
                                            String str = (String) args.get(0);

                                            System.out.println("LOG - debug - " + str);
                                            return str;
                                        }))
                        .build();
        var invokables =
                Invokables.builder("ArtifactTypeScriptProvider_Invokables")
                        .add(
                                new GuestFunction(
                                        "testCompatibility", List.of(String.class), JsonNode.class))
                        .build();

        var buildingEngine =
                Engine.builder().addBuiltins(builtins).addInvokables(invokables).build();

        var jsLibrarySource =
                EngineTest.class
                        .getResourceAsStream("/apicurio-numbers/js-artifact-types-test.js")
                        .readAllBytes();

        // this is the code that will be generated by @GuestFunction
        var jsCompiledCode =
                buildingEngine.compilePortableGuestFunction(
                        new String(jsLibrarySource, StandardCharsets.UTF_8));
        // the separation is good, how things match together is not
        // TODO: string escaping is not working correctly -> verify
        // var argsStr = buildingEngine.computeArgs("ArtifactTypeScriptProvider_Invokables",
        // "testCompatibility", List.of("123test"));

        buildingEngine.close();
        var compiled = System.currentTimeMillis();

        // Now running on a separate engine without performing any compilation
        var runningEngine =
                Engine.builder().addBuiltins(builtins).addInvokables(invokables).build();
        var result =
                (JsonNode)
                        runningEngine.invokePrecompiledGuestFunction(
                                "ArtifactTypeScriptProvider_Invokables",
                                "testCompatibility",
                                List.of("123test"),
                                jsCompiledCode);

        runningEngine.close();

        assertTrue(result.get("incompatibleDifferences").isArray());
        var after = System.currentTimeMillis();

        System.out.println("Approx consumed time: TOTAL: " + (after - before));
        System.out.println("compilation: " + (compiled - before));
        System.out.println("execution: " + (after - compiled));
    }

    @Test
    public void handleExceptionsThrownInJava() {
        var builtins =
                Builtins.builder("from_java")
                        .addStringToString(
                                "imported_function",
                                (str) -> {
                                    throw new IndexOutOfBoundsException("whatever");
                                })
                        .build();
        var engine = Engine.builder().addBuiltins(builtins).build();

        // Act
        var codePtr = engine.compile("from_java.imported_function(\"ciao\");");

        var exception = assertThrows(IndexOutOfBoundsException.class, () -> engine.exec(codePtr));

        assertEquals("whatever", exception.getMessage());

        engine.free(codePtr);
        engine.close();
    }

    @Test
    public void handleExceptionsThrownInJs() {
        var engine = Engine.builder().build();

        // Act
        var codePtr = engine.compile("throw \"hello exception\";");

        var exception = assertThrows(GuestException.class, () -> engine.exec(codePtr));

        assertTrue(exception.getMessage().contains("hello exception"));

        engine.free(codePtr);
        engine.close();
    }

    @Test
    public void failToCompileJs() {
        var engine = Engine.builder().build();

        var exception =
                assertThrows(IllegalArgumentException.class, () -> engine.compile("1' \" 2 ..."));

        assertTrue(exception.getMessage().contains("Failed to compile JS code"));

        engine.close();
    }

    @Test
    public void supportAsyncAwait() throws Exception {
        var engine = Engine.builder().build();

        var jsSource =
                "function debug() {\n"
                        + "  return Promise.resolve(\"foo\");\n"
                        + "}\n"
                        + "console.log(await debug());";
        var codePtr = engine.compile(jsSource);
        engine.exec(codePtr);
        assertEquals("foo\n", engine.stdout());

        engine.free(codePtr);
        engine.close();
    }
}
