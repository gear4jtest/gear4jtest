package io.github.gear4jtest.external.api.exception;

/**
 * Raised before generated source or bytecode can exceed a configured hard
 * compilation limit.
 */
public final class CompilationLimitExceededException extends CompilationException {
    private final String resource;
    private final long actualBytes;
    private final long maxBytes;

    public CompilationLimitExceededException(String resource, long actualBytes, long maxBytes) {
        super(resource + " exceeds hard limit: " + actualBytes + " bytes > " + maxBytes + " bytes");
        this.resource = resource;
        this.actualBytes = actualBytes;
        this.maxBytes = maxBytes;
    }

    public String resource() {
        return resource;
    }

    public long actualBytes() {
        return actualBytes;
    }

    public long maxBytes() {
        return maxBytes;
    }
}
