package io.github.gear4jtest.core.api.behavior;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.gear4jtest.core.model.StationLogStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SiblingBranchOutcomesDeepCoverageTest {
    @Test
    void of_shouldReturnSingletonEmptyForNullOrEmptyInputs() {
        assertThat(SiblingBranchOutcomes.of(null)).isSameAs(SiblingBranchOutcomes.empty());
        assertThat(SiblingBranchOutcomes.of(Map.of())).isSameAs(SiblingBranchOutcomes.empty());
        assertThat(SiblingBranchOutcomes.empty().branchIds()).isEmpty();
        assertThat(SiblingBranchOutcomes.empty().statusOf("missing")).isEmpty();
    }

    @Test
    void immutableOutcomes_shouldExposeStatusHelpersAndDefensivelyCopyInput() {
        // Given
        Map<String, StationLogStatus> statuses = new LinkedHashMap<>();
        statuses.put("success", StationLogStatus.SUCCEEDED);
        statuses.put("skip", StationLogStatus.SKIPPED);
        statuses.put("fail", StationLogStatus.FAILED);
        statuses.put("stop", StationLogStatus.STOPPED);
        statuses.put("cancel", StationLogStatus.CANCELLED);

        // When
        SiblingBranchOutcomes outcomes = SiblingBranchOutcomes.of(statuses);
        statuses.clear();

        // Then
        assertThat(outcomes.branchIds()).containsExactly("success", "skip", "fail", "stop", "cancel");
        assertThat(outcomes.statusOf("success")).contains(StationLogStatus.SUCCEEDED);
        assertThat(outcomes.isSucceeded("success")).isTrue();
        assertThat(outcomes.isSkipped("skip")).isTrue();
        assertThat(outcomes.isFailed("fail")).isTrue();
        assertThat(outcomes.isStopped("stop")).isTrue();
        assertThat(outcomes.isCancelled("cancel")).isTrue();
        assertThat(outcomes.hasStatus("missing", StationLogStatus.SUCCEEDED)).isFalse();
        assertThatThrownBy(() -> outcomes.branchIds().add("late"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
