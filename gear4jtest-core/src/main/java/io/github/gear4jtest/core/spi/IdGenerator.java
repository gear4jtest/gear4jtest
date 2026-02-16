package io.github.gear4jtest.core.spi;

import java.util.UUID;

import io.github.gear4jtest.core.util.DefaultUuidGenerator;

/**
 * Point d'extension pour la génération d'identifiants (Runs, Logs).
 * * L'implémentation par défaut utilise un algorithme UUID v7 Time-Ordered
 * thread-safe et sans dépendance.
 */
@FunctionalInterface
public interface IdGenerator {

    UUID generate();

    /**
     * Retourne le générateur par défaut (UUID v7, ~4M ops/sec).
     */
    static IdGenerator defaultGenerator() {
        return DefaultUuidGenerator::generate;
    }
}