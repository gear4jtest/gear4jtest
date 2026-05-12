package io.github.gear4jtest.core.api.config;

/**
 * Politique à appliquer quand une station enfant termine en {@code STOPPED}.
 */
public enum StopPolicy {
    /** Arrête et propage le STOP (défaut). */
    PROPAGATE_STOP,
    /** Ignore le STOP, conserve l'input précédent et continue. */
    IGNORE_AND_CONTINUE,
    /** Transforme un STOP en échec (FAILED). */
    TREAT_AS_FAILURE
}
