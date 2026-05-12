package io.github.gear4jtest.core.api.config;

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

    public static final FlowConfig DEFAULT = new FlowConfig(FailurePolicy.FAIL_FAST, StopPolicy.PROPAGATE_STOP,
            CancelPolicy.PROPAGATE_CANCEL);
}
