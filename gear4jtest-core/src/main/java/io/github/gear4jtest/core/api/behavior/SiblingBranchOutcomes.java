package io.github.gear4jtest.core.api.behavior;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.github.gear4jtest.core.model.StationLogStatus;

public interface SiblingBranchOutcomes {

    static SiblingBranchOutcomes empty() {
        return ImmutableSiblingBranchOutcomes.EMPTY;
    }

    static SiblingBranchOutcomes of(Map<String, StationLogStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return empty();
        }
        return new ImmutableSiblingBranchOutcomes(statuses);
    }

    Optional<StationLogStatus> statusOf(String branchId);

    Set<String> branchIds();

    default boolean hasStatus(String branchId, StationLogStatus expected) {
        return statusOf(branchId).map(expected::equals).orElse(false);
    }

    default boolean isSucceeded(String branchId) {
        return hasStatus(branchId, StationLogStatus.SUCCEEDED);
    }

    default boolean isSkipped(String branchId) {
        return hasStatus(branchId, StationLogStatus.SKIPPED);
    }

    default boolean isFailed(String branchId) {
        return hasStatus(branchId, StationLogStatus.FAILED);
    }

    default boolean isStopped(String branchId) {
        return hasStatus(branchId, StationLogStatus.STOPPED);
    }

    default boolean isCancelled(String branchId) {
        return hasStatus(branchId, StationLogStatus.CANCELLED);
    }

    final class ImmutableSiblingBranchOutcomes implements SiblingBranchOutcomes {
        private static final ImmutableSiblingBranchOutcomes EMPTY = new ImmutableSiblingBranchOutcomes(Map.of());

        private final Map<String, StationLogStatus> statuses;

        private ImmutableSiblingBranchOutcomes(Map<String, StationLogStatus> statuses) {
            this.statuses = Collections.unmodifiableMap(new LinkedHashMap<>(statuses));
        }

        @Override
        public Optional<StationLogStatus> statusOf(String branchId) {
            return Optional.ofNullable(statuses.get(branchId));
        }

        @Override
        public Set<String> branchIds() {
            return statuses.keySet();
        }
    }
}
