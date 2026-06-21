package io.github.gear4jtest.core.api.behavior;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.gear4jtest.core.model.StationLogStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SiblingBranchOutcomesTest {
    @Test
    void emptyFactories_shouldExposeNoBranchStatus() {
        assertThat(SiblingBranchOutcomes.empty().branchIds()).isEmpty();
        assertThat(SiblingBranchOutcomes.empty().statusOf("missing")).isEmpty();
        assertThat(SiblingBranchOutcomes.empty().hasStatus("missing", StationLogStatus.SUCCEEDED)).isFalse();
        assertThat(SiblingBranchOutcomes.of(null).branchIds()).isEmpty();
        assertThat(SiblingBranchOutcomes.of(Map.of()).branchIds()).isEmpty();
    }

    @Test
    void of_shouldDefensivelyExposeBranchStatusesAndConveniencePredicates() {
        // Given
        Map<String, StationLogStatus> statuses = new LinkedHashMap<>();
        statuses.put("succeeded", StationLogStatus.SUCCEEDED);
        statuses.put("skipped", StationLogStatus.SKIPPED);
        statuses.put("failed", StationLogStatus.FAILED);
        statuses.put("stopped", StationLogStatus.STOPPED);
        statuses.put("cancelled", StationLogStatus.CANCELLED);

        // When
        SiblingBranchOutcomes outcomes = SiblingBranchOutcomes.of(statuses);
        statuses.put("late", StationLogStatus.RUNNING);

        // Then
        assertThat(outcomes.branchIds()).containsExactly("succeeded", "skipped", "failed", "stopped", "cancelled");
        assertThat(outcomes.statusOf("succeeded")).contains(StationLogStatus.SUCCEEDED);
        assertThat(outcomes.isSucceeded("succeeded")).isTrue();
        assertThat(outcomes.isSkipped("skipped")).isTrue();
        assertThat(outcomes.isFailed("failed")).isTrue();
        assertThat(outcomes.isStopped("stopped")).isTrue();
        assertThat(outcomes.isCancelled("cancelled")).isTrue();
        assertThat(outcomes.isSucceeded("failed")).isFalse();
        assertThat(outcomes.branchIds()).doesNotContain("late");
    }
}
