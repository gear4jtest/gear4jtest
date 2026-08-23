package io.github.gear4jtest.external.api.spi;

import java.util.List;
import java.util.Map;

import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.artifact.InMemoryArtifactStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtifactStoreResolverTest {
    @Test
    void constructor_shouldRejectDuplicateCanonicalTypesRegardlessOfDiscoveryOrder() {
        ArtifactStorePlugin first = new FirstCustomPlugin();
        ArtifactStorePlugin second = new SecondCustomPlugin();

        assertThatThrownBy(() -> new ArtifactStoreResolver(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ambiguous ArtifactStorePlugin for CUSTOM")
                .hasMessageContaining(first.getClass().getName());
        assertThatThrownBy(() -> new ArtifactStoreResolver(List.of(second, first)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ambiguous ArtifactStorePlugin for CUSTOM")
                .hasMessageContaining(first.getClass().getName());
    }

    @Test
    void availableTypes_shouldHaveStableLexicographicIterationOrder() {
        ArtifactStoreResolver resolver = new ArtifactStoreResolver(List.of(new ZetaPlugin(), new AlphaPlugin()));

        assertThat(resolver.availableTypes()).containsExactly("ALPHA", "ZETA");
    }

    private abstract static class StubPlugin implements ArtifactStorePlugin {
        @Override
        public ArtifactStore build(Map<String, String> props, Context ctx) {
            return new InMemoryArtifactStore();
        }
    }

    private static final class FirstCustomPlugin extends StubPlugin {
        @Override
        public String type() {
            return "custom";
        }
    }

    private static final class SecondCustomPlugin extends StubPlugin {
        @Override
        public String type() {
            return "CUSTOM";
        }
    }

    private static final class AlphaPlugin extends StubPlugin {
        @Override
        public String type() {
            return "ALPHA";
        }
    }

    private static final class ZetaPlugin extends StubPlugin {
        @Override
        public String type() {
            return "ZETA";
        }
    }
}
