package io.github.gear4jtest.external.api.storage;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
 */
public final class DefaultArtifactStoreProvider implements ArtifactStoreProvider, AutoCloseable {
    private final ArtifactStoreResolver resolver;
    private final ArtifactStorePlugin.Context ctx;
    private final Executor asyncExec;
    private final Map<StoreConfiguration, StoreLease> storesByConfiguration = new HashMap<>();
    private final IdentityHashMap<ArtifactStore, Integer> leasesByStore = new IdentityHashMap<>();
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
    public synchronized ArtifactStore forConfig(OperationChainConfig cfg) {
        Objects.requireNonNull(cfg, "cfg");
        if (closed) {
            throw new IllegalStateException("Artifact-store provider is closed");
        }
        StoreConfiguration configuration = StoreConfiguration.from(cfg);
        StoreLease existing = storesByConfiguration.get(configuration);
        if (existing != null) {
            existing.retain();
            leasesByStore.merge(existing.store(), 1, Integer::sum);
            return existing.store();
        }

        ArtifactStore store = buildStore(cfg);
        storesByConfiguration.put(configuration, new StoreLease(store));
        leasesByStore.merge(store, 1, Integer::sum);
        return store;
    }

    @Override
    public synchronized void release(ArtifactStore store) {
        if (store == null) {
            return;
        }
        StoreConfiguration releasedConfiguration = null;
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
        if (totalReferences == null || totalReferences <= 1) {
            leasesByStore.remove(store);
            store.close();
        } else {
            leasesByStore.put(store, totalReferences - 1);
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        List<ArtifactStore> stores = new ArrayList<>(leasesByStore.keySet());
        storesByConfiguration.clear();
        leasesByStore.clear();
        for (ArtifactStore store : stores) {
            store.close();
        }
    }

    private ArtifactStore buildStore(OperationChainConfig cfg) {
        Map<String, String> props = cfg.storeProps();

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
            primary = resolver.resolve(cfg.storeType().name(), props, ctx);

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
            closeStores(primary, fallbacks);
            throw exception;
        }
    }

    private static void closeStores(ArtifactStore primary, List<ArtifactStore> fallbacks) {
        IdentityHashMap<ArtifactStore, Boolean> closed = new IdentityHashMap<>();
        if (primary != null) {
            closed.put(primary, Boolean.TRUE);
            primary.close();
        }
        for (ArtifactStore fallback : fallbacks) {
            if (closed.put(fallback, Boolean.TRUE) == null) {
                fallback.close();
            }
        }
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
            closeStores(null, out);
            throw exception;
        }
    }
}
