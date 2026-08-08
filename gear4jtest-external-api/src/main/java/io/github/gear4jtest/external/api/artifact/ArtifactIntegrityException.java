package io.github.gear4jtest.external.api.artifact;

import java.io.IOException;

import io.github.gear4jtest.core.api.annotation.Internal;

/** Signals that artifact metadata or content no longer matches its identity. */
@Internal
public final class ArtifactIntegrityException extends IOException {
    private static final long serialVersionUID = 1L;

    public ArtifactIntegrityException(String message) {
        super(message);
    }

    public ArtifactIntegrityException(String message, Throwable cause) {
        super(message, cause);
    }
}
