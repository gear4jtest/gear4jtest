package io.github.gear4jtest.core.persistence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageRequestTest {

    @Test
    void constructor_shouldRejectUnboundedLimits() {
        // Given
        int unsafeLimit = PageRequest.MAX_LIMIT + 1;

        // When / Then
        assertThatThrownBy(() -> new PageRequest(0, unsafeLimit)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit must be <= " + PageRequest.MAX_LIMIT);
    }

    @Test
    void first_shouldCreateFirstPageWithinMaximumLimit() {
        // When
        PageRequest request = PageRequest.first(PageRequest.MAX_LIMIT);

        // Then
        assertThat(request.offset()).isZero();
        assertThat(request.limit()).isEqualTo(PageRequest.MAX_LIMIT);
    }
}
