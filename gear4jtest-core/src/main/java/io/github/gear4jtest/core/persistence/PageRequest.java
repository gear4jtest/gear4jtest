package io.github.gear4jtest.core.persistence;

/** Zero-based bounded query window for persistence views. */
public record PageRequest(int offset, int limit) {
    public static final int MAX_LIMIT = 1_000;

    public PageRequest {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }
        if (limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be <= " + MAX_LIMIT);
        }
    }

    public static PageRequest first(int limit) {
        return new PageRequest(0, limit);
    }
}
