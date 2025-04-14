package io.roastedroot.js;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

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
        var builtins = Builtins.builder()
                .addIntIntToInt("add", ChicoryJsTest::add)
                .addIntToVoid("check", ChicoryJsTest.check(42))
                .build();

        var chicoryJs = ChicoryJs.builder()
                .withBuiltins(builtins).build();

        var codePtr = chicoryJs.compile("check(add(40, 2));");
        chicoryJs.exec(codePtr);
        chicoryJs.free(codePtr);
        chicoryJs.close();
    }

    @Test
    public void callJavaFunctionsFromJSNegativeCheck() {
        var builtins = Builtins.builder()
                .addIntIntToInt("add", ChicoryJsTest::add)
                .addIntToVoid("check", ChicoryJsTest.check(43))
                .build();

        var chicoryJs = ChicoryJs.builder()
                .withBuiltins(builtins).build();

        var codePtr = chicoryJs.compile("check(add(40, 2));");

        assertThrows(AssertionError.class, () -> chicoryJs.exec(codePtr));
        chicoryJs.free(codePtr);
        chicoryJs.close();
    }

    public static void func1() {
        System.out.println("void -> void function");
    }
    public static void func2(int a) {
        System.out.println("int -> void function " + a);
    }
    public static void func3(String a) {
        System.out.println("String -> void function " + a);
    }
    public static String func4() {
        System.out.println("void -> String functionS");
        return "funcS";
    }
    public static String func5(int a) {
        System.out.println("void -> String functionS " + a);
        return "funcS" + a;
    }
    public static String func6(String a) {
        System.out.println("void -> String functionS " + a);
        return "funcS" + a;
    }

//    @Test
//    public void callJavaFunctionsFromJSWithDifferentParamsAndReturns() {
//        var builtins = Builtins.builder()
//                .build();
//
//        var chicoryJs = ChicoryJs.builder()
//                .withBuiltins(builtins).build();
//
//        var codePtr = chicoryJs.compile("check(add(40, 2));");
//        chicoryJs.exec(codePtr);
//        chicoryJs.free(codePtr);
//        chicoryJs.close();
//    }

    // TODO: write a test to verify different types of args/returns

    // TODO: verify if we need to invoke functions on objects passed as proxies?
}
