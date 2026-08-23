package io.github.gear4jtest.external.api.storage;

import java.nio.charset.StandardCharsets;
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

        OperationChainConfig invalidWriteModeConfig = new OperationChainConfig("pipeline", false, StoreType.MEMORY,
                Map.of("mode.write", "ALL_THE_THINGS"));

        assertThatThrownBy(() -> provider.forConfig(invalidWriteModeConfig))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mode.write");

        OperationChainConfig invalidVerificationLimitConfig = new OperationChainConfig("pipeline", false,
                StoreType.MEMORY,
                Map.of("verificationMaxArtifactSizeBytes", "not-a-number"));

        assertThatThrownBy(() -> provider.forConfig(invalidVerificationLimitConfig))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("verificationMaxArtifactSizeBytes");
    }

    @Test
    void forConfig_shouldBuildCompositeStoreWhenFallbacksAreConfigured() {
        DefaultArtifactStoreProvider provider = new DefaultArtifactStoreProvider(
                new ArtifactStoreResolver(getClass().getClassLoader()), null, Runnable::run);
        OperationChainConfig config = new OperationChainConfig("pipeline", false, StoreType.MEMORY,
                Map.of("fallback.2.type", "MEMORY",
                       "fallback.2.props.maxEntries", "100",
                       "fallback.1.type", "MEMORY",
                       "verifyOnRead", "true",
                       "selfHealing", "true"));

        ArtifactStore store = provider.forConfig(config);

        assertThat(store).isInstanceOf(CompositeArtifactStore.class);
    }

    @Test
    void forConfig_shouldRejectUnknownPrimaryAndFallbackProperties() {
        DefaultArtifactStoreProvider provider = new DefaultArtifactStoreProvider(
                new ArtifactStoreResolver(getClass().getClassLoader()), null, Runnable::run);

        assertThatThrownBy(() -> provider.forConfig(new OperationChainConfig("pipeline", false, StoreType.MEMORY,
                Map.of("maxEntry", "10"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxEntry")
                .hasMessageContaining("maxEntries");
        assertThatThrownBy(() -> provider.forConfig(new OperationChainConfig("pipeline", false, StoreType.MEMORY,
                Map.of("fallback.1.type", "MEMORY", "fallback.1.props.maxEntry", "10"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxEntry")
                .hasMessageContaining("maxEntries");
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
    void forConfig_shouldKeepVerifyOnReadEffectiveWithoutFallback() {
        DefaultArtifactStoreProvider provider = new DefaultArtifactStoreProvider(
                new ArtifactStoreResolver(getClass().getClassLoader()), null, Runnable::run);

        ArtifactStore store = provider.forConfig(new OperationChainConfig("pipeline", false, StoreType.MEMORY,
                Map.of("verifyOnRead", "true")));

        assertThat(store).isInstanceOf(CompositeArtifactStore.class);
    }

    @Test
    void forConfig_shouldRejectNonBooleanFlags() {
        DefaultArtifactStoreProvider provider = new DefaultArtifactStoreProvider(
                new ArtifactStoreResolver(getClass().getClassLoader()), null, Runnable::run);

        assertThatThrownBy(() -> provider.forConfig(new OperationChainConfig("pipeline", false, StoreType.MEMORY,
                Map.of("verifyOnRead", "yes"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("verifyOnRead")
                .hasMessageContaining("true or false");
        assertThatThrownBy(() -> provider.forConfig(new OperationChainConfig("pipeline", false, StoreType.MEMORY,
                Map.of("selfHealing", "1"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("selfHealing")
                .hasMessageContaining("true or false");
        assertThatThrownBy(() -> provider.forConfig(new OperationChainConfig("pipeline", false, StoreType.MEMORY,
                Map.of("requirePrivatePermissions", "sometimes"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requirePrivatePermissions")
                .hasMessageContaining("true or false");
    }

    @Test
    void forConfig_shouldRejectIncompleteFallbackAndReplicationWithoutFallback() {
        DefaultArtifactStoreProvider provider = new DefaultArtifactStoreProvider(
                new ArtifactStoreResolver(getClass().getClassLoader()), null, Runnable::run);

        assertThatThrownBy(() -> provider.forConfig(new OperationChainConfig("pipeline", false, StoreType.MEMORY,
                Map.of("fallback.1.props.path", "/tmp/store"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fallback.1.type");
        assertThatThrownBy(() -> provider.forConfig(new OperationChainConfig("pipeline", false, StoreType.MEMORY,
                Map.of("mode.write", "SYNC_ALL"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires at least one complete fallback");
        assertThatThrownBy(() -> provider.forConfig(new OperationChainConfig("pipeline", false, StoreType.MEMORY,
                Map.of("selfHealing", "true"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("selfHealing=true");
    }

    @Test
    void resolver_shouldExposeImmutableAvailableTypes() {
        ArtifactStoreResolver resolver = new ArtifactStoreResolver(getClass().getClassLoader());

        assertThatThrownBy(() -> resolver.availableTypes().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(resolver.availableTypes()).contains("MEMORY");
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
        assertThatThrownBy(() -> provider.forConfig(new OperationChainConfig("pipeline", false, StoreType.MEMORY,
                Map.of("fallback.0.type", "MEMORY"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive integer");
    }

    @Test
    void provider_shouldShareEquivalentStoreConfigurationsUntilEveryLeaseIsReleased() throws Exception {
        // Given
        DefaultArtifactStoreProvider provider = new DefaultArtifactStoreProvider(
                new ArtifactStoreResolver(getClass().getClassLoader()), null, Runnable::run);
        OperationChainConfig firstConfig = new OperationChainConfig("first", false, StoreType.MEMORY, Map.of());
        OperationChainConfig secondConfig = new OperationChainConfig("second", false, StoreType.MEMORY, Map.of());

        // When
        ArtifactStore first = provider.forConfig(firstConfig);
        ArtifactStore second = provider.forConfig(secondConfig);
        String hash = first.put("shared".getBytes(StandardCharsets.UTF_8));
        provider.release(first);

        // Then
        assertThat(second).isSameAs(first);
        assertThat(second.exists(hash)).isTrue();

        // When
        provider.release(second);
        ArtifactStore replacement = provider.forConfig(firstConfig);

        // Then
        assertThat(replacement).isNotSameAs(first);
        assertThat(replacement.exists(hash)).isFalse();
        provider.release(replacement);
        provider.close();
    }
}
