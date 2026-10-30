package io.roastedroot.quickjs4j.jackson2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.roastedroot.quickjs4j.binding.Binding;
import io.roastedroot.quickjs4j.binding.TypedGuestFunction;
import io.roastedroot.quickjs4j.binding.TypedHostFunction;
import io.roastedroot.quickjs4j.core.Builtins;
import io.roastedroot.quickjs4j.core.Engine;
import io.roastedroot.quickjs4j.core.Runner;
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

    private static final String MODULE = "ArtifactTypeScriptProvider_Invokables";

    private static String jsLibrary() throws Exception {
        var bytes =
                ApicurioArtifactTypesTest.class
                        .getResourceAsStream(
                                "/quickjs4j-tck/apicurio-numbers/js-artifact-types-test.js")
                        .readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static Builtins logBuiltins(Binding binding, String module) {
        return binding.builtins(
                module,
                new TypedHostFunction("info", List.of(String.class), Void.class, args -> null),
                new TypedHostFunction("debug", List.of(String.class), Void.class, args -> null));
    }

    private static Engine engine(Binding binding, String builtinsModule) {
        return Engine.builder()
                .addBuiltins(logBuiltins(binding, builtinsModule))
                .addInvokables(
                        binding.invokables(
                                MODULE,
                                new TypedGuestFunction(
                                        "acceptsContent", List.of(JsonNode.class), Boolean.class),
                                new TypedGuestFunction(
                                        "validate", List.of(JsonNode.class), JsonNode.class),
                                new TypedGuestFunction(
                                        "testCompatibility",
                                        List.of(String.class),
                                        JsonNode.class)))
                .build();
    }

    @Test
    public void testInvokeAfterClose() throws Exception {
        var binding = Binding.create(new Jackson2Codec());
        var runner =
                binding.bind(
                        Runner.builder()
                                .withEngine(engine(binding, "ArtifactTypeScriptProvider_Builtins"))
                                .build());
        var jsLibrary = jsLibrary();

        var ramlRequest = mapper.createObjectNode();
        var typedContent = mapper.createObjectNode();
        typedContent.put("contentType", "application/x-yaml");
        typedContent.put("content", RAML_CONTENT);
        ramlRequest.set("typedContent", typedContent);

        var accepted =
                (Boolean)
                        runner.invokeGuestFunction(
                                MODULE, "acceptsContent", List.of(ramlRequest), jsLibrary);
        assertTrue(accepted);

        // Close the runner (as Apicurio's closeScriptProvider does after each operation)
        runner.close();

        // Reuse the closed runner (as Apicurio does via cache)
        var validRequest = mapper.createObjectNode();
        var validContent = mapper.createObjectNode();
        validContent.put("contentType", "application/x-yaml");
        validContent.put("content", RAML_CONTENT);
        validRequest.set("content", validContent);

        var validResult =
                (JsonNode)
                        runner.invokeGuestFunction(
                                MODULE, "validate", List.of(validRequest), jsLibrary);
        assertNotNull(validResult);
        assertTrue(validResult.has("ruleViolations"));
        assertEquals(0, validResult.get("ruleViolations").size());
    }

    @Test
    public void sameResultOnRepeatedInvocations() throws Exception {
        var binding = Binding.create(new Jackson2Codec());
        var jsLibrary = jsLibrary();

        try (var runner =
                binding.bind(Runner.builder().withEngine(engine(binding, "java_api")).build())) {
            var result1 =
                    (JsonNode)
                            runner.invokeGuestFunction(
                                    MODULE, "testCompatibility", List.of("zoobar"), jsLibrary);
            var result2 =
                    (JsonNode)
                            runner.invokeGuestFunction(
                                    MODULE, "testCompatibility", List.of("barbaz"), jsLibrary);

            assertTrue(result1.get("incompatibleDifferences").isArray());
            assertEquals(result1, result2);
        }
    }

    @Test
    public void precompiledOnAnotherEngine() throws Exception {
        var buildingBinding = Binding.create(new Jackson2Codec());
        byte[] compiled;
        try (var buildingEngine = engine(buildingBinding, "java_api")) {
            compiled = buildingEngine.compilePortableGuestFunction(jsLibrary());
        }

        var binding = Binding.create(new Jackson2Codec());
        try (var runner =
                binding.bind(Runner.builder().withEngine(engine(binding, "java_api")).build())) {
            var result =
                    (JsonNode)
                            runner.invokePrecompiledGuestFunction(
                                    MODULE, "testCompatibility", List.of("123test"), compiled);
            assertTrue(result.get("incompatibleDifferences").isArray());
        }
    }
}
