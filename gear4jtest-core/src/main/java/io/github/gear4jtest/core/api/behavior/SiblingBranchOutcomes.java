package io.github.gear4jtest.core.api.behavior;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.github.gear4jtest.core.persistence.StationLog;

public interface SiblingBranchOutcomes {

    Optional<StationLog.Status> statusOf(String branchId);

    Set<String> branchIds();

    default boolean hasStatus(String branchId, StationLog.Status expected) {
        return statusOf(branchId).map(expected::equals).orElse(false);
    }

    default boolean isSucceeded(String branchId) {
        return hasStatus(branchId, StationLog.Status.SUCCEEDED);
    }

    default boolean isSkipped(String branchId) {
        return hasStatus(branchId, StationLog.Status.SKIPPED);
    }

    default boolean isFailed(String branchId) {
        return hasStatus(branchId, StationLog.Status.FAILED);
    }

    default boolean isStopped(String branchId) {
        return hasStatus(branchId, StationLog.Status.STOPPED);
    }

    default boolean isCancelled(String branchId) {
        return hasStatus(branchId, StationLog.Status.CANCELLED);
    }

    static SiblingBranchOutcomes empty() {
        return ImmutableSiblingBranchOutcomes.EMPTY;
    }

    static SiblingBranchOutcomes of(Map<String, StationLog.Status> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return empty();
        }
        return new ImmutableSiblingBranchOutcomes(statuses);
    }

    final class ImmutableSiblingBranchOutcomes implements SiblingBranchOutcomes {
        private static final ImmutableSiblingBranchOutcomes EMPTY = new ImmutableSiblingBranchOutcomes(Map.of());

        private final Map<String, StationLog.Status> statuses;

        private ImmutableSiblingBranchOutcomes(Map<String, StationLog.Status> statuses) {
            this.statuses = Collections.unmodifiableMap(new LinkedHashMap<>(statuses));
        }

        @Override
        public Optional<StationLog.Status> statusOf(String branchId) {
            return Optional.ofNullable(statuses.get(branchId));
        }

        @Override
        public Set<String> branchIds() {
            return statuses.keySet();
        }
    }
}
