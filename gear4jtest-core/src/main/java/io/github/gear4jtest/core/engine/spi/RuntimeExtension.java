package io.github.gear4jtest.core.engine.spi;

/**
 * Contrat pour étendre le comportement du moteur (DryRun, Debug, Premium...).
 */
public interface RuntimeExtension {

    /**
     * Ordre d'application : PLUS PETIT = PLUS EXTERNE (s'exécute en premier).
     * Ex: 0 = Logging/Tracing, 50 = Métier, 100 = Persistance interne.
     */
    default int getOrder() {
        return 50;
    }
}
