package io.github.gear4jtest.core.execution;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.github.gear4jtest.core.model.ExecutionContext;

/**
 * Registry simple pour mapper un executionId (String) vers un ExecutionContext.
 * À alimenter au moment du démarrage d'une exécution de pipeline, et à nettoyer
 * une fois l'exécution terminée.
 */
public final class ExecutionContextRegistry {

    private final ConcurrentMap<UUID, ExecutionContext> contexts = new ConcurrentHashMap<>();

    /**
     * Enregistre un ExecutionContext pour un executionId donné.
     */
    public void register(ExecutionContext ctx) {
        if (ctx == null || ctx.getExecutionId() == null) {
            return;
        }
        contexts.put(ctx.getExecutionId(), ctx);
    }

    /**
     * Récupère le contexte associé à l'executionId, ou null si absent.
     */
    public ExecutionContext get(UUID executionId) {
        if (executionId == null) {
            return null;
        }
        return contexts.get(executionId);
    }

    /**
     * Supprime le contexte associé à l'executionId.
     * À appeler lorsqu'une exécution de pipeline est terminée.
     */
    public void remove(UUID executionId) {
        if (executionId == null) {
            return;
        }
        contexts.remove(executionId);
    }
}
