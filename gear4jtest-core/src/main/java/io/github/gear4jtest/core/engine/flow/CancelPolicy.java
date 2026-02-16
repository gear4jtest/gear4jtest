package io.github.gear4jtest.core.engine.flow;

/** Politique à appliquer quand une station enfant termine en {@code CANCELLED}. */
public enum CancelPolicy {
    /** Arrête et propage l'annulation (défaut). */
    PROPAGATE_CANCEL,
    /** Ignore l'annulation et continue (rare). */
    IGNORE_AND_CONTINUE,
    /** Transforme une annulation en échec (FAILED). */
    TREAT_AS_FAILURE
}
