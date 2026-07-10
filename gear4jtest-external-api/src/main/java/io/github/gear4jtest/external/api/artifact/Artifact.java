package io.github.gear4jtest.external.api.artifact;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class Artifact {
    private final String hashHex;
    private final long size;
    private final Map<String, String> metadata;
    private final StreamOpener streamOpener;

    public Artifact(String hashHex, long size, Map<String, String> metadata, Supplier<InputStream> streamSupplier) {
        this(hashHex, size, metadata, toStreamOpener(streamSupplier));
    }

    private Artifact(String hashHex, long size, Map<String, String> metadata, StreamOpener streamOpener) {
        this.hashHex = Objects.requireNonNull(hashHex);
        if (size < 0) {
            throw new IllegalArgumentException("size must be >= 0");
        }
        this.size = size;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        this.streamOpener = streamOpener;
    }

    private static StreamOpener toStreamOpener(Supplier<InputStream> streamSupplier) {
        return streamSupplier == null ? null : streamSupplier::get;
    }

    public static Artifact streaming(String hashHex,
                                     long size,
                                     Map<String, String> metadata,
                                     StreamOpener streamOpener) {
        return new Artifact(hashHex, size, metadata, Objects.requireNonNull(streamOpener, "streamOpener"));
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
        try {
            return openStreamChecked();
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to open artifact stream " + hashHex, exception);
        }
    }

    public InputStream openStreamChecked() throws IOException {
        if (streamOpener == null) {
            throw new IllegalStateException("No stream available");
        }
        return streamOpener.open();
    }

    @FunctionalInterface
    public interface StreamOpener {
        InputStream open() throws IOException;
    }
}
