package chicory.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.roastedroot.quickjs4j.annotations.ScriptInterface;
import org.junit.jupiter.api.Test;

class ScriptInterfaceTest {

    @ScriptInterface()
    public interface UserFunction {
        int operation(int term1, int term2);
    }

    @Test
    public void scriptInterfaceUsage() throws Exception {
        // Arrange
        var jsLibrary = "function operation(term1, term2) { return (term1 * term2); }";
        var userFunctionProxy = new UserFunction_Proxy(jsLibrary);

        // Act
        var result = userFunctionProxy.operation(3, 2);

        // Assert
        assertEquals(6, result);

        userFunctionProxy.close();
    }

    @Test
    public void scriptInterfaceUsage2() throws Exception {
        // Arrange
        var jsLibrary = "function operation(term1, term2) { return (term1 + term2); }";
        var userFunctionProxy = new UserFunction_Proxy(jsLibrary);

        // Act
        var result = userFunctionProxy.operation(3, 2);

        // Assert
        assertEquals(5, result);

        userFunctionProxy.close();
    }
}
