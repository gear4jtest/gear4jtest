package io.github.gear4jtest.external.api.storage;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import io.github.gear4jtest.external.api.artifact.ArtifactSpoolPolicy;
import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.artifact.ArtifactStoreExecutors;
import io.github.gear4jtest.external.api.artifact.CompositeArtifactStore;
import io.github.gear4jtest.external.api.model.OperationChainConfig;
import io.github.gear4jtest.external.api.spi.ArtifactStorePlugin;
import io.github.gear4jtest.external.api.spi.ArtifactStoreProvider;
import io.github.gear4jtest.external.api.spi.ArtifactStoreResolver;

/**
 * Builds the configured primary {@link ArtifactStore} and optional fallback
 * stores from an external pipeline configuration.
 *
 * <p>
 * Store implementations are resolved through the artifact-store SPI. Supported
 * properties include read/write mode, self-healing flags and numbered
 * {@code fallback.N.*} entries.
 * </p>
 *
 * <p>
 * Each call to {@link #forConfig(OperationChainConfig)} acquires a lease.
 * Callers must balance it with {@link #release(ArtifactStore)}, or close the
 * provider only after every consumer has stopped.
 * </p>
 *
 * <p>
 * Construction is shared per configuration. Backend creation and closure run
 * outside the provider metadata monitor, so callbacks for different
 * configurations may execute concurrently. Closing rejects new acquisition and
 * wakes waiters; still-running constructors dispose their late results when
 * they return.
 * </p>
 */
public final class DefaultArtifactStoreProvider implements ArtifactStoreProvider, AutoCloseable {
    private static final Set<String> PROVIDER_PROPERTIES = Set.of(
                                                                  "mode.write", "mode.read", "verifyOnRead",
                                                                  "selfHealing", "verificationMaxArtifactSizeBytes",
                                                                  "spoolDirectory", "spoolMaxBytes",
                                                                  "spoolStaleFileAge", "requirePrivatePermissions");

    private final ArtifactStoreResolver resolver;
    private final ArtifactStorePlugin.Context ctx;
    private final Executor asyncExec;
    private final Map<StoreConfiguration, StoreLease> storesByConfiguration = new HashMap<>();
    private final IdentityHashMap<ArtifactStore, Integer> leasesByStore = new IdentityHashMap<>();
    private final Map<StoreConfiguration, PendingStore> pendingStores = new HashMap<>();
    private final IdentityHashMap<ArtifactStore, Boolean> retiringStores = new IdentityHashMap<>();
    private boolean closed;

    /**
     * Creates a provider that discovers store plugins through the supplied class
     * loader.
     *
     * @param classLoader class loader used for SPI discovery, usually the thread
     *                    context class loader
     * @param ctx         optional lookup context for backend resources
     * @param asyncExec   executor used by fallback and self-healing stores
     */
    public DefaultArtifactStoreProvider(ClassLoader classLoader, ArtifactStorePlugin.Context ctx, Executor asyncExec) {
        this.resolver = new ArtifactStoreResolver(classLoader);
        this.ctx = ctx != null ? ctx : key -> null;
        this.asyncExec = asyncExec != null ? asyncExec : ArtifactStoreExecutors.defaultAsyncExecutor();
    }

    /**
     * Creates a provider with an already initialized resolver.
     */
    public DefaultArtifactStoreProvider(ArtifactStoreResolver resolver,
                                        ArtifactStorePlugin.Context ctx,
                                        Executor asyncExec) {
        this.resolver = Objects.requireNonNull(resolver);
        this.ctx = ctx != null ? ctx : key -> null;
        this.asyncExec = asyncExec != null ? asyncExec : ArtifactStoreExecutors.defaultAsyncExecutor();
    }

    private static CompositeArtifactStore.WriteMode parseWriteMode(String value) {
        return parseEnum(value, CompositeArtifactStore.WriteMode.PRIMARY_ONLY, CompositeArtifactStore.WriteMode.class,
                         "mode.write");
    }

    // ---------- helpers ----------

    private static CompositeArtifactStore.ReadMode parseReadMode(String value) {
        return parseEnum(value, CompositeArtifactStore.ReadMode.PREFER_PRIMARY, CompositeArtifactStore.ReadMode.class,
                         "mode.read");
    }

