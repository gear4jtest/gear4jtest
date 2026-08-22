package io.github.gear4jtest.external.api.artifact;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@io.github.gear4jtest.core.api.annotation.Internal
public final class ManagedArtifactSpool implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ManagedArtifactSpool.class);

    private final ArtifactSpoolDirectoryRegistry.Lease lease;
    private volatile boolean closed;

    public ManagedArtifactSpool(ArtifactSpoolPolicy policy) throws IOException {
        ArtifactSpoolPolicy effectivePolicy = policy != null ? policy : ArtifactSpoolPolicy.defaults();
        this.lease = ArtifactSpoolDirectoryRegistry.acquire(effectivePolicy);
    }

    public Path createTempFile(String prefix) throws IOException {
        requireOpen();
        return lease.shared().createTempFile(lease.ownerId(), prefix);
    }

    public OutputStream openOutput(Path file) throws IOException {
        requireOpen();
        OutputStream output = lease.shared().openOutput(lease.ownerId(), file);
        return new QuotaOutputStream(output, lease.shared(), file);
    }

    public void copy(InputStream source, Path target) throws IOException {
        Objects.requireNonNull(source, "source must not be null");
        byte[] buffer = new byte[8192];
        try (OutputStream output = openOutput(target)) {
            int read;
            while ((read = source.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }

    public void delete(Path file) {
        if (file == null) {
            return;
        }
        try {
            lease.shared().delete(file);
        } catch (IOException | RuntimeException exception) {
            lease.shared().recordCleanupFailure();
            LOGGER.warn("Unable to delete an artifact spool file.", exception);
        } finally {
            ArtifactSpoolDirectoryRegistry.retireIfUnused(lease.shared());
        }
    }

    public ArtifactSpoolStats snapshotStats() {
        return lease.shared().snapshotStats();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        ArtifactSpoolDirectoryRegistry.release(lease);
    }

    private void requireOpen() throws IOException {
        if (closed) {
            throw new IOException("Artifact spool is closed");
        }
    }

    private static final class QuotaOutputStream extends FilterOutputStream {
        private final ArtifactSpoolDirectoryRegistry.SharedDirectory shared;
        private final Path file;
        private boolean closed;

        QuotaOutputStream(OutputStream output,
                          ArtifactSpoolDirectoryRegistry.SharedDirectory shared,
                          Path file) {
            super(output);
            this.shared = shared;
            this.file = file;
        }

        @Override
        public void write(int value) throws IOException {
            shared.reserve(file, 1L);
            try {
                out.write(value);
            } catch (IOException | RuntimeException exception) {
                shared.reconcileAfterWrite(file);
                throw exception;
            }
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) {
                return;
            }
            shared.reserve(file, length);
            try {
                out.write(buffer, offset, length);
            } catch (IOException | RuntimeException exception) {
                shared.reconcileAfterWrite(file);
                throw exception;
            }
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            try {
                super.close();
            } finally {
                shared.finishOutput(file);
                ArtifactSpoolDirectoryRegistry.retireIfUnused(shared);
            }
        }
    }
}
