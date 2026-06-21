package io.github.gear4jtest.external.api.artifact;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
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
        Objects.requireNonNull(content, "content must not be null");
        return put(new java.io.ByteArrayInputStream(content), content.length);
    }

    @Override
    public String put(InputStream in, long maxBytes) throws IOException {
        Objects.requireNonNull(in, "input stream must not be null");
        Files.createDirectories(root);
        Path tmp = Files.createTempFile(root, "artifact-", ".tmp");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total = 0L;
            byte[] buffer = new byte[8192];
            try (var out = Files.newOutputStream(tmp)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (maxBytes >= 0 && total > maxBytes) {
                        throw new IOException("Artifact size exceeds configured limit. maxBytes=" + maxBytes);
                    }
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            }
            String hash = HexFormat.of().formatHex(digest.digest());
            Path target = pathForValidatedHash(hash);
            if (!Files.exists(target)) {
                Files.createDirectories(target.getParent());
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            return hash;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Unable to write filesystem artifact", e);
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (Exception ignored) {
                // Best-effort cleanup: the operation result must not be masked by temp-file
                // deletion failure.
            }
        }
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
