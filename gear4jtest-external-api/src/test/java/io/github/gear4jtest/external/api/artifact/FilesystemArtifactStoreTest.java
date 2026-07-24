package io.github.gear4jtest.external.api.artifact;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilesystemArtifactStoreTest {
    private static final byte[] HELLO = "hello".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tempDir;

    @Test
    void should_reject_path_traversal_hashes() throws Exception {
        // Given
        FilesystemArtifactStore store = new FilesystemArtifactStore(tempDir);

        // When / Then
        assertThatThrownBy(() -> store.get("../../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.exists("abc")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_round_trip_content() throws Exception {
        // Given
        FilesystemArtifactStore store = new FilesystemArtifactStore(tempDir);

        // When
        String hash = store.put(HELLO);

        // Then
        assertThat(store.exists(hash)).isTrue();
        assertThat(store.get(hash).orElseThrow().openStream().readAllBytes()).isEqualTo(HELLO);
        assertThat(store.snapshotStats().writesCompleted()).isEqualTo(1L);
        assertThat(store.snapshotStats().readStreamsCompleted()).isEqualTo(1L);
    }

    @Test
    void put_shouldStoreDefensiveCopyOfContent() throws Exception {
        // Given
        FilesystemArtifactStore store = new FilesystemArtifactStore(tempDir);
        byte[] content = HELLO.clone();

        // When
        String hash = store.put(content);
        content[0] = 'j';

        // Then
        assertThat(store.get(hash).orElseThrow().openStream().readAllBytes()).isEqualTo(HELLO);
    }

    @Test
    void put_shouldStreamInputDirectlyToFilesystemStore() throws Exception {
        // Given
        FilesystemArtifactStore store = new FilesystemArtifactStore(tempDir);

        // When
        String hash = store
                .put(new java.io.ByteArrayInputStream("streamed".getBytes(StandardCharsets.UTF_8)), 64);

        // Then
        assertThat(store.get(hash).orElseThrow().size()).as("artifact size is captured during streaming write")
                .isEqualTo(8);
        assertThat(new String(store.get(hash).orElseThrow().openStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("streamed");
    }

    @Test
    void put_shouldRejectStreamAboveConfiguredLimitBeforePublishingTargetFile() throws Exception {
        // Given
        FilesystemArtifactStore store = new FilesystemArtifactStore(tempDir);

        // When / Then
        assertThatThrownBy(() -> store
                .put(new java.io.ByteArrayInputStream("too-large".getBytes(StandardCharsets.UTF_8)), 3))
                .as("streaming writes must enforce max artifact size")
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("maxBytes=3");
    }

    @Test
    void put_shouldEnforceStoreLimitEvenWhenCallerRequestsUnlimitedSize() throws Exception {
        // Given
        FilesystemArtifactStore store = new FilesystemArtifactStore(tempDir, 4L);

        // When / Then
        assertThatThrownBy(() -> store.put(new java.io.ByteArrayInputStream(HELLO),
                                           ArtifactStore.UNLIMITED_SIZE))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("maxBytes=4");
        assertThat(store.snapshotStats().writeFailures()).isEqualTo(1L);
    }

    @Test
    void put_shouldApplyDefaultStoreLimitToByteArrays() throws Exception {
        // Given
        FilesystemArtifactStore store = new FilesystemArtifactStore(tempDir);
        byte[] content = new byte[(int) ArtifactStore.DEFAULT_MAX_ARTIFACT_SIZE_BYTES + 1];

        // When / Then
        assertThatThrownBy(() -> store.put(content))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("maxBytes=" + ArtifactStore.DEFAULT_MAX_ARTIFACT_SIZE_BYTES);
    }

    @Test
    void constructor_shouldRejectUnboundedOrUnmaterializableReadLimit() {
        assertThatThrownBy(() -> new FilesystemArtifactStore(tempDir, ArtifactStore.UNLIMITED_SIZE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNLIMITED_SIZE is unsupported");
        assertThatThrownBy(() -> new FilesystemArtifactStore(tempDir, Integer.MAX_VALUE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("largest supported in-memory snapshot");
    }

    @Test
    void getAndExists_shouldRejectArtifactAboveConfiguredReadLimitBeforeMaterializingIt() throws Exception {
        // Given
        Path root = tempDir.resolve("store");
        FilesystemArtifactStore writer = new FilesystemArtifactStore(root, 64L);
        String hash = writer.put(HELLO);
        FilesystemArtifactStore reader = new FilesystemArtifactStore(root, 4L);

        // When / Then
        assertThatThrownBy(() -> reader.exists(hash))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("configured read limit")
                .hasMessageContaining("maxArtifactSizeBytes=4")
                .hasMessageContaining("actualSizeBytes=5");
        assertThatThrownBy(() -> reader.get(hash))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("configured read limit")
                .hasMessageContaining("maxArtifactSizeBytes=4")
                .hasMessageContaining("actualSizeBytes=5");
    }

    @Test
    void put_shouldTreatMatchingExistingArtifactAsIdempotent() throws Exception {
        // Given
        FilesystemArtifactStore store = new FilesystemArtifactStore(tempDir);
        String hash = store.put(HELLO);

        // When
        String repeatedHash = store.put(HELLO);

        // Then
        assertThat(repeatedHash).isEqualTo(hash);
        assertThat(store.get(hash).orElseThrow().openStream().readAllBytes()).isEqualTo(HELLO);
        assertThat(store.snapshotStats().writesCompleted()).isEqualTo(2L);
    }

    @Test
    void put_shouldNotReplaceExistingArtifactWithMismatchedContent() throws Exception {
        // Given
        Path root = tempDir.resolve("store");
        FilesystemArtifactStore store = new FilesystemArtifactStore(root);
        String hash = ArtifactHashes.sha256Hex(HELLO);
        Path target = pathFor(root, hash);
        Files.createDirectories(target.getParent());
        Files.writeString(target, "mismatched-existing-content");

        // When / Then
        assertThatThrownBy(() -> store.put(HELLO))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("integrity check failed");
        assertThat(Files.readString(target)).isEqualTo("mismatched-existing-content");
    }

    @Test
    void put_shouldSafelyConvergeConcurrentIdenticalPublications() throws Exception {
        // Given
        Path root = tempDir.resolve("store");
        FilesystemArtifactStore store = new FilesystemArtifactStore(root);
        String expectedHash = ArtifactHashes.sha256Hex(HELLO);
        int writerCount = 8;
        CountDownLatch writersReady = new CountDownLatch(writerCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(writerCount);
        List<Future<String>> writes = new ArrayList<>();
        for (int index = 0; index < writerCount; index++) {
            writes.add(executor.submit(() -> {
                writersReady.countDown();
                start.await();
                return store.put(HELLO);
            }));
        }

        try {
            writersReady.await();

            // When
            start.countDown();

            // Then
            for (Future<String> write : writes) {
                assertThat(write.get()).isEqualTo(expectedHash);
            }
        } finally {
            executor.shutdownNow();
        }
        assertThat(store.get(expectedHash).orElseThrow().openStream().readAllBytes()).isEqualTo(HELLO);
        assertThat(store.snapshotStats().writesCompleted()).isEqualTo(writerCount);
    }

    @Test
    void constructor_shouldRejectRootSymbolicLink() throws Exception {
        // Given
        Path target = Files.createDirectory(tempDir.resolve("target"));
        Path link = createSymbolicLinkOrSkip(tempDir.resolve("root-link"), target);

        // When / Then
        assertThatThrownBy(() -> new FilesystemArtifactStore(link))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("symbolic link");
    }

    @Test
    void constructor_shouldRejectSymbolicLinkInRootParentChain() throws Exception {
        // Given
        Path target = Files.createDirectory(tempDir.resolve("target-parent"));
        Path link = createSymbolicLinkOrSkip(tempDir.resolve("parent-link"), target);

        // When / Then
        assertThatThrownBy(() -> new FilesystemArtifactStore(link.resolve("store")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("symbolic link");
    }

    @Test
    void put_shouldRejectSymbolicArtifactParent() throws Exception {
        // Given
        Path root = tempDir.resolve("store");
        FilesystemArtifactStore store = new FilesystemArtifactStore(root);
        String hash = ArtifactHashes.sha256Hex(HELLO);
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        createSymbolicLinkOrSkip(root.resolve(hash.substring(0, 2)), outside);

        // When / Then
        assertThatThrownBy(() -> store.put(HELLO))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("symbolic link");
        assertThat(outside).isEmptyDirectory();
    }

    @Test
    void get_shouldRejectSymbolicArtifactLeafWithoutReadingTarget() throws Exception {
        // Given
        Path root = tempDir.resolve("store");
        FilesystemArtifactStore store = new FilesystemArtifactStore(root);
        String hash = ArtifactHashes.sha256Hex(HELLO);
        Path parent = Files.createDirectories(root.resolve(hash.substring(0, 2))
                .resolve(hash.substring(2, 4)));
        Path secret = Files.writeString(tempDir.resolve("secret.txt"), "must-not-be-read");
        createSymbolicLinkOrSkip(parent.resolve(hash), secret);

        // When / Then
        assertThatThrownBy(() -> store.get(hash))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("symbolic link")
                .hasMessageNotContaining("must-not-be-read");
    }

    @Test
    void get_shouldRejectContentThatDoesNotMatchAddressHash() throws Exception {
        // Given
        Path root = tempDir.resolve("store");
        FilesystemArtifactStore store = new FilesystemArtifactStore(root);
        String hash = store.put(HELLO);
        Files.writeString(pathFor(root, hash), "tampered");

        // When / Then
        assertThatThrownBy(() -> store.exists(hash))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("integrity check failed");
        assertThatThrownBy(() -> store.get(hash))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("integrity check failed");
        assertThat(store.snapshotStats().readFailures()).isEqualTo(1L);
    }

    @Test
    void get_shouldReturnVerifiedSnapshotUnaffectedByLaterFilesystemChanges() throws Exception {
        // Given
        Path root = tempDir.resolve("store");
        FilesystemArtifactStore store = new FilesystemArtifactStore(root);
        String hash = store.put(HELLO);

        // When
        Artifact snapshot = store.get(hash).orElseThrow();
        Files.writeString(pathFor(root, hash), "tampered-after-get");

        // Then
        assertThat(snapshot.openStream().readAllBytes()).isEqualTo(HELLO);
    }

    @Test
    void put_shouldUseOwnerOnlyPosixPermissions() throws Exception {
        // Given
        Path root = tempDir.resolve("store");
        FilesystemArtifactStore store = new FilesystemArtifactStore(root);

        // When
        String hash = store.put(HELLO);

        // Then
        Assumptions.assumeTrue(Files.getFileStore(root).supportsFileAttributeView("posix"));
        Set<PosixFilePermission> directoryPermissions = Set.of(PosixFilePermission.OWNER_READ,
                                                               PosixFilePermission.OWNER_WRITE,
                                                               PosixFilePermission.OWNER_EXECUTE);
        Set<PosixFilePermission> filePermissions = Set.of(PosixFilePermission.OWNER_READ,
                                                          PosixFilePermission.OWNER_WRITE);
        Path target = pathFor(root, hash);
        assertThat(Files.getPosixFilePermissions(root)).isEqualTo(directoryPermissions);
        assertThat(Files.getPosixFilePermissions(target.getParent().getParent())).isEqualTo(directoryPermissions);
        assertThat(Files.getPosixFilePermissions(target.getParent())).isEqualTo(directoryPermissions);
        assertThat(Files.getPosixFilePermissions(target)).isEqualTo(filePermissions);
    }

    @Test
    void cleanupFailure_shouldBeCountedWithoutMaskingWriteFailure() throws Exception {
        // Given
        AtomicReference<Path> abandonedTempFile = new AtomicReference<>();
        FilesystemArtifactStore store = new FilesystemArtifactStore(tempDir, path -> {
            abandonedTempFile.set(path);
            throw new IOException("simulated cleanup failure");
        });

        try {
            // When / Then
            assertThatThrownBy(() -> store.put(new java.io.ByteArrayInputStream(HELLO), 1))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("maxBytes=1")
                    .hasMessageNotContaining("simulated cleanup failure");
            assertThat(store.snapshotStats().writeFailures()).isEqualTo(1L);
            assertThat(store.snapshotStats().cleanupFailures()).isEqualTo(1L);
            assertThat(abandonedTempFile.get()).exists();
        } finally {
            Files.deleteIfExists(abandonedTempFile.get());
        }
    }

    @Test
    void operation_shouldRejectRootReplacedBySymbolicLinkAfterConstruction() throws Exception {
        // Given
        Path root = tempDir.resolve("store");
        FilesystemArtifactStore store = new FilesystemArtifactStore(root);
        Path originalRoot = tempDir.resolve("original-store");
        Files.move(root, originalRoot);
        createSymbolicLinkOrSkip(root, tempDir);

        // When / Then
        assertThatThrownBy(() -> store.put(HELLO))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("symbolic link");
    }

    private static Path pathFor(Path root, String hash) {
        return root.resolve(hash.substring(0, 2)).resolve(hash.substring(2, 4)).resolve(hash);
    }

    private static Path createSymbolicLinkOrSkip(Path link, Path target) {
        try {
            return Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable: " + exception.getMessage());
            throw new IllegalStateException("Assumption should have aborted the test", exception);
        }
    }
}
