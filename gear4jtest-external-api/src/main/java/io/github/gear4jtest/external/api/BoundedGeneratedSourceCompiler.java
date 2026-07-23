package io.github.gear4jtest.external.api;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import io.github.gear4jtest.external.api.compiler.GeneratedSourceCompiler;
import io.github.gear4jtest.external.api.exception.CompilationException;

/**
 * Bounded single-flight cache shared by publication validation and runtime
 * loading.
 */
final class BoundedGeneratedSourceCompiler implements GeneratedSourceCompiler {
    static final int DEFAULT_MAX_ENTRIES = 128;
    static final long DEFAULT_MAX_BYTECODE_BYTES = 16L * 1024L * 1024L;

    private final GeneratedSourceCompiler delegate;
    private final int maxEntries;
    private final long maxBytecodeBytes;
    private final Map<CompilationKey, CachedCompilation> completed;
    private final Map<CompilationKey, CompletableFuture<Map<String, byte[]>>> inFlight = new ConcurrentHashMap<>();
    private long cachedBytecodeBytes;

    BoundedGeneratedSourceCompiler(GeneratedSourceCompiler delegate) {
        this(delegate, DEFAULT_MAX_ENTRIES, DEFAULT_MAX_BYTECODE_BYTES);
    }

    BoundedGeneratedSourceCompiler(GeneratedSourceCompiler delegate, int maxEntries) {
        this(delegate, maxEntries, DEFAULT_MAX_BYTECODE_BYTES);
    }

    BoundedGeneratedSourceCompiler(GeneratedSourceCompiler delegate, int maxEntries, long maxBytecodeBytes) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be > 0");
        }
        if (maxBytecodeBytes <= 0L) {
            throw new IllegalArgumentException("maxBytecodeBytes must be > 0");
        }
        this.maxEntries = maxEntries;
        this.maxBytecodeBytes = maxBytecodeBytes;
        this.completed = new LinkedHashMap<>(16, 0.75f, true);
    }

    @Override
    public Map<String, byte[]> compile(String className, byte[] sourceCode) {
        CompilationKey key = CompilationKey.of(className, sourceCode);
        Map<String, byte[]> cached = findCached(key);
        if (cached != null) {
            return copyClasses(cached);
        }

        CompletableFuture<Map<String, byte[]>> owned = new CompletableFuture<>();
        CompletableFuture<Map<String, byte[]>> current = inFlight.putIfAbsent(key, owned);
        if (current != null) {
            return copyClasses(await(key, current));
        }

        try {
            Map<String, byte[]> cachedAfterOwnership = findCached(key);
            if (cachedAfterOwnership != null) {
                owned.complete(cachedAfterOwnership);
                return copyClasses(cachedAfterOwnership);
            }
            Map<String, byte[]> compiled = copyClasses(delegate.compile(className, sourceCode.clone()));
            cache(key, compiled);
            owned.complete(compiled);
            return copyClasses(compiled);
        } catch (RuntimeException | Error failure) {
            owned.completeExceptionally(failure);
            throw failure;
        } finally {
            inFlight.remove(key, owned);
        }
    }

    private Map<String, byte[]> findCached(CompilationKey key) {
        synchronized (completed) {
            CachedCompilation cached = completed.get(key);
            return cached == null ? null : cached.classes();
        }
    }

    private void cache(CompilationKey key, Map<String, byte[]> compiled) {
        long bytecodeBytes = bytecodeSize(compiled);
        if (bytecodeBytes > maxBytecodeBytes) {
            return;
        }
        synchronized (completed) {
            CachedCompilation previous = completed.put(key, new CachedCompilation(compiled, bytecodeBytes));
            if (previous != null) {
                cachedBytecodeBytes -= previous.bytecodeBytes();
            }
            cachedBytecodeBytes += bytecodeBytes;
            while (completed.size() > maxEntries || cachedBytecodeBytes > maxBytecodeBytes) {
                CompilationKey eldest = completed.keySet().iterator().next();
                CachedCompilation removed = completed.remove(eldest);
                cachedBytecodeBytes -= removed.bytecodeBytes();
            }
        }
    }

    private static long bytecodeSize(Map<String, byte[]> compiled) {
        long size = 0L;
        for (byte[] bytes : compiled.values()) {
            size = Math.addExact(size, bytes.length);
        }
        return size;
    }

    private static Map<String, byte[]> await(CompilationKey key,
                                             CompletableFuture<Map<String, byte[]>> future) {
        try {
            return future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CompilationException("Interrupted while waiting for generated-source compilation "
                    + key.className(), List.of(), interrupted);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new CompilationException("Generated-source compilation failed for " + key.className(),
                    List.of(), cause);
        }
    }

    private static Map<String, byte[]> copyClasses(Map<String, byte[]> classes) {
        if (classes == null) {
            throw new CompilationException("Generated-source compiler returned null");
        }
        Map<String, byte[]> copy = new LinkedHashMap<>();
        classes.forEach((name, bytes) -> copy.put(
                                                  Objects.requireNonNull(name, "compiled class name must not be null"),
                                                  Objects.requireNonNull(bytes, "compiled class bytes must not be null")
                                                          .clone()));
        return Map.copyOf(copy);
    }

    private record CachedCompilation(Map<String, byte[]> classes, long bytecodeBytes) {}

    private record CompilationKey(String className, byte[] sourceHash) {
        private CompilationKey {
            Objects.requireNonNull(className, "className must not be null");
            sourceHash = sourceHash.clone();
        }

        static CompilationKey of(String className, byte[] sourceCode) {
            Objects.requireNonNull(sourceCode, "sourceCode must not be null");
            try {
                return new CompilationKey(className, MessageDigest.getInstance("SHA-256").digest(sourceCode));
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("SHA-256 is not available", impossible);
            }
        }

        @Override
        public byte[] sourceHash() {
            return sourceHash.clone();
        }

        @Override
        public boolean equals(Object candidate) {
            return candidate instanceof CompilationKey other
                    && className.equals(other.className)
                    && Arrays.equals(sourceHash, other.sourceHash);
        }

        @Override
        public int hashCode() {
            return 31 * className.hashCode() + Arrays.hashCode(sourceHash);
        }
    }
}
