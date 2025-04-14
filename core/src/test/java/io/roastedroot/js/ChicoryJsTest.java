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
        var builtins =
                Builtins.builder()
                        .addVoidToVoid("func1", this::func1)
                        .addIntToVoid("func2", this::func2)
                        .addStringToVoid("func3", this::func3)
                        .addVoidToString("func4", this::func4)
                        .addIntToString("func5", this::func5)
                        .addStringToString("func6", this::func6)
                        .build();

        var chicoryJs = ChicoryJs.builder().withBuiltins(builtins).build();

        compileAndExec(chicoryJs, "func1();");
        assertTrue(func1Called);

        compileAndExec(chicoryJs, "func2(10);");
        assertEquals(10, func2Called);

        compileAndExec(chicoryJs, "func3(\"h3110\");");
        assertEquals("h3110", func3Called);

        compileAndExec(chicoryJs, "func4();");
        assertEquals("func4", func4Called);

        compileAndExec(chicoryJs, "func5(11);");
        assertEquals(11, func5Called);
    }

    // TODO: write a test to verify mixed types of args/returns

    // TODO: verify if we need to invoke functions on objects passed as proxies?
}
