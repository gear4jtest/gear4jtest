package io.github.gear4jtest.core.sidecompute;

import java.util.function.Function;

import io.github.gear4jtest.core.event.OperationCompletedEvent;

/**
 * Déclaration d'un traitement side compute, déclenché par le succès d'une
 * opération donnée.
 *
 * @param <R> type du résultat produit par ce side compute.
 */
public final class SideComputer<R> {

    private final String operationId;
    private final String key;
    private final Function<OperationCompletedEvent, R> computer;

    private SideComputer(String operationId,
                         String key,
                         Function<OperationCompletedEvent, R> computer) {
        this.operationId = operationId;
        this.key = key;
        this.computer = computer;
    }

    public static <R> SideComputer<R> of(String operationId,
                                         String key,
                                         Function<OperationCompletedEvent, R> computer) {
        return new SideComputer<>(operationId, key, computer);
    }

    public boolean matches(OperationCompletedEvent ev) {
        return ev != null && operationId.equals(ev.getOperationId());
    }

    public String key() {
        return key;
    }

    public Function<OperationCompletedEvent, R> computer() {
        return computer;
    }
}
