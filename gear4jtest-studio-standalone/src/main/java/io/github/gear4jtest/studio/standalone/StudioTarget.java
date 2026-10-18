package io.github.gear4jtest.studio.standalone;

import java.net.URI;

/**
 * Explicit endpoint binding. Secrets, credentials and execution mode are not
 * target metadata.
 */
public record StudioTarget(String id, String applicationId, String name, String runtimeContextId, URI endpoint) {
    public StudioTarget {
        TargetDirectory.identifier(id);
        TargetDirectory.identifier(applicationId);
        TargetDirectory.label(name);
        if (runtimeContextId == null || !runtimeContextId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
                || endpoint == null || endpoint.getHost() == null || endpoint.getRawUserInfo() != null
                || endpoint.getRawQuery() != null || endpoint.getRawFragment() != null
                || !endpoint.getRawPath().matches("(/[A-Za-z0-9_-]+)*")
                || !("https".equals(endpoint.getScheme()) || "http".equals(endpoint.getScheme())
                        && "127.0.0.1".equals(endpoint.getHost())))
            throw new IllegalArgumentException("Invalid management target binding");
    }
}
