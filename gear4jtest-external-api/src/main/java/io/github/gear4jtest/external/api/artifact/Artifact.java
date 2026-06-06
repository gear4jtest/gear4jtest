package io.github.gear4jtest.external.api.artifact;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class Artifact {
    private final String hashHex;
    private final long size;
    private final Map<String, String> metadata;
    private final Supplier<InputStream> streamSupplier;

    public Artifact(String hashHex, long size, Map<String, String> metadata, Supplier<InputStream> streamSupplier) {
        this.hashHex = Objects.requireNonNull(hashHex);
        this.size = size;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        this.streamSupplier = streamSupplier;
    }

    public String hashHex() {
        return hashHex;
    }

    public long size() {
        return size;
    }

    public Map<String, String> metadata() {
        return metadata;
    }

    public InputStream openStream() {
        if (streamSupplier == null) {
            throw new IllegalStateException("No stream available");
        }
        return streamSupplier.get();
    }
}
