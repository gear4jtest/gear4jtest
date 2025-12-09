package io.github.gear4jtest.core.execution;

import java.util.List;
import java.util.UUID;

import io.github.gear4jtest.core.persistence.OperationExecutionRecord;
import io.github.gear4jtest.core.persistence.PipelineExecution;

public interface PipelineExecutionManager {

    void start(PipelineExecution execution);

    /**
     * Append d'un seul OperationExecutionRecord.
     * En DB : typiquement ajouté à un buffer, pas forcément inséré immédiatement.
     */
    default void append(OperationExecutionRecord record) {
        // no-op par défaut
    }

    /**
     * Append d'une liste de records (si un orchestrateur veut pousser un batch
     * déjà prêt). Par défaut, boucle sur append(...).
     */
    default void appendAll(List<OperationExecutionRecord> records) {
        if (records != null) {
            records.forEach(this::append);
        }
    }

    /**
     * Append d'un éventuel record agrégé (ex: batch d'iterator).
     * Non utilisé pour le moment, mais laissé pour extension future.
     */
    default void append(IteratorBatch batch) {
        // no-op par défaut
    }

    /**
     * Heartbeat éventuel (non utilisé actuellement, conservé pour compat).
     */
    default void heartbeat(UUID pipelineId) {
        // no-op
    }

    /**
     * Flush explicite d'un pipeline (écrit les buffers en DB).
     */
    default void flush(UUID pipelineId) {
        // no-op par défaut
    }

    /**
     * Fin d'exécution : flush final + mise à jour de l'exécution.
     */
    void end(PipelineExecution finalExecution);

    /**
     * Shutdown global du manager (fermeture des ressources, flush global...).
     */
    default void shutdown() {
        // no-op par défaut
    }
}
