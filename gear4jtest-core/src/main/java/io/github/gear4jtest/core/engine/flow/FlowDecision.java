package io.github.gear4jtest.core.engine.flow;

/**
 * Décision de contrôle de flux à appliquer à un résultat enfant.
 *
 * <ul>
 *   <li>{@link #PROCEED} : on continue normalement</li>
 *   <li>{@link #INTERRUPT} : on interrompt immédiatement (stop/fail/cancel propagé)</li>
 *   <li>{@link #MARK_AND_PROCEED} : on note un échec (collect) mais on continue</li>
 * </ul>
 */
public enum FlowDecision {
    PROCEED,
    INTERRUPT,
    MARK_AND_PROCEED
}
