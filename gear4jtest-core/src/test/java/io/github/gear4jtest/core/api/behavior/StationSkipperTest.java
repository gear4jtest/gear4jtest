package io.github.gear4jtest.core.api.behavior;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StationSkipperTest {
    @Test
    void preAndPostFactories_shouldExposePhaseReasonAndDecision() {
        // Given
        StationSkipper pre = StationSkipper.pre((input, ctx) -> "skip".equals(input), "pre reason");
        StationSkipper post = StationSkipper.post((input, ctx) -> true, "post reason");
        StationSkipper noReason = StationSkipper.post((input, ctx) -> false);

        // When / Then
        assertThat(pre.phase()).isEqualTo(SkipPhase.PRE_PROCESSORS);
        assertThat(pre.reason()).contains("pre reason");
        assertThat(pre.shouldSkip("skip", null).shouldSkip()).isTrue();
        assertThat(pre.shouldSkip("keep", null).shouldSkip()).isFalse();
        assertThat(pre.shouldSkip("skip", null).reason()).isEqualTo("pre reason");

        assertThat(post.phase()).isEqualTo(SkipPhase.POST_PROCESSORS);
        assertThat(post.reason()).contains("post reason");
        assertThat(post.shouldSkip("anything", null).reason()).isEqualTo("post reason");

        assertThat(noReason.reason()).isEmpty();
        assertThat(noReason.shouldSkip("anything", null)).isSameAs(SkipDecision.dontSkip());
    }

    @Test
    void factories_shouldRejectNullPredicates() {
        assertThatThrownBy(() -> StationSkipper.pre(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("predicate");
        assertThatThrownBy(() -> StationSkipper.pre(null, "reason"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("predicate");
        assertThatThrownBy(() -> StationSkipper.post(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("predicate");
        assertThatThrownBy(() -> StationSkipper.post(null, "reason"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("predicate");
    }
}
