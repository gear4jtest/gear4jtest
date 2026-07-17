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
    void config_shouldRejectMissingDatabaseInvariants() {
        assertThatThrownBy(() -> new OperationChainConfig(" ", false, StoreType.MEMORY, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alId");
        assertThatThrownBy(() -> new OperationChainConfig("line", null, StoreType.MEMORY, Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("allowRunPublicationWithoutTest");
    }
}
