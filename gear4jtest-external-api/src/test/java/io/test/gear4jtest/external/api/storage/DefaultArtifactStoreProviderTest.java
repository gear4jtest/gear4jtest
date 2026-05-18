package io.test.gear4jtest.external.api.storage;

import java.util.Map;

import io.test.gear4jtest.external.api.StoreType;
import io.test.gear4jtest.external.api.model.OperationChainConfig;
import io.test.gear4jtest.external.api.spi.ArtifactStoreResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultArtifactStoreProviderTest {
    @Test
    void forConfig_shouldRejectInvalidReadModeInsteadOfFallingBackSilently() {
        // Given
        DefaultArtifactStoreProvider provider = new DefaultArtifactStoreProvider(
                new ArtifactStoreResolver(getClass().getClassLoader()), null, Runnable::run);
        OperationChainConfig config = new OperationChainConfig("pipeline", false, StoreType.MEMORY,
                Map.of("mode.read", "FIRST_AVAILABLE"));

        // When / Then
        assertThatThrownBy(() -> provider.forConfig(config)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mode.read");
    }
}
