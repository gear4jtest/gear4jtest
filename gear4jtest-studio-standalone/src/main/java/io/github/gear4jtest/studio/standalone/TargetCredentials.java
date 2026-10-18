package io.github.gear4jtest.studio.standalone;

import io.github.gear4jtest.studio.management.StudioIdentity;

/**
 * Host token exchange/delegation. Return a bearer token for this authenticated
 * actor on this allowlisted target, never an elevated service-account token.
 * Remote authentication must resolve the same subject; groups/permissions are
 * recomputed there. Tokens are transient, never logged or sent to the browser.
 */
@FunctionalInterface
public interface TargetCredentials {
    String tokenFor(StudioIdentity actor, StudioTarget target, String incomingBearerToken);
}
