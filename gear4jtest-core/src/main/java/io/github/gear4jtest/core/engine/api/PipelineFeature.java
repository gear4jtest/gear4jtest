package io.github.gear4jtest.core.engine.api;

/**
 * Interface marqueur pour une fonctionnalité activable sur un pipeline.
 * Elle sert de clé pour le registre d'extensions et transporte la configuration (et les services) nécessaires.
 */
public interface PipelineFeature {
    /**
     * La clé unique identifiant cette feature (ex: "PERSISTENCE", "DRY_RUN").
     * Cette clé permet au RunnerStackBuilder de trouver l'extension correspondante.
     */
    String getFeatureKey();
}
