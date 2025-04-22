package chicory.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.js.Builtins;
import io.roastedroot.js.JsEngine;
import io.roastedroot.js.JsMachine;
import io.roastedroot.js.annotations.JavaRefParam;
import io.roastedroot.js.annotations.JsFunction;
import io.roastedroot.js.annotations.JsModule;
import io.roastedroot.js.annotations.ReturningJavaRef;
import org.junit.jupiter.api.Test;

class HelloJsTest {

    @JsModule()
    class JsTestModule {
        private boolean invoked;
        private boolean refInvoked;
        private final JsMachine machine;

        JsTestModule() {
            var builtins = Builtins.builder().add(JsTestModule_Builtins.toBuiltins(this)).build();
            var engine = JsEngine.builder().withBuiltins(builtins).build();
            this.machine = JsMachine.builder().withEngine(engine).build();
        }

        @JsFunction("my_java_func")
        public String add(int x, int y) {
            var sum = x + y;
            return "hello " + sum;
        }

        @JsFunction("my_java_check")
        public void check(String value) {
            invoked = true;
            assertEquals("hello 42", value);
        }

        @ReturningJavaRef
        @JsFunction("my_java_ref")
        public String myRef() {
            return "a pure java string";
        }

        @JsFunction("my_java_ref_check")
        public void myRefCheck(@JavaRefParam String value) {
            refInvoked = true;
            assertEquals("a pure java string", value);
        }

        public void exec(String code) {
            machine.compileAndExec(code);
        }

        public boolean isInvoked() {
            return invoked;
        }

        public boolean isRefInvoked() {
            return refInvoked;
        }
    }

    @Test
    public void helloJsModule() {
        // Arrange
        var helloJsModule = new JsTestModule();

        // Act
        helloJsModule.exec("my_java_check(my_java_func(40, 2));");

        // Assert
        assertTrue(helloJsModule.isInvoked());
    }

    @Test
    public void useJavaRefs() {
        // Arrange
        var helloJsModule = new JsTestModule();

        // Act
        helloJsModule.exec("my_java_ref_check(my_java_ref());");

        // assert
        assertTrue(helloJsModule.isRefInvoked());
    }
}
