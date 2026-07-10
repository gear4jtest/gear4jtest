package io.github.gear4jtest.external.jdbc.artifact;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Objects;

final class JdbcArtifactInputStream extends FilterInputStream {
    private final ResultSet resultSet;
    private final PreparedStatement statement;
    private final Connection connection;
    private final String hash;
    private final long expectedSize;
    private final long maxSize;
    private final ArtifactStoreMetrics metrics;
    private final long startedNanos;
    private long bytesRead;
    private boolean closed;
    private boolean failed;

    JdbcArtifactInputStream(InputStream content,
                            ResultSet resultSet,
                            PreparedStatement statement,
                            Connection connection,
                            String hash,
                            long expectedSize,
                            long maxSize,
                            ArtifactStoreMetrics metrics,
                            long startedNanos) {
        super(content);
        this.resultSet = resultSet;
        this.statement = statement;
        this.connection = connection;
        this.hash = hash;
        this.expectedSize = expectedSize;
        this.maxSize = maxSize;
        this.metrics = metrics;
        this.startedNanos = startedNanos;
    }

    @Override
    public int read() throws IOException {
        ensureOpen();
        try {
            int value = super.read();
            if (value == -1) {
                handleEndOfStream();
                return -1;
            }
            recordRead(1L);
            return value;
        } catch (IOException | RuntimeException exception) {
            fail(exception);
            throw exception;
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, buffer.length);
        ensureOpen();
        if (length == 0) {
            return 0;
        }
        try {
            int read = super.read(buffer, offset, length);
            if (read == -1) {
                handleEndOfStream();
                return -1;
            }
            recordRead(read);
            return read;
        } catch (IOException | RuntimeException exception) {
            fail(exception);
            throw exception;
        }
    }

    @Override
    public long skip(long requested) throws IOException {
        if (requested <= 0) {
            return 0L;
        }
        byte[] buffer = new byte[(int) Math.min(8192L, requested)];
        long skipped = 0L;
        while (skipped < requested) {
            int read = read(buffer, 0, (int) Math.min(buffer.length, requested - skipped));
            if (read == -1) {
                break;
            }
            skipped += read;
        }
        return skipped;
    }

    private void recordRead(long count) throws IOException {
        long nextCount;
        try {
            nextCount = Math.addExact(bytesRead, count);
        } catch (ArithmeticException exception) {
            throw new IOException("Database artifact stream size overflow for " + hash, exception);
        }
        if (nextCount > expectedSize || maxSize >= 0 && nextCount > maxSize) {
            throw new IOException("Database artifact content exceeds declared or configured size. hash=" + hash
                    + ", declaredSize=" + expectedSize + ", maxBytes=" + maxSize);
        }
        bytesRead = nextCount;
    }

    private void handleEndOfStream() throws IOException {
        if (bytesRead != expectedSize) {
            throw new IOException("Database artifact content is shorter than its declared size. hash=" + hash
                    + ", declaredSize=" + expectedSize + ", actualSize=" + bytesRead);
        }
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Database artifact stream is closed. hash=" + hash);
        }
    }

    private void fail(Throwable failure) {
        failed = true;
        try {
            closeResources(failure);
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    @Override
    public void close() throws IOException {
        closeResources(null);
    }

    private void closeResources(Throwable primaryFailure) throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        IOException closeFailure = null;
        closeFailure = closeResource(in, closeFailure, primaryFailure);
        closeFailure = closeResource(resultSet, closeFailure, primaryFailure);
        closeFailure = closeResource(statement, closeFailure, primaryFailure);
        closeFailure = closeResource(connection, closeFailure, primaryFailure);
        boolean completed = bytesRead == expectedSize && !failed && closeFailure == null;
        metrics.recordReadClosed(bytesRead, ArtifactStoreMetrics.elapsedSince(startedNanos), completed,
                                 bytesRead < expectedSize && primaryFailure == null && closeFailure == null,
                                 failed || closeFailure != null);
        if (primaryFailure == null && closeFailure != null) {
            throw closeFailure;
        }
    }

    private static IOException closeResource(AutoCloseable resource,
                                             IOException currentFailure,
                                             Throwable primaryFailure) {
        if (resource == null) {
            return currentFailure;
        }
        try {
            resource.close();
        } catch (Exception exception) {
            if (primaryFailure != null) {
                primaryFailure.addSuppressed(exception);
            } else if (currentFailure == null) {
                currentFailure = exception instanceof IOException ioException ? ioException
                        : new IOException("Failed to close database artifact stream resource", exception);
            } else {
                currentFailure.addSuppressed(exception);
            }
        }
        return currentFailure;
    }
}
