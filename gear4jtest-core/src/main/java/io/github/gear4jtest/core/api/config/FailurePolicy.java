package io.github.gear4jtest.core.api.config;

/** Politique à appliquer quand une station enfant termine en {@code FAILED}. */
public enum FailurePolicy {
    /** Arrête immédiatement l'orchestration et propage l'échec (défaut). */
    FAIL_FAST,
    /** Ignore l'échec, conserve l'input précédent et continue. */
    IGNORE_AND_CONTINUE,
    /** Continue l'exécution, collecte les erreurs, puis échoue à la fin. */
    COLLECT_AND_FAIL
}
