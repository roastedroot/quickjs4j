package chicory.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.roastedroot.quickjs4j.annotations.ScriptInterface;
import org.junit.jupiter.api.Test;

class ScriptInterfaceTest {

    @ScriptInterface()
    public interface UserFunction {
        int operation(int term1, int term2);

        void log(String message);
    }

    String jsLibrary(String operation) {
        return "function operation(term1, term2) { return (term1 "
                + operation
                + " term2); }\n"
                + "function log(msg) { console.log(msg); }";
    }

    @Test
    public void scriptInterfaceUsage() throws Exception {
        // Arrange
        var userFunctionProxy = new UserFunction_Proxy(jsLibrary("*"));

        // Act
        var result = userFunctionProxy.operation(3, 2);
        userFunctionProxy.log("result is " + result);

        // Assert
        assertEquals(6, result);

        userFunctionProxy.close();
    }

    @Test
    public void scriptInterfaceUsage2() throws Exception {
        // Arrange
        var userFunctionProxy = new UserFunction_Proxy(jsLibrary("+"));

        // Act
        var result = userFunctionProxy.operation(3, 2);

        // Assert
        assertEquals(5, result);

        userFunctionProxy.close();
    }
}
