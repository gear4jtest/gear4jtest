package io.github.gear4jtest.core.model.refactor;

import java.util.Optional;

import io.github.gear4jtest.core.persistence.OperationExecutionRecord;

/**
 * Contexte runtime d'une exécution d'opération.
 * - Porte l'identité de l'opération
 * - Le type logique (kind)
 * - Le contexte global du pipeline
 * - Le record d'exécution (trace)
 * - Des "capabilities" typées pour les besoins spécifiques (transformer, params, etc.)
 */
public interface OperationExecutionContext {

    String getOperationId();

    OperationKind getKind();

    ExecutionContext getGlobalContext();

    OperationExecutionRecord getRecord();

    /**
     * Capabilities typées, optionnelles, utilisées pour les besoins spécifiques
     * (transformer, paramètres d'injection, profils, etc.).
     */
    <T> Optional<T> getCapability(Class<T> type);
}
