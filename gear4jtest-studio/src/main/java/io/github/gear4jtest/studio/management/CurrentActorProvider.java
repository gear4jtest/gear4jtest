package io.github.gear4jtest.studio.management;

/**
 * Integrates the host authentication context; absence of an actor fails closed.
 */
@FunctionalInterface
public interface CurrentActorProvider {
    StudioIdentity currentActor();
}
