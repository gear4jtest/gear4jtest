package io.github.gear4jtest.external.jdbc.artifact;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;

import io.github.gear4jtest.external.api.artifact.Artifact;
import io.github.gear4jtest.external.api.artifact.ArtifactHashes;
import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.artifact.ArtifactStoreStats;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DatabaseArtifactStoreStreamingTest {
    private static final String HASH = ArtifactHashes.sha256Hex("abc".getBytes(StandardCharsets.UTF_8));

    @TempDir
    Path tempDirectory;

    @Test
    void get_shouldOpenJdbcContentLazilyAndCloseEveryOwnedResource() throws Exception {
        // Given
        byte[] bytes = "abc".getBytes(StandardCharsets.UTF_8);
        ReadJdbc jdbc = readJdbc(bytes.length, new ByteArrayInputStream(bytes));
        DatabaseArtifactStore store = store(jdbc.dataSource(), 10);

        // When
        Artifact artifact = store.get(HASH).orElseThrow();

        // Then
        assertThat(artifact.size()).isEqualTo(bytes.length);
        verify(jdbc.dataSource(), times(1)).getConnection();
        verify(jdbc.metadataResultSet()).close();
        verify(jdbc.metadataStatement()).close();
        verify(jdbc.metadataConnection()).close();
        verify(jdbc.contentResultSet(), never()).getBinaryStream(2);

        // When
        try (InputStream input = artifact.openStreamChecked()) {
            assertThat(input.readAllBytes()).isEqualTo(bytes);
        }

        // Then
        verify(jdbc.dataSource(), times(2)).getConnection();
        verify(jdbc.contentResultSet()).getBinaryStream(2);
        verify(jdbc.contentResultSet()).close();
        verify(jdbc.contentStatement()).close();
        verify(jdbc.contentConnection()).close();
        verify(jdbc.contentResultSet(), never()).getBytes(2);
        ArtifactStoreStats stats = store.snapshotStats();
        assertThat(stats.readStreamsOpened()).isEqualTo(1);
        assertThat(stats.readStreamsCompleted()).isEqualTo(1);
        assertThat(stats.bytesRead()).isEqualTo(bytes.length);
    }

    @Test
    void stream_shouldAllowRepeatedEndOfStreamReadsAfterDigestVerification() throws Exception {
        // Given
        byte[] bytes = "abc".getBytes(StandardCharsets.UTF_8);
        ReadJdbc jdbc = readJdbc(bytes.length, new ByteArrayInputStream(bytes));
        DatabaseArtifactStore store = store(jdbc.dataSource(), 10);

        // When / Then
        try (InputStream input = store.get(HASH).orElseThrow().openStreamChecked()) {
            assertThat(input.readAllBytes()).isEqualTo(bytes);
            assertThat(input.read()).isEqualTo(-1);
            assertThat(input.read()).isEqualTo(-1);
        }
        assertThat(store.snapshotStats().readStreamsCompleted()).isEqualTo(1);
    }

    @Test
    void get_shouldRejectOversizedMetadataBeforeOpeningBlobContent() throws Exception {
        // Given
        ReadJdbc jdbc = readJdbc(6, new ByteArrayInputStream(new byte[6]));
        DatabaseArtifactStore store = store(jdbc.dataSource(), 5);

        // When / Then
        assertThatThrownBy(() -> store.get(HASH))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("exceeds configured read limit");
        verify(jdbc.dataSource(), times(1)).getConnection();
        verify(jdbc.contentResultSet(), never()).getBinaryStream(2);
        assertThat(store.snapshotStats().readFailures()).isEqualTo(1);
    }

    @Test
    void stream_shouldReportEarlyCloseWithoutMaterializingRemainingContent() throws Exception {
        // Given
        ReadJdbc jdbc = readJdbc(10, new ByteArrayInputStream(new byte[10]));
        DatabaseArtifactStore store = store(jdbc.dataSource(), 10);
        Artifact artifact = store.get(HASH).orElseThrow();

        // When
        try (InputStream input = artifact.openStreamChecked()) {
            assertThat(input.readNBytes(3)).hasSize(3);
        }

        // Then
        ArtifactStoreStats stats = store.snapshotStats();
        assertThat(stats.readStreamsOpened()).isEqualTo(1);
        assertThat(stats.readStreamsCompleted()).isZero();
        assertThat(stats.readStreamsClosedEarly()).isEqualTo(1);
        assertThat(stats.bytesRead()).isEqualTo(3);
    }

    @Test
    void stream_shouldFailAndCloseResourcesWhenContentExceedsDeclaredSize() throws Exception {
        // Given
        ReadJdbc jdbc = readJdbc(3, new ByteArrayInputStream(new byte[4]));
        DatabaseArtifactStore store = store(jdbc.dataSource(), 10);
        Artifact artifact = store.get(HASH).orElseThrow();

        // When / Then
        assertThatThrownBy(() -> {
            try (InputStream input = artifact.openStreamChecked()) {
                input.readAllBytes();
            }
        }).isInstanceOf(IOException.class)
                .hasMessageContaining("exceeds declared or configured size");
        verify(jdbc.contentResultSet()).close();
        verify(jdbc.contentStatement()).close();
        verify(jdbc.contentConnection()).close();
        assertThat(store.snapshotStats().readFailures()).isEqualTo(1);
    }

    @Test
    void stream_shouldRejectSameSizeContentWhoseDigestDoesNotMatchItsKey() throws Exception {
        // Given
        byte[] corrupt = "abd".getBytes(StandardCharsets.UTF_8);
        ReadJdbc jdbc = readJdbc(corrupt.length, new ByteArrayInputStream(corrupt));
        DatabaseArtifactStore store = store(jdbc.dataSource(), 10);
        Artifact artifact = store.get(HASH).orElseThrow();

        // When / Then
        assertThatThrownBy(() -> {
            try (InputStream input = artifact.openStreamChecked()) {
                input.readAllBytes();
            }
        }).isInstanceOf(IOException.class)
                .hasMessageContaining("content hash mismatch")
                .hasMessageContaining(HASH);
        verify(jdbc.contentResultSet()).close();
        verify(jdbc.contentStatement()).close();
        verify(jdbc.contentConnection()).close();
        assertThat(store.snapshotStats().readFailures()).isEqualTo(1);
    }

    @Test
    void put_shouldRejectConfiguredLimitAndDeletePrivateSpoolFile() throws Exception {
        // Given
        DataSource dataSource = mock(DataSource.class);
        long maxBytes = 1024L * 1024L;
        DatabaseArtifactStore store = DatabaseArtifactStore.builder()
                .dataSource(dataSource)
                .databaseDialect(Gear4jDatabaseDialect.H2)
                .maxArtifactSizeBytes(maxBytes)
                .spoolDirectory(tempDirectory)
                .build();
        GeneratedInputStream content = new GeneratedInputStream(8L * 1024L * 1024L);

        // When / Then
        assertThatThrownBy(() -> store.put(content, ArtifactStore.UNLIMITED_SIZE))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("maxBytes=" + maxBytes);
        assertThat(content.bytesProduced()).isLessThanOrEqualTo(maxBytes + 8192L);
        verifyNoInteractions(dataSource);
        try (var files = Files.list(tempDirectory)) {
            assertThat(files.toList()).isEmpty();
        }
        assertThat(store.snapshotStats().writeFailures()).isEqualTo(1);
    }

    @Test
    void put_shouldRejectSpoolQuotaWithoutLeakingContentOrLeavingAFile() throws Exception {
        // Given
        DataSource dataSource = mock(DataSource.class);
        String secret = "fixture-secret-must-not-leak";
        DatabaseArtifactStore store = DatabaseArtifactStore.builder()
                .dataSource(dataSource)
                .databaseDialect(Gear4jDatabaseDialect.H2)
                .maxArtifactSizeBytes(1024)
                .spoolDirectory(tempDirectory)
                .spoolMaxBytes(3)
                .build();

        // When / Then
        assertThatThrownBy(() -> store.put(secret.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("spool quota exceeded")
                .hasMessageNotContaining(secret);
        verifyNoInteractions(dataSource);
        try (var files = Files.list(tempDirectory)) {
            assertThat(files.toList()).isEmpty();
        }
        assertThat(store.snapshotSpoolStats().quotaRejections()).isEqualTo(1L);
        assertThat(store.snapshotSpoolStats().currentFiles()).isZero();
        assertThat(store.snapshotSpoolStats().currentBytes()).isZero();
    }

    @Test
    void putByteArray_shouldRejectStoreLimitBeforeOpeningJdbcConnection() {
        // Given
        DataSource dataSource = mock(DataSource.class);
        DatabaseArtifactStore store = store(dataSource, 3);

        // When / Then
        assertThatThrownBy(() -> store.put(new byte[4]))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("maxBytes=3");
        verifyNoInteractions(dataSource);
        assertThat(store.snapshotStats().writeFailures()).isEqualTo(1);
    }

    @Test
    void put_shouldStreamFromSpoolAndRecordWriteMetrics() throws Exception {
        // Given
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        DatabaseArtifactStore store = DatabaseArtifactStore.builder()
                .dataSource(dataSource)
                .databaseDialect(Gear4jDatabaseDialect.H2)
                .maxArtifactSizeBytes(10)
                .spoolDirectory(tempDirectory)
                .build();

        // When
        String hash = store.put("abc".getBytes(StandardCharsets.UTF_8));

        // Then
        assertThat(hash).hasSize(64);
        verify(statement).setBinaryStream(org.mockito.ArgumentMatchers.eq(3),
                                          org.mockito.ArgumentMatchers.any(InputStream.class),
                                          org.mockito.ArgumentMatchers.eq(3L));
        ArtifactStoreStats stats = store.snapshotStats();
        assertThat(stats.writesCompleted()).isEqualTo(1);
        assertThat(stats.bytesWritten()).isEqualTo(3);
        assertThat(stats.writeFailures()).isZero();
        try (var files = Files.list(tempDirectory)) {
            assertThat(files.toList()).isEmpty();
        }
    }

    private static DatabaseArtifactStore store(DataSource dataSource, long maxArtifactSizeBytes) {
        return DatabaseArtifactStore.builder()
                .dataSource(dataSource)
                .databaseDialect(Gear4jDatabaseDialect.H2)
                .maxArtifactSizeBytes(maxArtifactSizeBytes)
                .build();
    }

    private static ReadJdbc readJdbc(long declaredSize, InputStream content) throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection metadataConnection = mock(Connection.class);
        PreparedStatement metadataStatement = mock(PreparedStatement.class);
        ResultSet metadataResultSet = mock(ResultSet.class);
        Connection contentConnection = mock(Connection.class);
        PreparedStatement contentStatement = mock(PreparedStatement.class);
        ResultSet contentResultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(metadataConnection, contentConnection);
        when(metadataConnection.prepareStatement(anyString())).thenReturn(metadataStatement);
        when(metadataStatement.executeQuery()).thenReturn(metadataResultSet);
        when(metadataResultSet.next()).thenReturn(true);
        when(metadataResultSet.getLong(1)).thenReturn(declaredSize);
        when(contentConnection.prepareStatement(anyString())).thenReturn(contentStatement);
        when(contentStatement.executeQuery()).thenReturn(contentResultSet);
        when(contentResultSet.next()).thenReturn(true);
        when(contentResultSet.getLong(1)).thenReturn(declaredSize);
        when(contentResultSet.getBinaryStream(2)).thenReturn(content);
        return new ReadJdbc(dataSource, metadataConnection, metadataStatement, metadataResultSet, contentConnection,
                contentStatement, contentResultSet);
    }

    private record ReadJdbc(DataSource dataSource,
                            Connection metadataConnection,
                            PreparedStatement metadataStatement,
                            ResultSet metadataResultSet,
                            Connection contentConnection,
                            PreparedStatement contentStatement,
                            ResultSet contentResultSet) {}

    private static final class GeneratedInputStream extends InputStream {
        private final long size;
        private long position;

        private GeneratedInputStream(long size) {
            this.size = size;
        }

        @Override
        public int read() {
            if (position >= size) {
                return -1;
            }
            position++;
            return 0;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (position >= size) {
                return -1;
            }
            int read = (int) Math.min(length, size - position);
            java.util.Arrays.fill(buffer, offset, offset + read, (byte) 0);
            position += read;
            return read;
        }

        private long bytesProduced() {
            return position;
        }
    }
}
