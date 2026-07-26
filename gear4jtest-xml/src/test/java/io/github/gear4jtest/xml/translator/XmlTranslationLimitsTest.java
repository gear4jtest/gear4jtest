package io.github.gear4jtest.xml.translator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XmlTranslationLimitsTest {
    @Test
    void defaults_shouldProvideFiniteLimitsAlignedWithCompilationSourceBudget() {
        XmlTranslationLimits limits = XmlTranslationLimits.defaults();

        assertThat(limits.maxOperations()).isEqualTo(1_000);
        assertThat(limits.maxDependencies()).isEqualTo(256);
        assertThat(limits.maxNestingDepth()).isEqualTo(32);
        assertThat(limits.maxGeneratedSourceBytes()).isEqualTo(4L * 1024L * 1024L);
    }

    @Test
    void constructor_shouldRejectNonPositiveLimits() {
        assertThatThrownBy(() -> new XmlTranslationLimits(0, 1, 1, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxOperations");
        assertThatThrownBy(() -> new XmlTranslationLimits(1, 0, 1, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxDependencies");
        assertThatThrownBy(() -> new XmlTranslationLimits(1, 1, 0, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxNestingDepth");
        assertThatThrownBy(() -> new XmlTranslationLimits(1, 1, 1, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxGeneratedSourceBytes");
    }
}
