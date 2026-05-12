package io.github.gear4jtest.core.persistence;

public enum ExecutionStatus {
    // --- PHASE : EN ATTENTE / DÉMARRAGE ---
    PENDING(StatusCategory.ACTIVE), INITIALIZING(StatusCategory.ACTIVE),

    // --- PHASE : EN COURS ---
    RUNNING(StatusCategory.ACTIVE), PAUSED(StatusCategory.ACTIVE), // On considère que "Pause" est un état actif
    // (non-fini)

    // --- PHASE : TERMINÉ (Succès) ---
    SUCCEEDED(StatusCategory.TERMINAL),

    // --- PHASE : TERMINÉ (Arrêts & Erreurs) ---
    FAILED(StatusCategory.TERMINAL), STOPPED(StatusCategory.TERMINAL), CANCELLED(StatusCategory.TERMINAL),
    SKIPPED(StatusCategory.TERMINAL);

    // --------------------------------------------------------
    // Le Champ Final (La Source de Vérité)
    // --------------------------------------------------------

    private final StatusCategory category;

    ExecutionStatus(StatusCategory category) {
        this.category = category;
    }

    // --------------------------------------------------------
    // Les Helpers (L'API Publique)
    // --------------------------------------------------------

    /**
     * Est-ce que le run est fini ?
     */
    public boolean isTerminal() {
        return this.category == StatusCategory.TERMINAL;
    }

    /**
     * Est-ce que le run est vivant ? (En cours, en pause ou en attente)
     */
    public boolean isActive() {
        return this.category == StatusCategory.ACTIVE;
    }

    /**
     * Est-ce un succès franc ?
     */
    public boolean isSuccess() {
        return this == SUCCEEDED;
    }

    /**
     * Est-ce que ça s'est mal passé ? (Technique ou Timeout) Utile pour le
     * monitoring / alerting.
     */
    public boolean isError() {
        return this == FAILED || this == CANCELLED;
    }

    /**
     * Est-ce un arrêt fonctionnel volontaire ?
     */
    public boolean isStopped() {
        return this == STOPPED;
    }

    public enum StatusCategory {
        /**
         * Le processus est vivant. Il consomme des ressources (RUNNING) ou attend de le
         * faire (PENDING/PAUSED). Il n'a pas encore produit de résultat final.
         */
        ACTIVE,

        /**
         * Le processus est fini. Il ne changera plus d'état. Il a un résultat (Succès,
         * Echec ou Stop).
         */
        TERMINAL
    }
}
