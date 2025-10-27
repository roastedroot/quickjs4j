package chicory.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.quickjs4j.annotations.ScriptInterface;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScriptInterfaceGenericsTest {

    /**
     * Context class providing utility methods to the JavaScript environment.
     */
    public class StringListContext {
        /**
         * Logs a message to the console.
         *
         * @param message the message to log
         */
        public void log(String message) {
            System.out.println("LOG>> " + message);
        }
    }

    /**
     * Script interface demonstrating generic type handling with List&lt;String&gt;.
     */
    @ScriptInterface(context = StringListContext.class)
    public interface StringListProcessor {
        /**
         * Converts a list of strings to uppercase.
         *
         * @param items the list of strings to convert
         * @return a new list with all strings in uppercase
         */
        List<String> toUpperCase(List<String> items);

        /**
         * Filters a list of strings to only include those containing a specific substring.
         *
         * @param items the list of strings to filter
         * @param filter the substring to search for
         * @return a new list containing only matching strings
         */
        List<String> filterContaining(List<String> items, String filter);

        /**
         * Concatenates all strings in the list with a delimiter.
         *
         * @param items the list of strings to join
         * @param delimiter the delimiter to use between items
         * @return the concatenated string
         */
        String joinStrings(List<String> items, String delimiter);

        /**
         * Returns a list of strings generated from a count.
         *
         * @param count the number of items to generate
         * @return a list of generated strings
         */
        List<String> generateStrings(int count);
    }

    @Test
    public void testListStringInputAndOutput() throws Exception {
        // Arrange
        var jsLibrary =
                new String(
                        ScriptInterfaceGenericsTest.class
                                .getResourceAsStream("/ts/dist/out.js")
                                .readAllBytes(),
                        StandardCharsets.UTF_8);
        var processor = new StringListProcessor_Proxy(jsLibrary, new StringListContext());

        // Act
        var input = List.of("hello", "world", "java");
        var uppercased = processor.toUpperCase(input);

        // Assert
        assertEquals(3, uppercased.size());
        assertEquals("HELLO", uppercased.get(0));
        assertEquals("WORLD", uppercased.get(1));
        assertEquals("JAVA", uppercased.get(2));

        processor.close();
    }

    @Test
    public void testListStringFilter() throws Exception {
        // Arrange
        var jsLibrary =
                new String(
                        ScriptInterfaceGenericsTest.class
                                .getResourceAsStream("/ts/dist/out.js")
                                .readAllBytes(),
                        StandardCharsets.UTF_8);
        var processor = new StringListProcessor_Proxy(jsLibrary, new StringListContext());

        // Act
        var input = List.of("apple", "banana", "cherry", "apricot", "blueberry");
        var filtered = processor.filterContaining(input, "ap");

        // Assert
        assertEquals(2, filtered.size());
        assertTrue(filtered.contains("apple"));
        assertTrue(filtered.contains("apricot"));

        processor.close();
    }

    @Test
    public void testJoinStrings() throws Exception {
        // Arrange
        var jsLibrary =
                new String(
                        ScriptInterfaceGenericsTest.class
                                .getResourceAsStream("/ts/dist/out.js")
                                .readAllBytes(),
                        StandardCharsets.UTF_8);
        var processor = new StringListProcessor_Proxy(jsLibrary, new StringListContext());

        // Act
        var input = List.of("one", "two", "three");
        var joined = processor.joinStrings(input, ", ");

        // Assert
        assertEquals("one, two, three", joined);

        processor.close();
    }

    @Test
    public void testGenerateStrings() throws Exception {
        // Arrange
        var jsLibrary =
                new String(
                        ScriptInterfaceGenericsTest.class
                                .getResourceAsStream("/ts/dist/out.js")
                                .readAllBytes(),
                        StandardCharsets.UTF_8);
        var processor = new StringListProcessor_Proxy(jsLibrary, new StringListContext());

        // Act
        var generated = processor.generateStrings(5);

        // Assert
        assertEquals(5, generated.size());
        assertEquals("Item 0", generated.get(0));
        assertEquals("Item 1", generated.get(1));
        assertEquals("Item 4", generated.get(4));

        processor.close();
    }
}
