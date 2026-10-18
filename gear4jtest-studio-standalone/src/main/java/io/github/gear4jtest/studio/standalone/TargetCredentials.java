package io.github.gear4jtest.studio.standalone;

import io.github.gear4jtest.studio.management.StudioIdentity;

/**
 * Host token exchange/delegation. Return a bearer token for this authenticated
 * actor on this allowlisted target, never an elevated service-account token.
 * Remote authentication must resolve the same canonical principalId;
 * groups/permissions are recomputed there. Tokens are transient, never logged
 * or sent to the browser. This synchronous SPI MUST have a host-enforced
 * latency bound (including token exchange I/O), and must honor interruption.
 * HTTP deadlines start after it returns; they cannot safely stop arbitrary host
 * code. Calls hold both global and per-target permits, so a stalled provider is
 * contained per target. Prefer a local, bounded token cache; never perform
 * unbounded network I/O here.
 */
@FunctionalInterface
public interface TargetCredentials {
    String tokenFor(StudioIdentity actor, StudioTarget target, String incomingBearerToken);
}
