package io.test.gear4jtest.external.api.artifact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class FilesystemArtifactStore implements ArtifactStore {
    private final Path root;

    public FilesystemArtifactStore(Path root) {
        this.root = Objects.requireNonNull(root, "root must not be null");
    }

    private Path pathForValidatedHash(String hash) {
        String firstSegment = hash.substring(0, 2);
        String secondSegment = hash.substring(2, 4);
        return root.resolve(firstSegment).resolve(secondSegment).resolve(hash);
    }

    @Override
    public String put(byte[] content) throws IOException {
        String hash = Hashing.sha256Hex(content);
        Path target = pathForValidatedHash(hash);
        if (!Files.exists(target)) {
            Files.createDirectories(target.getParent());
            Path tmp = Files.createTempFile(root, "artifact-", ".tmp");
            try {
                Files.write(tmp, content);
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                try {
                    Files.deleteIfExists(tmp);
                } catch (Exception ignored) {
                }
            }
        }
        return hash;
    }

    @Override
    public Optional<Artifact> get(String hashHex) throws IOException {
        String hash = Hashing.requireSha256Hex(hashHex);
        Path path = pathForValidatedHash(hash);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        long size = Files.size(path);
        return Optional.of(new Artifact(hash, size, Map.of(), () -> {
            try {
                return Files.newInputStream(path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    @Override
    public boolean exists(String hashHex) {
        String hash = Hashing.requireSha256Hex(hashHex);
        return Files.exists(pathForValidatedHash(hash));
    }
}
