package io.github.gear4jtest.core.event;

import io.github.gear4jtest.core.api.annotation.PublicApi;

@PublicApi
public enum StationCancellationReason {
    COOPERATIVE_CANCELLATION,
    TIMEOUT,
    UNEXPECTED_WAIT_INTERRUPTION
}
