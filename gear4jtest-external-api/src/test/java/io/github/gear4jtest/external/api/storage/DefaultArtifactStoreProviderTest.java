package io.github.gear4jtest.external.api.storage;

import java.util.Map;

import io.github.gear4jtest.external.api.StoreType;
import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.artifact.CompositeArtifactStore;
import io.github.gear4jtest.external.api.model.OperationChainConfig;
import io.github.gear4jtest.external.api.spi.ArtifactStoreResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void forConfig_shouldRejectInvalidWriteModeAndInvalidVerificationLimit() {
        DefaultArtifactStoreProvider provider = new DefaultArtifactStoreProvider(
                new ArtifactStoreResolver(getClass().getClassLoader()), null, Runnable::run);

        assertThatThrownBy(() -> provider.forConfig(new OperationChainConfig("pipeline", false, StoreType.MEMORY,
                Map.of("mode.write", "ALL_THE_THINGS"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mode.write");

        assertThatThrownBy(() -> provider.forConfig(new OperationChainConfig("pipeline", false, StoreType.MEMORY,
                Map.of("verificationMaxArtifactSizeBytes", "not-a-number"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("verificationMaxArtifactSizeBytes");
    }

    @Test
    void forConfig_shouldBuildCompositeStoreWhenFallbacksAreConfigured() {
        DefaultArtifactStoreProvider provider = new DefaultArtifactStoreProvider(
                new ArtifactStoreResolver(getClass().getClassLoader()), null, Runnable::run);
        OperationChainConfig config = new OperationChainConfig("pipeline", false, StoreType.MEMORY,
                Map.of("fallback.2.type", "MEMORY",
                       "fallback.2.props.ignored", "value",
                       "fallback.1.type", "MEMORY",
                       "verifyOnRead", "yes",
                       "selfHealing", "1"));

        ArtifactStore store = provider.forConfig(config);

        assertThat(store).isInstanceOf(CompositeArtifactStore.class);
    }

    @Test
    void forConfig_shouldReturnPrimaryStoreWhenNoFallbackIsConfigured() {
        DefaultArtifactStoreProvider provider = new DefaultArtifactStoreProvider(
                new ArtifactStoreResolver(getClass().getClassLoader()), null, Runnable::run);

        ArtifactStore store = provider.forConfig(new OperationChainConfig("pipeline", false, StoreType.MEMORY,
                Map.of()));

        assertThat(store).isNotInstanceOf(CompositeArtifactStore.class);
    }

    @Test
    void forConfig_shouldRejectInvalidFallbackIndex() {
        DefaultArtifactStoreProvider provider = new DefaultArtifactStoreProvider(
                new ArtifactStoreResolver(getClass().getClassLoader()), null, Runnable::run);
        OperationChainConfig config = new OperationChainConfig("pipeline", false, StoreType.MEMORY,
                Map.of("fallback.alpha.type", "MEMORY"));

        assertThatThrownBy(() -> provider.forConfig(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid artifact fallback index 'alpha'");
    }
}
