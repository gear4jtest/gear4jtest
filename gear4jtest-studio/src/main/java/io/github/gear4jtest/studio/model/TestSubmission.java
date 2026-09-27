package io.github.gear4jtest.studio.model;

import java.util.UUID;

public record TestSubmission(UUID requestId, State state, ControlledRun run, String errorCode) {
    public enum State {
        RUNNING, COMPLETED, PREPARATION_FAILED, OUTCOME_UNKNOWN
    }
}
