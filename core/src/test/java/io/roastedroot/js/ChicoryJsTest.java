package io.roastedroot.js;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

public class ChicoryJsTest {

    @Test
    public void basicUsage() {
        // Arrange
        var invoked = new AtomicBoolean(false);
        var chicoryJs = ChicoryJs.builder().withImportedFunction(() -> invoked.set(true)).build();

        // Act
        var codePtr = chicoryJs.compile("console.log(\"hello js world!!!\"); myJavaFunc();");
        chicoryJs.exec(codePtr);
        chicoryJs.free(codePtr);
        chicoryJs.close();

        // Assert
        assertTrue(invoked.get());
    }
}
