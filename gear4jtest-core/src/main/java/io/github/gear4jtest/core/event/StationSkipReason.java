package io.github.gear4jtest.core.event;

import io.github.gear4jtest.core.api.annotation.PublicApi;

@PublicApi
public enum StationSkipReason {
    CONDITION_NOT_SATISFIED,
    SIBLING_CONDITION_NOT_SATISFIED
}
