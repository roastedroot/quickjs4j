package chicory.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.quickjs4j.annotations.Builtins;
import io.roastedroot.quickjs4j.annotations.GuestFunction;
import io.roastedroot.quickjs4j.annotations.HostFunction;
import io.roastedroot.quickjs4j.annotations.HostRefParam;
import io.roastedroot.quickjs4j.annotations.Invokables;
import io.roastedroot.quickjs4j.annotations.ReturnsHostRef;
import io.roastedroot.quickjs4j.core.Engine;
import io.roastedroot.quickjs4j.core.Runner;
import org.junit.jupiter.api.Test;

class HelloJsTest {

    @Invokables
    interface JsApi {
        @GuestFunction("my_js_func")
        int sub(int x, int y);
    }

    private String JS_LIBRARY_CODE = "function my_js_func(x, y) { return x - y; }";

    @Builtins("from_java")
    class JavaApi {
        public boolean invoked;
        public boolean refInvoked;

        @HostFunction("my_java_func")
        public String add(int x, int y) {
            var sum = x + y;
            return "hello " + sum;
        }

        @HostFunction("my_java_check")
        public void check(String value) {
            invoked = true;
            assertEquals("hello 42", value);
        }

        @ReturnsHostRef
        @HostFunction("my_java_ref")
        public String myRef() {
            return "a pure java string";
        }

        @HostFunction("my_java_ref_check")
        public void myRefCheck(@HostRefParam String value) {
            refInvoked = true;
            assertEquals("a pure java string", value);
        }
    }

    class JsTest {
        // Sandbox
        private final Runner runner;

        // the Java API
        private final JavaApi javaApi = new JavaApi();

        // the JS API
        private final JsApi jsApi = JsApi_Invokables.create(JS_LIBRARY_CODE);

        JsTest() {
            var engine =
                    Engine.builder()
                            .addBuiltins(JavaApi_Builtins.toBuiltins(javaApi))
                            .addInvokables(JsApi_Invokables.toInvokables())
                            .build();
            this.runner = Runner.builder().withEngine(engine).build();
        }

        public void exec(String code) {
            runner.compileAndExec(code);
        }

        public boolean isInvoked() {
            return javaApi.invoked;
        }

        public boolean isRefInvoked() {
            return javaApi.refInvoked;
        }

        public int sub(int x, int y) {
            return jsApi.sub(x, y);
        }
    }

    @Test
    public void helloJsModule() {
        // Arrange
        var helloJs = new JsTest();

        // Act
        helloJs.exec("my_js.my_java_check(my_js.my_java_func(40, 2));");

        // Assert
        assertTrue(helloJs.isInvoked());
    }

    @Test
    public void useJavaRefs() {
        // Arrange
        var helloJs = new JsTest();

        // Act
        helloJs.exec("my_js.my_java_ref_check(my_js.my_java_ref());");

        // assert
        assertTrue(helloJs.isRefInvoked());
    }

    @Test
    public void useInvokables() {
        // Arrange
        var helloJs = new JsTest();

        // Act
        var result = helloJs.sub(5, 2);

        // assert
        assertEquals(3, result);
    }
}
