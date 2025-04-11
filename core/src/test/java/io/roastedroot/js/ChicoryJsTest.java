package io.roastedroot.js;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

public class ChicoryJsTest {

    @Test
    public void basicUsage() {
        // Arrange
        var invoked = new AtomicBoolean(false);
        var chicoryJs =
                ChicoryJs.builder()
                        .withImportedFunction(
                                (str) -> {
                                    assertEquals("ciao", str);
                                    invoked.set(true);
                                    return "{ received: " + str + "}";
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

    @Test
    public void javaFunctionInvocation() {
        // Arrange
        var chicoryJs =
                ChicoryJs.builder()
                        .withImportedFunction(
                                (str) -> {
                                    System.out.println("from here " + str);

                                    return "java_imported_function(\"from_java\");";
                                })
                        .build();

        // Act
        var codePtr = chicoryJs.compile("eval(java_imported_function(\"from_js\"));");
        chicoryJs.exec(codePtr);
        chicoryJs.free(codePtr);
        chicoryJs.close();

        // Assert
    }
}
