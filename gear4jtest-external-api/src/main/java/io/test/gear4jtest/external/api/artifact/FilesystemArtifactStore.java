package io.test.gear4jtest.external.api.artifact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;

public final class FilesystemArtifactStore implements ArtifactStore {
    private final Path root;

    public FilesystemArtifactStore(Path root) {
        this.root = root;
    }

    private Path pathFor(String hash) {
        String a = hash.substring(0, 2), b = hash.substring(2, 4);
        return root.resolve(a).resolve(b).resolve(hash);
    }

    @Override
    public String put(byte[] content) throws IOException {
        String hash = Hashing.sha256Hex(content);
        var target = pathFor(hash);
        if (!Files.exists(target)) {
            Files.createDirectories(target.getParent());
            var tmp = Files.createTempFile(root, "artifact-", ".tmp");
            try {
                Files.write(tmp, content);
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                try {
                    Files.deleteIfExists(tmp);
                } catch (Exception ignore) {
                }
            }
        }
        return hash;
    }

    @Override
    public Optional<Artifact> get(String hashHex) throws IOException {
        var p = pathFor(hashHex);
        if (!Files.exists(p)) return Optional.empty();
        long size = Files.size(p);
        return Optional.of(new Artifact(hashHex, size, Map.of(), () -> {
            try {
                return Files.newInputStream(p);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    @Override
    public boolean exists(String hashHex) {
        return Files.exists(pathFor(hashHex));
    }
}
