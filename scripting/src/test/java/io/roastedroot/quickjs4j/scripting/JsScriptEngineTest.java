package io.roastedroot.quickjs4j.scripting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.script.ScriptEngineManager;
import org.junit.jupiter.api.Test;

public class JsScriptEngineTest {

    @Test
    public void testBasicEval() throws Exception {
        var engine = new ScriptEngineManager().getEngineByName("quickjs4j");

        var res1 = engine.eval("const x = 3;");
        assertNull(res1);

        var res2 = engine.eval("return 3;");
        assertNotNull(res2);
        assertEquals(3, res2);
    }

    @Test
    public void testInteractionWithStdout() throws Exception {
        var engine = new ScriptEngineManager().getEngineByName("quickjs4j");

        var res = engine.eval("console.log('foo'); return \"3\";");

        assertNotNull(res);
        assertEquals("3", res);
    }

    @Test
    public void testAllowMultilineStrings() throws Exception {
        var engine = new ScriptEngineManager().getEngineByName("quickjs4j");
        engine.put("source", "line1\nline2\n");

        var res = engine.eval("return source;");

        assertNotNull(res);
        assertEquals("line1\nline2\n", res);
    }
}
