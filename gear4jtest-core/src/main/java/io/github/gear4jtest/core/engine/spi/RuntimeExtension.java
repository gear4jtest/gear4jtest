package io.github.gear4jtest.core.engine.spi;

import io.github.gear4jtest.core.engine.core.RunRequest;
import io.github.gear4jtest.core.model.ExecutionContext;

/**
 * Contrat pour étendre le comportement du moteur (DryRun, Debug, Premium...).
 */
public interface RuntimeExtension {

    /**
     * PHASE 1 : BOOTSTRAP
     * L'extension initialise ses ressources (Bus dynamiques, Managers...)
     * et les injecte dans le contexte.
     */
    void prepare(ExecutionContext ctx, RunRequest request);

    /**
     * PHASE 2 : ASSEMBLY
     * L'extension enrobe le runner actuel (Chain of Responsibility).
     * @param current Le runner qui est "en dessous" dans la pile.
     * @return Le nouveau runner qui enveloppe le précédent.
     */
    StationRunner decorate(StationRunner current, ExecutionContext ctx);

    /**
     * Juste avant le run.
     */
    default void onStart(ExecutionContext ctx) {}

    /**
     * Si le run se termine sans exception (le résultat métier est dispo).
     */
    default void onSuccess(ExecutionContext ctx, Object result) {}

    /**
     * Si le run crash (Exception non gérée).
     */
    default void onFailure(ExecutionContext ctx, Throwable error) {}

    /**
     * Toujours appelé à la fin (dans le finally), après success/failure.
     * Idéal pour le nettoyage ou le commit final.
     */
    default void onEnd(ExecutionContext ctx) {}
}