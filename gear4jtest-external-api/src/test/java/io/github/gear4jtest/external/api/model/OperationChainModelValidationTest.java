package io.github.gear4jtest.external.api.model;

import java.time.Instant;
import java.util.Map;

import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.external.api.StoreType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationChainModelValidationTest {
    @Test
    void object_shouldNormalizeHashAndRejectSchemaViolations() {
        Instant now = Instant.parse("2026-07-16T12:00:00Z");

        OperationChainObject object = new OperationChainObject(null, "line", "1.0.0", ExecutionMode.TEST,
                "A".repeat(64), 0L, "application/xml", now, null, now);

        assertThat(object.contentHash()).isEqualTo("a".repeat(64));
        assertThatThrownBy(() -> new OperationChainObject(null, " ", "1.0.0", ExecutionMode.TEST,
                "a".repeat(64), 1L, "application/xml", now, null, now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alId");
        assertThatThrownBy(() -> new OperationChainObject(null, "line", "v".repeat(101), ExecutionMode.TEST,
                "a".repeat(64), 1L, "application/xml", now, null, now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version")
                .hasMessageContaining("100");
        assertThatThrownBy(() -> new OperationChainObject(null, "line", "1.0.0", ExecutionMode.TEST,
                "not-a-hash", 1L, "application/xml", now, null, now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SHA-256");
        assertThatThrownBy(() -> new OperationChainObject(null, "line", "1.0.0", ExecutionMode.TEST,
                "a".repeat(64), -1L, "application/xml", now, null, now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sizeBytes");
        assertThatThrownBy(() -> new OperationChainObject(null, "line", "1.0.0", ExecutionMode.TEST,
                "a".repeat(64), 1L, "application/xml", null, null, now))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("createdAt");
        assertThatThrownBy(() -> new OperationChainObject(null, "line", "1.0.0", ExecutionMode.TEST,
                "a".repeat(64), 1L, "application/xml", now, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("publishedAt");
    }

    @Test
    void contentIdentity_shouldNormalizeAndCompareAllPersistedComponents() {
        // Given
        OperationChainContentIdentity identity = new OperationChainContentIdentity("A".repeat(64), 42L,
                "application/xml");

        // When / Then
        assertThat(identity.contentHash()).isEqualTo("a".repeat(64));
        assertThat(identity).isEqualTo(new OperationChainContentIdentity("a".repeat(64), 42L, "application/xml"))
                .isNotEqualTo(new OperationChainContentIdentity("b".repeat(64), 42L, "application/xml"))
                .isNotEqualTo(new OperationChainContentIdentity("a".repeat(64), 43L, "application/xml"))
                .isNotEqualTo(new OperationChainContentIdentity("a".repeat(64), 42L, "application/json"));
    }

    @Test
    void objectContentIdentity_shouldExposeTheCanonicalPublicationIdentity() {
        // Given
        Instant now = Instant.parse("2026-07-16T12:00:00Z");
        OperationChainObject object = new OperationChainObject(null, "line", "1.0.0", ExecutionMode.TEST,
                "A".repeat(64), 42L, "application/xml", now, null, now);

        // When / Then
        assertThat(object.contentIdentity())
                .isEqualTo(new OperationChainContentIdentity("a".repeat(64), 42L, "application/xml"));
    }

    @Test
    void config_shouldRejectMissingDatabaseInvariants() {
        assertThatThrownBy(() -> new OperationChainConfig(" ", false, StoreType.MEMORY, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alId");
        assertThatThrownBy(() -> new OperationChainConfig("line", null, StoreType.MEMORY, Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("allowRunPublicationWithoutTest");
    }

    @Test
    void assemblyLineId_shouldUseTheShared255UnicodeCodePointLimit() {
        String maxSupplementaryCharacters = "\uD83D\uDE80".repeat(255);

        assertThat(new OperationChainConfig(maxSupplementaryCharacters, false, StoreType.MEMORY, Map.of()).alId())
                .isEqualTo(maxSupplementaryCharacters);
        assertThatThrownBy(() -> new OperationChainConfig("a".repeat(256), false, StoreType.MEMORY, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alId")
                .hasMessageContaining("255");
    }

    @Test
    void configToString_shouldRedactStorePropertyValues() {
        // Given
        String secret = "secret-access-key";
        OperationChainConfig config = new OperationChainConfig("line", false, StoreType.S3,
                Map.of("accessKey", secret, "bucket", "gear4j-artifacts"));

        // When
        String description = config.toString();

        // Then
        assertThat(description).contains("alId=line")
                .contains("storeType=S3")
                .contains("storePropsKeys=[accessKey, bucket]")
                .contains("storePropsCount=2")
                .contains("storePropsValues=<redacted>")
                .doesNotContain(secret)
                .doesNotContain("gear4j-artifacts");
    }
}
