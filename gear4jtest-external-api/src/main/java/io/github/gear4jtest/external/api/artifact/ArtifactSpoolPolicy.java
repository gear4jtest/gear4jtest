package io.github.gear4jtest.external.api.artifact;

import java.nio.file.Path;
import java.time.Duration;

/** Configuration for the private temporary artifact spool. */
public final class ArtifactSpoolPolicy {
    public static final long DEFAULT_MAX_BYTES = 100L * 1024L * 1024L;
    public static final Duration DEFAULT_STALE_FILE_AGE = Duration.ofHours(24);
    public static final boolean DEFAULT_REQUIRE_PRIVATE_PERMISSIONS = true;

    private final Path directory;
    private final long maxBytes;
    private final Duration staleFileAge;
    private final boolean requirePrivatePermissions;

    private ArtifactSpoolPolicy(Builder builder) {
        this.directory = builder.directory;
        this.maxBytes = validateMaxBytes(builder.maxBytes);
        this.staleFileAge = validateStaleFileAge(builder.staleFileAge);
        this.requirePrivatePermissions = builder.requirePrivatePermissions;
    }

    public static ArtifactSpoolPolicy defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return builder().directory(directory)
                .maxBytes(maxBytes)
                .staleFileAge(staleFileAge)
                .requirePrivatePermissions(requirePrivatePermissions);
    }

    public Path directory() {
        return directory;
    }

    public long maxBytes() {
        return maxBytes;
    }

    public Duration staleFileAge() {
        return staleFileAge;
    }

    /**
     * Whether spool initialization must fail when owner-only permissions cannot be
     * applied and verified.
     *
     * @return {@code true} by default
     */
    public boolean requirePrivatePermissions() {
        return requirePrivatePermissions;
    }

    private static long validateMaxBytes(long maxBytes) {
        if (maxBytes == 0 || maxBytes < ArtifactStore.UNLIMITED_SIZE) {
            throw new IllegalArgumentException("spool maxBytes must be > 0 or UNLIMITED_SIZE");
        }
        return maxBytes;
    }

    private static Duration validateStaleFileAge(Duration staleFileAge) {
        if (staleFileAge == null || staleFileAge.isZero() || staleFileAge.isNegative()) {
            throw new IllegalArgumentException("spool staleFileAge must be > 0");
        }
        return staleFileAge;
    }

    public static final class Builder {
        private Path directory;
        private long maxBytes = DEFAULT_MAX_BYTES;
        private Duration staleFileAge = DEFAULT_STALE_FILE_AGE;
        private boolean requirePrivatePermissions = DEFAULT_REQUIRE_PRIVATE_PERMISSIONS;

        private Builder() {
        }

        public Builder directory(Path directory) {
            this.directory = directory;
            return this;
        }

        public Builder maxBytes(long maxBytes) {
            this.maxBytes = maxBytes;
            return this;
        }

        public Builder staleFileAge(Duration staleFileAge) {
            this.staleFileAge = staleFileAge;
            return this;
        }

        /**
         * Requires verifiable owner-only POSIX permissions or an owner-only ACL for the
         * spool directory and files. Disable this only when the configured directory is
         * protected outside Gear4J.
         *
         * @param requirePrivatePermissions whether startup must fail closed when
         *                                  privacy cannot be verified
         * @return this builder
         */
        public Builder requirePrivatePermissions(boolean requirePrivatePermissions) {
            this.requirePrivatePermissions = requirePrivatePermissions;
            return this;
        }

        public ArtifactSpoolPolicy build() {
            return new ArtifactSpoolPolicy(this);
        }
    }
}