    private static <E extends Enum<E>> E parseEnum(String value,
                                                   E defaultValue,
                                                   Class<E> enumType,
                                                   String propertyName) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid artifact store property '" + propertyName + "': " + value, e);
        }
    }

    private static boolean parseBoolean(String value, boolean defaultValue, String propertyName) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(value.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(value.trim())) {
            return false;
        }
        throw new IllegalArgumentException("Invalid artifact store property '" + propertyName
                + "': " + value + ". Expected true or false.");
    }

    private static long parseLong(String value, long defaultValue, String propertyName) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid artifact store property '" + propertyName + "': " + value, e);
        }
    }

    private static String opt(Map<String, String> m, String k) {
        return m.get(k);
    }

    private static int parseFallbackIndex(String index) {
        try {
            int parsed = Integer.parseInt(index);
            if (parsed <= 0) {
                throw new IllegalArgumentException("Invalid artifact fallback index '" + index
                        + "'. Expected a positive integer.");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid artifact fallback index '" + index
                    + "'. Expected a positive integer.", e);
        }
    }

    @Override
    public ArtifactStore forConfig(OperationChainConfig cfg) {
        Objects.requireNonNull(cfg, "cfg");
        StoreConfiguration configuration = StoreConfiguration.from(cfg);
        while (true) {
            PendingStore pending;
            boolean creator;
            synchronized (this) {
                requireOpen();
                StoreLease existing = storesByConfiguration.get(configuration);
                if (existing != null) {
                    existing.retain();
                    leasesByStore.merge(existing.store(), 1, Integer::sum);
                    return existing.store();
                }
                pending = pendingStores.get(configuration);
                creator = pending == null;
                if (creator) {
                    pending = new PendingStore(true);
                    pendingStores.put(configuration, pending);
                }
            }
            if (creator) {
                return createStore(cfg, configuration, pending);
            }
            pending.await();
        }
    }

    private ArtifactStore createStore(OperationChainConfig cfg,
                                      StoreConfiguration configuration,
                                      PendingStore pending) {
        ArtifactStore store;
        try {
            store = Objects.requireNonNull(buildStore(cfg), "artifact-store plugin returned null");
        } catch (RuntimeException | Error failure) {
            finishPending(configuration, pending, failure);
            throw failure;
        }
        Throwable rejection = null;
        boolean dispose = false;
        synchronized (this) {
            if (pending.wasRetired(store) || retiringStores.containsKey(store)) {
                // A plugin may share an instance across configurations, but a retired
                // instance cannot be installed or closed a second time by this construction.
                rejection = new IllegalStateException(
                        "Artifact-store plugin returned an instance retired during construction");
            } else if (closed) {
                rejection = new IllegalStateException("Artifact-store provider is closed");
                dispose = !leasesByStore.containsKey(store);
                if (dispose) {
                    beginRetirement(store);
                }
            } else {
                storesByConfiguration.put(configuration, new StoreLease(store));
                leasesByStore.merge(store, 1, Integer::sum);
            }
        }
        if (rejection != null) {
            if (dispose) {
                try {
                    closeRetired(store);
                } catch (RuntimeException | Error cleanupFailure) {
                    rejection = combineFailures(rejection, cleanupFailure);
                }
            }
            finishPending(configuration, pending, rejection);
            rethrow(rejection);
        }
        finishPending(configuration, pending, null);
        return store;
    }

    @Override
    public void release(ArtifactStore store) {
        if (store == null) {
            return;
        }
        StoreConfiguration releasedConfiguration = null;
        PendingStore retirement;
        synchronized (this) {
            StoreLease releasedLease = null;
            for (var entry : storesByConfiguration.entrySet()) {
                if (entry.getValue().store() == store && entry.getValue().references() > 0) {
                    releasedConfiguration = entry.getKey();
                    releasedLease = entry.getValue();
                    break;
                }
            }
            if (releasedLease == null) {
                return;
            }
            if (releasedLease.release() == 0) {
                storesByConfiguration.remove(releasedConfiguration);
            }
            Integer totalReferences = leasesByStore.get(store);
            if (totalReferences != null && totalReferences > 1) {
                leasesByStore.put(store, totalReferences - 1);
                return;
            }
            leasesByStore.remove(store);
            retirement = new PendingStore(false);
            pendingStores.put(releasedConfiguration, retirement);
            beginRetirement(store);
        }
        Throwable failure = null;
        try {
            closeRetired(store);
        } catch (RuntimeException | Error cleanupFailure) {
            failure = cleanupFailure;
            throw cleanupFailure;
        } finally {
            finishPending(releasedConfiguration, retirement, failure);
        }
    }

    @Override
    public void close() {
        List<ArtifactStore> stores;
        List<PendingStore> pending;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            stores = new ArrayList<>(leasesByStore.keySet());
            for (ArtifactStore store : stores) {
                beginRetirement(store);
            }
            storesByConfiguration.clear();
            leasesByStore.clear();
            pending = new ArrayList<>(pendingStores.values());
        }
        // Do not wait for plugin constructors. Their owners dispose late results.
        for (PendingStore operation : pending) {
            operation.completed.completeExceptionally(new IllegalStateException("Artifact-store provider is closed"));
        }
        Throwable failure = null;
        for (ArtifactStore store : stores) {
            try {
                closeRetired(store);
            } catch (RuntimeException | Error cleanupFailure) {
                failure = combineFailures(failure, cleanupFailure);
            }
        }
        rethrow(failure);
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Artifact-store provider is closed");
        }
    }

    // Called under the metadata monitor; no plugin callbacks run here.
    private void beginRetirement(ArtifactStore store) {
        retiringStores.put(store, Boolean.TRUE);
        for (PendingStore pending : pendingStores.values()) {
            if (pending.creating) {
                // Weak identity markers protect aliases without keeping unrelated closed
                // backends alive if a plugin constructor remains blocked indefinitely.
                pending.markRetired(store);
            }
        }
    }

    private void closeRetired(ArtifactStore store) {
        try {
            store.close();
        } finally {
            synchronized (this) {
                retiringStores.remove(store);
            }
        }
    }

    private void finishPending(StoreConfiguration configuration, PendingStore pending, Throwable failure) {
        synchronized (this) {
            pendingStores.remove(configuration, pending);
        }
        if (failure == null) {
            pending.completed.complete(null);
        } else {
            pending.completed.completeExceptionally(failure);
        }
    }

    private static Throwable combineFailures(Throwable primary, Throwable secondary) {
        if (primary == null) {
            return secondary;
        }
        if (primary != secondary) {
            if (secondary instanceof Error && !(primary instanceof Error)) {
                secondary.addSuppressed(primary);
                return secondary;
            }
            primary.addSuppressed(secondary);
        }
        return primary;
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof RuntimeException exception) {
            throw exception;
        }
    }

    private static final class PendingStore {
        private final Thread owner = Thread.currentThread();
        private final CompletableFuture<Void> completed = new CompletableFuture<>();
        private final ReferenceQueue<ArtifactStore> collectedStores = new ReferenceQueue<>();
        private final Set<RetiredStore> retiredDuringBuild = new HashSet<>();
        private final boolean creating;

        private PendingStore(boolean creating) {
            this.creating = creating;
        }

        private void markRetired(ArtifactStore store) {
            for (var reference = collectedStores.poll(); reference != null; reference = collectedStores.poll()) {
                retiredDuringBuild.remove(reference);
            }
            retiredDuringBuild.add(new RetiredStore(store, collectedStores));
        }

        private boolean wasRetired(ArtifactStore store) {
            return retiredDuringBuild.contains(new RetiredStore(store, null));
        }

        private void await() {
            if (owner == Thread.currentThread()) {
                throw new IllegalStateException("Recursive acquisition of the same artifact-store configuration");
            }
            try {
                completed.get();
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while acquiring an artifact store", failure);
            } catch (ExecutionException failure) {
                rethrow(failure.getCause());
                throw new IllegalStateException("Artifact-store acquisition failed", failure.getCause());
            }
        }
    }

    private static final class RetiredStore extends WeakReference<ArtifactStore> {
        private final int identityHash;

        private RetiredStore(ArtifactStore store, ReferenceQueue<ArtifactStore> queue) {
            super(store, queue);
            identityHash = System.identityHashCode(store);
        }

        @Override
        public int hashCode() {
            return identityHash;
        }

        @Override
        public boolean equals(Object other) {
            ArtifactStore store = get();
            return this == other
                    || store != null && other instanceof RetiredStore reference && store == reference.get();
        }
    }

    private ArtifactStore buildStore(OperationChainConfig cfg) {
        Map<String, String> props = cfg.storeProps();
        validateTopLevelProperties(cfg.storeType().name(), props);

        CompositeArtifactStore.WriteMode writeMode = parseWriteMode(props.get("mode.write"));
        CompositeArtifactStore.ReadMode readMode = parseReadMode(props.get("mode.read"));
        boolean verifyOnRead = parseBoolean(props.get("verifyOnRead"), false, "verifyOnRead");
        boolean selfHealing = parseBoolean(props.get("selfHealing"), false, "selfHealing");
        long verificationMaxArtifactSizeBytes = parseLong(props.get("verificationMaxArtifactSizeBytes"),
                                                          ArtifactStore.DEFAULT_MAX_ARTIFACT_SIZE_BYTES,
                                                          "verificationMaxArtifactSizeBytes");
        String spoolDirectoryProperty = props.get("spoolDirectory");
        Path spoolDirectory = spoolDirectoryProperty == null || spoolDirectoryProperty.isBlank() ? null
                : Path.of(spoolDirectoryProperty);
        long spoolMaxBytes = parseLong(props.get("spoolMaxBytes"), ArtifactSpoolPolicy.DEFAULT_MAX_BYTES,
                                       "spoolMaxBytes");
        Duration spoolStaleFileAge = parseDuration(props.get("spoolStaleFileAge"),
                                                   ArtifactSpoolPolicy.DEFAULT_STALE_FILE_AGE,
                                                   "spoolStaleFileAge");
        boolean requirePrivatePermissions = parseBoolean(props.get("requirePrivatePermissions"),
                                                         ArtifactSpoolPolicy.DEFAULT_REQUIRE_PRIVATE_PERMISSIONS,
                                                         "requirePrivatePermissions");
        ArtifactSpoolPolicy spoolPolicy = ArtifactSpoolPolicy.builder()
                .directory(spoolDirectory)
                .maxBytes(spoolMaxBytes)
                .staleFileAge(spoolStaleFileAge)
                .requirePrivatePermissions(requirePrivatePermissions)
                .build();

        ArtifactStore primary = null;
        List<ArtifactStore> fallbacks = List.of();
        try {
            // Primary store type declared by the pipeline configuration.
            primary = resolver.resolve(cfg.storeType().name(), backendProperties(cfg.storeType().name(), props), ctx);

            // Numbered fallback stores: fallback.N.type and fallback.N.props.*
            fallbacks = buildFallbacks(props);

            if (fallbacks.isEmpty()) {
                if (writeMode != CompositeArtifactStore.WriteMode.PRIMARY_ONLY) {
                    throw new IllegalArgumentException("Artifact store property 'mode.write' requires at least one "
                            + "complete fallback store");
                }
                if (selfHealing) {
                    throw new IllegalArgumentException("Artifact store property 'selfHealing=true' requires at least "
                            + "one complete fallback store");
                }
                if (!verifyOnRead) {
                    return primary;
                }
            }

            return new CompositeArtifactStore(primary, fallbacks, writeMode, readMode, verifyOnRead, selfHealing,
                    verificationMaxArtifactSizeBytes, spoolPolicy, asyncExec);
        } catch (RuntimeException | Error exception) {
            try {
                closeStores(primary, fallbacks);
            } catch (RuntimeException | Error cleanupFailure) {
                rethrow(combineFailures(exception, cleanupFailure));
            }
            throw exception;
        }
    }

    private static void closeStores(ArtifactStore primary, List<ArtifactStore> fallbacks) {
        IdentityHashMap<ArtifactStore, Boolean> unique = new IdentityHashMap<>();
        if (primary != null) {
            unique.put(primary, Boolean.TRUE);
        }
        for (ArtifactStore fallback : fallbacks) {
            unique.put(fallback, Boolean.TRUE);
        }
        Throwable failure = null;
        for (ArtifactStore store : unique.keySet()) {
            try {
                store.close();
            } catch (RuntimeException | Error cleanupFailure) {
                failure = combineFailures(failure, cleanupFailure);
            }
        }
        rethrow(failure);
    }

    private void validateTopLevelProperties(String storeType, Map<String, String> properties) {
        var schema = resolver.propertySchema(storeType);
        if (schema.allowsUnknownProperties()) {
            return;
        }
        var supported = schema.supportedProperties();
        List<String> unknown = properties.keySet().stream()
                .filter(name -> !name.startsWith("fallback."))
                .filter(name -> !PROVIDER_PROPERTIES.contains(name))
                .filter(name -> !supported.contains(name))
                .sorted()
                .toList();
        if (!unknown.isEmpty()) {
            Set<String> accepted = new TreeSet<>(PROVIDER_PROPERTIES);
            accepted.addAll(supported);
            throw new IllegalArgumentException("Unsupported " + storeType + " artifact store properties: " + unknown
                    + ". Supported properties: " + accepted);
        }
    }

    private Map<String, String> backendProperties(String storeType, Map<String, String> properties) {
        var schema = resolver.propertySchema(storeType);
        return properties.entrySet().stream()
                .filter(entry -> !entry.getKey().startsWith("fallback."))
                .filter(entry -> schema.supportedProperties().contains(entry.getKey())
                        || (schema.allowsUnknownProperties() && !PROVIDER_PROPERTIES.contains(entry.getKey())))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private record StoreConfiguration(String type, Map<String, String> properties) {
        private static StoreConfiguration from(OperationChainConfig config) {
            return new StoreConfiguration(config.storeType().name(), Map.copyOf(config.storeProps()));
        }
    }

    private static final class StoreLease {
        private final ArtifactStore store;
        private int references = 1;

        private StoreLease(ArtifactStore store) {
            this.store = Objects.requireNonNull(store, "artifact-store plugin returned null");
        }

        private ArtifactStore store() {
            return store;
        }

        private int references() {
            return references;
        }

        private void retain() {
            references = Math.addExact(references, 1);
        }

        private int release() {
            references--;
            return references;
        }
    }

    private static Duration parseDuration(String value, Duration defaultValue, String property) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Duration.parse(value.trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid artifact store property '" + property + "': " + value
                    + ". Expected an ISO-8601 duration such as PT24H.", exception);
        }
    }

    private record FallbackGroup(int order, Map<String, String> properties) {}

    private List<ArtifactStore> buildFallbacks(Map<String, String> props) {
        Map<String, Map<String, String>> groups = new HashMap<>();
        for (var e : props.entrySet()) {
            String k = e.getKey();
            if (!k.startsWith("fallback.")) {
                continue;
            }
            String rest = k.substring("fallback.".length());
            int dot = rest.indexOf('.');
            if (dot < 1 || dot == rest.length() - 1) {
                throw new IllegalArgumentException("Invalid artifact fallback property '" + k
                        + "'. Expected fallback.N.type or fallback.N.props.name.");
            }
            String idx = rest.substring(0, dot);
            String tail = rest.substring(dot + 1);
            if (!"type".equals(tail) && !tail.startsWith("props.")) {
                throw new IllegalArgumentException("Invalid artifact fallback property '" + k
                        + "'. Expected fallback.N.type or fallback.N.props.name.");
            }
            if (tail.startsWith("props.") && tail.length() == "props.".length()) {
                throw new IllegalArgumentException("Invalid artifact fallback property '" + k
                        + "'. Child property name must not be blank.");
            }
            groups.computeIfAbsent(idx, __ -> new HashMap<>()).put(tail, e.getValue());
        }

        var ordered = groups.entrySet().stream()
                .map(entry -> new FallbackGroup(parseFallbackIndex(entry.getKey()), entry.getValue()))
                .sorted(Comparator.comparingInt(FallbackGroup::order))
                .toList();
        for (int index = 1; index < ordered.size(); index++) {
            if (ordered.get(index - 1).order() == ordered.get(index).order()) {
                throw new IllegalArgumentException("Duplicate artifact fallback index " + ordered.get(index).order());
            }
        }

        List<ArtifactStore> out = new ArrayList<>();
        try {
            for (FallbackGroup fallback : ordered) {
                Map<String, String> g = fallback.properties();
                String type = opt(g, "type");
                if (type == null || type.isBlank()) {
                    throw new IllegalArgumentException("Artifact fallback " + fallback.order()
                            + " must define fallback." + fallback.order() + ".type");
                }

                // Convert props.* entries into the child store property map.
                Map<String, String> childProps = g.entrySet().stream()
                        .filter(en -> en.getKey().startsWith("props."))
                        .collect(Collectors.toMap(en -> en.getKey().substring("props.".length()),
                                                  Map.Entry::getValue));

                out.add(resolver.resolve(type, childProps, ctx));
            }
            return out;
        } catch (RuntimeException | Error exception) {
            try {
                closeStores(null, out);
            } catch (RuntimeException | Error cleanupFailure) {
                rethrow(combineFailures(exception, cleanupFailure));
            }
            throw exception;
        }
    }
}
