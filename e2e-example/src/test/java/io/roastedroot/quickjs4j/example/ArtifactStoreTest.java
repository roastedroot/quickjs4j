package io.roastedroot.quickjs4j.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ArtifactStoreTest {

    Artifact raml = new Artifact("one", "raml", "some raml...");
    Artifact openapi = new Artifact("two", "openapi", "some openapi...");

    @Test
    public void basicUage() {
        try (var artifactStore = new ArtifactStore()) {

            assertFalse(artifactStore.api().validate(raml));
            assertEquals("one", artifactStore.lastMessage());

            assertTrue(artifactStore.api().validate(openapi));
            assertEquals("two", artifactStore.lastMessage());
        }
    }
}
