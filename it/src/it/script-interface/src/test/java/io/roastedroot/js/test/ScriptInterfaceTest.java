package chicory.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.roastedroot.quickjs4j.annotations.ScriptInterface;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ScriptInterfaceTest {

    public class DivideByZeroException extends Exception {
        public DivideByZeroException(String message) {
            super(message);
        }

        public DivideByZeroException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public class CalculatorContext {
        public void log(String message) {
            System.out.println("LOG>> " + message);
        }
    }

    @ScriptInterface(context = CalculatorContext.class)
    public interface Calculator {
        int add(int term1, int term2);

        int subtract(int term1, int term2);

        int multiply(int factor1, int factor2);

        int divide(int dividend, int divisor) throws DivideByZeroException;
    }

    @Test
    public void scriptInterfaceUsage() throws Exception {
        // Arrange
        var jsLibrary =
                new String(
                        ScriptInterfaceTest.class
                                .getResourceAsStream("/ts/dist/out.js")
                                .readAllBytes(),
                        StandardCharsets.UTF_8);
        var calculatorProxy = new Calculator_Proxy(jsLibrary, new CalculatorContext());

        // Act
        var add = calculatorProxy.add(1, 2);
        var subtract = calculatorProxy.subtract(3, 1);
        var multiply = calculatorProxy.multiply(3, 2);
        var divide = calculatorProxy.divide(6, 2);

        // Assert
        assertEquals(3, add);
        assertEquals(2, subtract);
        assertEquals(6, multiply);
        assertEquals(3, divide);
    }
}
