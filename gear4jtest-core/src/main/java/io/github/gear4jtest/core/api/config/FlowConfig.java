package io.github.gear4jtest.core.api.config;

/**
 * Configuration de flux pour une station "conteneur" (sequence, iterator, parallel, ...).
 *
 * <p>La station peut surcharger cette config; sinon on utilise un défaut au niveau du run.
 */
public record FlowConfig(
    FailurePolicy failurePolicy,
    StopPolicy stopPolicy,
    CancelPolicy cancelPolicy
) {

    public static final FlowConfig DEFAULT = new FlowConfig(
        FailurePolicy.FAIL_FAST,
        StopPolicy.PROPAGATE_STOP,
        CancelPolicy.PROPAGATE_CANCEL
    );
}
