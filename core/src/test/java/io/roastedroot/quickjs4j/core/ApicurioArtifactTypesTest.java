package io.roastedroot.quickjs4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Reproduces the Apicurio Registry failure pattern: the ScriptingService caches the
 * ArtifactTypeScriptProvider proxy (which wraps a Runner), but each operation calls
 * closeScriptProvider() which closes the Runner. Subsequent operations reuse the
 * cached (closed) proxy. In quickjs4j 0.0.15 this worked because
 * Runner.invokeGuestFunction bypassed the executor. In 0.0.16 it fails because
 * invokeGuestFunction now goes through submitWithTimeout, and the
 * executor is shut down by close().
 */
public class ApicurioArtifactTypesTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String RAML_CONTENT =
            "#%RAML 1.0\n"
                    + "title: Mobile Order API\n"
                    + "baseUri: http://localhost:8081/api\n"
                    + "version: \"1.0\"\n"
                    + "\n"
                    + "uses:\n"
                    + "  assets: assets.lib.raml\n"
                    + "\n"
                    + "annotationTypes:\n"
                    + "  monitoringInterval:\n"
                    + "    type: integer\n"
                    + "\n"
                    + "/orders:\n"
                    + "  displayName: Orders\n"
                    + "  get:\n"
                    + "    is: [ assets.paging ]\n"
                    + "    (monitoringInterval): 30\n"
                    + "    description: Lists all orders of a specific user\n"
                    + "    queryParameters:\n"
                    + "      userId:\n"
                    + "        type: string\n"
                    + "        description: use to query all orders of a user\n"
                    + "  post:\n"
                    + "  /{orderId}:\n"
                    + "    get:\n"
                    + "      responses:\n"
                    + "        200:\n"
                    + "          body:\n"
                    + "            application/json:\n"
                    + "              type: assets.Order\n"
                    + "            application/xml:\n"
                    + "              type: ~include schemas/order.xsd\n";

    private String loadJsLibrary() throws Exception {
        var bytes =
                ApicurioArtifactTypesTest.class
                        .getResourceAsStream("/apicurio-numbers/js-artifact-types-test.js")
                        .readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private Engine createEngine(Invokables invokables) {
        var builtins =
                Builtins.builder("ArtifactTypeScriptProvider_Builtins")
                        .add(
                                new HostFunction(
                                        "info",
                                        List.of(String.class),
                                        Void.class,
                                        (args) -> {
                                            return null;
                                        }),
                                new HostFunction(
                                        "debug",
                                        List.of(String.class),
                                        Void.class,
                                        (args) -> {
                                            return null;
                                        }))
                        .build();
        return Engine.builder().addBuiltins(builtins).addInvokables(invokables).build();
    }

    @Test
    public void testInvokeAfterClose() throws Exception {
        var invokables =
                Invokables.builder("ArtifactTypeScriptProvider_Invokables")
                        .add(
                                new GuestFunction(
                                        "acceptsContent", List.of(JsonNode.class), Boolean.class))
                        .add(new GuestFunction("validate", List.of(JsonNode.class), JsonNode.class))
                        .build();
        var engine = createEngine(invokables);
        var runner = Runner.builder().withEngine(engine).build();
        var jsLibrary = loadJsLibrary();

        // First call: acceptsContent - should work
        ObjectNode ramlRequest = mapper.createObjectNode();
        ObjectNode typedContent = mapper.createObjectNode();
        typedContent.put("contentType", "application/x-yaml");
        typedContent.put("content", RAML_CONTENT);
        ramlRequest.set("typedContent", typedContent);

        var accepted =
                (Boolean)
                        runner.invokeGuestFunction(
                                "ArtifactTypeScriptProvider_Invokables",
                                "acceptsContent",
                                List.of(ramlRequest),
                                jsLibrary);
        assertTrue(accepted);

        // Close the runner (as Apicurio's closeScriptProvider does after each operation)
        runner.close();

        // Second call: validate - reusing the closed runner (as Apicurio does via cache)
        ObjectNode validRequest = mapper.createObjectNode();
        ObjectNode validContent = mapper.createObjectNode();
        validContent.put("contentType", "application/x-yaml");
        validContent.put("content", RAML_CONTENT);
        validRequest.set("content", validContent);

        var validResult =
                (JsonNode)
                        runner.invokeGuestFunction(
                                "ArtifactTypeScriptProvider_Invokables",
                                "validate",
                                List.of(validRequest),
                                jsLibrary);
        assertNotNull(validResult);
        assertTrue(validResult.has("ruleViolations"));
        assertEquals(0, validResult.get("ruleViolations").size());
    }
}
