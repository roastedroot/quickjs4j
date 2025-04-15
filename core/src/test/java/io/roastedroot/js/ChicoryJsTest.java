package io.roastedroot.js;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

public class ChicoryJsTest {

    @Test
    public void basicUsage() {
        // Arrange
        var invoked = new AtomicBoolean(false);
        var chicoryJs =
                ChicoryJs.builder()
                        .withBuiltins(Builtins.builder().build())
                        .withImportedFunction(
                                (str) -> {
                                    assertEquals("ciao", str);
                                    invoked.set(true);
                                    return "{ received: " + str + " }";
                                })
                        .build();

        // Act
        var codePtr =
                chicoryJs.compile(
                        "console.log(\"hello js world!!!\");"
                                + " console.error(java_imported_function(\"ciao\"));");
        chicoryJs.exec(codePtr);
        chicoryJs.free(codePtr);
        chicoryJs.close();

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
                        .addIntIntToInt("add", ChicoryJsTest::add)
                        .addIntToVoid("check", ChicoryJsTest.check(42))
                        .build();

        var chicoryJs = ChicoryJs.builder().withBuiltins(builtins).build();

        var codePtr = chicoryJs.compile("check(add(40, 2));");
        chicoryJs.exec(codePtr);
        chicoryJs.free(codePtr);
        chicoryJs.close();
    }

    @Test
    public void callJavaFunctionsFromJSNegativeCheck() {
        var builtins =
                Builtins.builder()
                        .addIntIntToInt("add", ChicoryJsTest::add)
                        .addIntToVoid("check", ChicoryJsTest.check(43))
                        .build();

        var chicoryJs = ChicoryJs.builder().withBuiltins(builtins).build();

        var codePtr = chicoryJs.compile("check(add(40, 2));");

        assertThrows(AssertionError.class, () -> chicoryJs.exec(codePtr));
        chicoryJs.free(codePtr);
        chicoryJs.close();
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

    static void compileAndExec(ChicoryJs chicoryJs, String code) {
        var codePtr = chicoryJs.compile(code);
        chicoryJs.exec(codePtr);
        chicoryJs.free(codePtr);
        chicoryJs.close();
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

        var chicoryJs = ChicoryJs.builder().withBuiltins(builtins).build();

        compileAndExec(chicoryJs, "func1();");
        assertTrue(func1Called);

        compileAndExec(chicoryJs, "func2(10);");
        assertEquals(10, func2Called);

        compileAndExec(chicoryJs, "func3(\"h3110\");");
        assertEquals("h3110", func3Called);

        toCheck.set("func4");
        compileAndExec(chicoryJs, "check(func4());");
        assertEquals("func4", func4Called);

        compileAndExec(chicoryJs, "func5(11);");
        assertEquals(11, func5Called);

        // negative - needs to be last as the runtime needs a restart after exception
        toCheck.set("myFunc");
        assertThrows(
                AssertionFailedError.class, () -> compileAndExec(chicoryJs, "check(func4());"));
    }

    @Test
    public void callJavaFunctionsWithMixedParameters() {
        var expectedX = 123;
        var expectedY = "hello my world";
        var expectedZ = 321;
        var builtins =
                Builtins.builder()
                        .add(
                                "myFunc",
                                new JsFunction(
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

        var chicoryJs = ChicoryJs.builder().withBuiltins(builtins).build();

        compileAndExec(
                chicoryJs,
                String.format("myFunc(%d, \"%s\", %d);", expectedX, expectedY, expectedZ));
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
                                "getUser",
                                new JsFunction(
                                        "getUser",
                                        0,
                                        List.of(String.class, String.class, Integer.class),
                                        JavaRef.class,
                                        (args) -> {
                                            var name = (String) args.get(0);
                                            var surname = (String) args.get(1);
                                            var age = (Integer) args.get(2);

                                            return new User(name, surname, age);
                                        }))
                        .add(
                                "checkUser",
                                new JsFunction(
                                        "checkUser",
                                        1,
                                        List.of(JavaRef.class),
                                        Void.class,
                                        (args) -> {
                                            var user = (User) args.get(0);

                                            assertEquals(expectedUser, user);
                                            return null;
                                        }))
                        .build();

        var chicoryJs = ChicoryJs.builder().withBuiltins(builtins).build();

        compileAndExec(
                chicoryJs,
                String.format(
                        "const user = getUser(\"%s\", \"%s\", %d);\n" + "checkUser(user);",
                        expectedUser.name, expectedUser.surname, expectedUser.age));
    }
}
