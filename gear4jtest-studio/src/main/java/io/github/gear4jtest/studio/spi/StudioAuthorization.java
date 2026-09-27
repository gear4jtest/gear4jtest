package io.github.gear4jtest.studio.spi;

/**
 * The host resolves the authenticated actor; never accept it from an untrusted
 * request body.
 */
@FunctionalInterface
public interface StudioAuthorization {
    void require(String actor, Action action);

    enum Action {
        READ, EDIT, VALIDATE, TEST
    }
}
