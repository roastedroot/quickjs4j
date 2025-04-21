package chicory.test;

import io.roastedroot.js.annotations.JsFunction;
import io.roastedroot.js.annotations.JsModule;
import org.junit.jupiter.api.Test;

class HelloJsTest {

    @JsModule()
    class JsTestModule {

        @JsFunction("my-java-func")
        public String doSomething(int x, int y) {
            System.out.println("invoked doSomething " + x + " " + y);
            return "go on from here " + (x + y);
        }

        // TODO: how to inject this method?
        public void exec(String code) {}
    }

    @Test
    public void helloWasiModule() {
        // Arrange
        var helloJsModule = new JsTestModule();

        // Act
        helloJsModule.exec("my-java-func(40, 2); console.log(\"finished\");");

        // Assert
    }
}
