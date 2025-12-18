package io.github.gear4jtest.core.model;

public enum WorkerConcurrencyStrategy {

    /**
     * Si le transformer est déjà en cours d'utilisation, on échoue immédiatement
     * en levant une ConcurrentTransformerUseException.
     */
    FAIL_FAST,

    /**
     * Si le transformer est déjà en cours d'utilisation, on bloque le thread
     * appelant jusqu'à ce qu'il soit à nouveau disponible.
     */
    BLOCK_CALLER,

    /**
     * Aucun verrou n'est pris, le transformer peut être utilisé en parallèle.
     * À n'utiliser que si le transformer est réellement thread-safe / stateless.
     */
    IGNORE
}
