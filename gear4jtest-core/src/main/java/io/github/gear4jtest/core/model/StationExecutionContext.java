package io.github.gear4jtest.core.model;

import java.util.Optional;

import io.github.gear4jtest.core.engine.support.ExecutionSupport;
import io.github.gear4jtest.core.persistence.StationLog;

/**
 * Contexte runtime d'une exécution d'opération.
 * - Porte l'identité de l'opération
 * - Le type logique (kind)
 * - Le contexte global du pipeline
 * - Le record d'exécution (trace)
 * - Des "capabilities" typées pour les besoins spécifiques (transformer, params, etc.)
 */
public interface StationExecutionContext {

    String getOperationId();

    StationKind getKind();

    ExecutionContext getGlobalContext();

    StationLog getRecord();

    ExecutionSupport getSupport();

    /**
     * Capabilities typées, optionnelles, utilisées pour les besoins spécifiques
     * (transformer, paramètres d'injection, profils, etc.).
     */
    <T> Optional<T> getCapability(Class<T> type);

    ResolvedParameters getResolvedParameters();
}
