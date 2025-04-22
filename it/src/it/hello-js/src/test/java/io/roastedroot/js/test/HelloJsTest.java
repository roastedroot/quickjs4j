package chicory.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.quickjs4j.annotations.HostFunction;
import io.roastedroot.quickjs4j.annotations.HostRefParam;
import io.roastedroot.quickjs4j.annotations.JsModule;
import io.roastedroot.quickjs4j.annotations.ReturnsHostRef;
import io.roastedroot.quickjs4j.core.Builtins;
import io.roastedroot.quickjs4j.core.Engine;
import io.roastedroot.quickjs4j.core.Runner;
import org.junit.jupiter.api.Test;

class HelloJsTest {

    @JsModule()
    class JsTestModule {
        private boolean invoked;
        private boolean refInvoked;
        private final Runner runner;

        JsTestModule() {
            var builtins = Builtins.builder().add(JsTestModule_Builtins.toBuiltins(this)).build();
            var engine = Engine.builder().withBuiltins(builtins).build();
            this.runner = Runner.builder().withEngine(engine).build();
        }

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

        public void exec(String code) {
            runner.compileAndExec(code);
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
