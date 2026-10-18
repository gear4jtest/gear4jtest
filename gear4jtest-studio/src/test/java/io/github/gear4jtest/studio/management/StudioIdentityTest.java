package io.github.gear4jtest.studio.management;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class StudioIdentityTest {
    @Test
    void scopedSubjectsCannotCollideAcrossAuthoritiesOrAmbiguousConcatenation() {
        // Given / When
        var a = StudioIdentity.fromScopedSubject("https://issuer-a", "same-sub", Set.of(), false);
        var b = StudioIdentity.fromScopedSubject("https://issuer-b", "same-sub", Set.of(), false);
        assertThatThrownBy(() -> StudioIdentity.fromScopedSubject("issuer", "\uD800", Set.of(), false))
                .isInstanceOf(IllegalArgumentException.class);
        // Then: group/admin changes do not change the canonical principal.
        assertThat(a.principalId()).isNotEqualTo(b.principalId());
        assertThat(a.principalId()).isEqualTo(StudioIdentity
                .fromScopedSubject("https://issuer-a", "same-sub", Set.of("team"), true).principalId());
        assertThat(StudioIdentity.fromScopedSubject("ab", "c", Set.of(), false).principalId())
                .isNotEqualTo(StudioIdentity.fromScopedSubject("a", "bc", Set.of(), false).principalId());
        assertThatThrownBy(() -> StudioIdentity.fromScopedSubject(" ", "s", Set.of(), false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
