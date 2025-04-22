package io.roastedroot.quickjs4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
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
                Builtins.builder()
                        .addStringToString(
                                "java_imported_function",
                                (str) -> {
                                    assertEquals("ciao", str);
                                    invoked.set(true);
                                    return "{ received: " + str + " }";
                                })
                        .build();
        var engine = Engine.builder().withBuiltins(builtins).build();

        // Act
        var codePtr =
                engine.compile(
                        "console.log(\"hello js world!!!\");"
                                + " console.error(java_imported_function(\"ciao\"));");
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
        var builtins =
                Builtins.builder()
                        .addIntIntToInt("add", EngineTest::add)
                        .addIntToVoid("check", EngineTest.check(42))
                        .build();

        var engine = Engine.builder().build().builder().withBuiltins(builtins).build();

        var codePtr = engine.compile("check(add(40, 2));");
        engine.exec(codePtr);
        engine.free(codePtr);
        engine.close();
    }

    @Test
    public void callJavaFunctionsFromJSNegativeCheck() {
        var builtins =
                Builtins.builder()
                        .addIntIntToInt("add", EngineTest::add)
                        .addIntToVoid("check", EngineTest.check(43))
                        .build();

        var engine = Engine.builder().withBuiltins(builtins).build();

        var codePtr = engine.compile("check(add(40, 2));");

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
                Builtins.builder()
                        .addVoidToVoid("func1", this::func1)
                        .addIntToVoid("func2", this::func2)
                        .addStringToVoid("func3", this::func3)
                        .addVoidToString("func4", this::func4)
                        .addIntToString("func5", this::func5)
                        .addStringToString("func6", this::func6)
                        .addStringToVoid("check", str -> assertEquals(toCheck.get(), str))
                        .build();

        var engine = Engine.builder().withBuiltins(builtins).build();

        compileAndExec(engine, "func1();");
        assertTrue(func1Called);

        compileAndExec(engine, "func2(10);");
        assertEquals(10, func2Called);

        compileAndExec(engine, "func3(\"h3110\");");
        assertEquals("h3110", func3Called);

        toCheck.set("func4");
        compileAndExec(engine, "check(func4());");
        assertEquals("func4", func4Called);

        compileAndExec(engine, "func5(11);");
        assertEquals(11, func5Called);

        // negative - needs to be last as the runtime needs a restart after exception
        toCheck.set("myFunc");
        assertThrows(AssertionFailedError.class, () -> compileAndExec(engine, "check(func4());"));
    }

    @Test
    public void callJavaFunctionsWithMixedParameters() {
        var expectedX = 123;
        var expectedY = "hello my world";
        var expectedZ = 321;
        var builtins =
                Builtins.builder()
                        .add(
                                new HostFunction(
                                        "myFunc",
                                        0,
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

        var engine = Engine.builder().withBuiltins(builtins).build();

        compileAndExec(
                engine, String.format("myFunc(%d, \"%s\", %d);", expectedX, expectedY, expectedZ));
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
                Builtins.builder()
                        .add(
                                new HostFunction(
                                        "getUser",
                                        0,
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
                                        1,
                                        List.of(HostRef.class),
                                        Void.class,
                                        (args) -> {
                                            var user = (User) args.get(0);

                                            assertEquals(expectedUser, user);
                                            return null;
                                        }))
                        .build();

        var engine = Engine.builder().withBuiltins(builtins).build();

        compileAndExec(
                engine,
                String.format(
                        "const user = getUser(\"%s\", \"%s\", %d);\n" + "checkUser(user);",
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
                Builtins.builder()
                        .addVoidToString("java_text", () -> "my Moooodule")
                        .addStringToVoid("java_check", (str) -> assertEquals(myCow, str))
                        .build();
        var engine = Engine.builder().withBuiltins(builtins).build();

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
                Builtins.builder()
                        .add(
                                new HostFunction(
                                        "java_check_tuna",
                                        0,
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
                                        1,
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
        var engine = Engine.builder().withBuiltins(builtins).build();

        var jsSource = EngineTest.class.getResourceAsStream("/zod/dist/out.js").readAllBytes();

        var codePtr = engine.compile(jsSource);
        engine.exec(codePtr);
        engine.free(codePtr);
        engine.close();
    }

    @Test
    public void cacheCompiledJS() throws Exception {
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
}
