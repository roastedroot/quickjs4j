package chicory.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.js.Builtins;
import io.roastedroot.js.JsEngine;
import io.roastedroot.js.JsMachine;
import io.roastedroot.js.annotations.JsFunction;
import io.roastedroot.js.annotations.JsModule;
import org.junit.jupiter.api.Test;

class HelloJsTest {

    @JsModule()
    class JsTestModule {
        private boolean invoked;
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

        public void exec(String code) {
            machine.compileAndExec(code);
        }

        public boolean isInvoked() {
            return invoked;
        }
    }

    @Test
    public void helloWasiModule() {
        // Arrange
        var helloJsModule = new JsTestModule();

        // Act
        helloJsModule.exec("my_java_check(my_java_func(40, 2));");

        // Assert
        assertTrue(helloJsModule.isInvoked());
    }
}
