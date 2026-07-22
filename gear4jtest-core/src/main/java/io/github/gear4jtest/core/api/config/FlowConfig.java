package io.github.gear4jtest.core.api.config;

import java.util.Objects;

/**
 * Flow policy used by container-like stations such as sequences, iterators and
 * parallel containers.
 *
 * <p>
 * A station may override this configuration. Otherwise the execution falls back
 * to the run-level default.
 * </p>
 */
public record FlowConfig(FailurePolicy failurePolicy, StopPolicy stopPolicy, CancelPolicy cancelPolicy) {
    public FlowConfig {
        Objects.requireNonNull(failurePolicy, "failurePolicy must not be null");
        Objects.requireNonNull(stopPolicy, "stopPolicy must not be null");
        Objects.requireNonNull(cancelPolicy, "cancelPolicy must not be null");
    }

    public static final FlowConfig DEFAULT = new FlowConfig(FailurePolicy.FAIL_FAST, StopPolicy.PROPAGATE_STOP,
            CancelPolicy.PROPAGATE_CANCEL);
}
