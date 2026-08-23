package io.github.gear4jtest.external.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoreTypeTest {
    @Test
    void of_shouldCanonicalizeThirdPartyStoreType() {
        StoreType storeType = StoreType.of(" custom-store_2 ");

        assertThat(storeType.value()).isEqualTo("CUSTOM-STORE_2");
        assertThat(storeType.name()).isEqualTo("CUSTOM-STORE_2");
        assertThat(storeType.toString()).isEqualTo("CUSTOM-STORE_2");
    }

    @Test
    void constructor_shouldRejectValuesOutsidePersistentFormat() {
        assertThatThrownBy(() -> StoreType.of("2CUSTOM"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[A-Z][A-Z0-9_-]{0,63}");
        assertThatThrownBy(() -> StoreType.of("A".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
