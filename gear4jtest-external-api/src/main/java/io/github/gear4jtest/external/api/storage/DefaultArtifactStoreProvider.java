package io.github.gear4jtest.external.api.storage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.artifact.ArtifactStoreExecutors;
import io.github.gear4jtest.external.api.artifact.CompositeArtifactStore;
import io.github.gear4jtest.external.api.model.OperationChainConfig;
import io.github.gear4jtest.external.api.spi.ArtifactStorePlugin;
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
 */
public final class DefaultArtifactStoreProvider implements ArtifactStoreProvider {
    private final ArtifactStoreResolver resolver;
    private final ArtifactStorePlugin.Context ctx;
    private final Executor asyncExec;

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

    private static boolean isTrue(String v) {
        return v != null && (v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes") || v.equals("1"));
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
            return Integer.parseInt(index);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid artifact fallback index '" + index + "'. Expected integer.", e);
        }
    }

    @Override
    public ArtifactStore forConfig(OperationChainConfig cfg) {
        Objects.requireNonNull(cfg, "cfg");
        Map<String, String> props = cfg.storeProps();

        CompositeArtifactStore.WriteMode writeMode = parseWriteMode(props.get("mode.write"));
        CompositeArtifactStore.ReadMode readMode = parseReadMode(props.get("mode.read"));
        boolean verifyOnRead = isTrue(props.get("verifyOnRead"));
        boolean selfHealing = isTrue(props.get("selfHealing"));
        long verificationMaxArtifactSizeBytes = parseLong(props.get("verificationMaxArtifactSizeBytes"),
                                                          ArtifactStore.DEFAULT_MAX_ARTIFACT_SIZE_BYTES,
                                                          "verificationMaxArtifactSizeBytes");

        // Primary store type declared by the pipeline configuration.
        ArtifactStore primary = resolver.resolve(cfg.storeType().name(), props, ctx);

        // Numbered fallback stores: fallback.N.type and fallback.N.props.*
        List<ArtifactStore> fallbacks = buildFallbacks(props);

        if (fallbacks.isEmpty()) {
            return primary;
        }

        return new CompositeArtifactStore(primary, fallbacks, writeMode, readMode, verifyOnRead, selfHealing,
                verificationMaxArtifactSizeBytes, asyncExec);
    }

    private record FallbackGroup(int order, Map<String, String> properties) {}

    private List<ArtifactStore> buildFallbacks(Map<String, String> props) {
        Map<String, Map<String, String>> groups = new HashMap<>();
        for (var e : props.entrySet()) {
            String k = e.getKey();
            if (!k.startsWith("fallback."))
                continue;
            String rest = k.substring("fallback.".length());
            int dot = rest.indexOf('.');
            if (dot < 0)
                continue;
            String idx = rest.substring(0, dot);
            String tail = rest.substring(dot + 1);
            groups.computeIfAbsent(idx, __ -> new HashMap<>()).put(tail, e.getValue());
        }

        var ordered = groups.entrySet().stream()
                .map(entry -> new FallbackGroup(parseFallbackIndex(entry.getKey()), entry.getValue()))
                .sorted(Comparator.comparingInt(FallbackGroup::order))
                .toList();

        List<ArtifactStore> out = new ArrayList<>();
        for (FallbackGroup fallback : ordered) {
            Map<String, String> g = fallback.properties();
            String type = opt(g, "type");
            if (type == null || type.isBlank())
                continue;

            // Convert props.* entries into the child store property map.
            Map<String, String> childProps = g.entrySet().stream()
                    .filter(en -> en.getKey().startsWith("props."))
                    .collect(Collectors.toMap(en -> en.getKey().substring("props.".length()), Map.Entry::getValue));

            out.add(resolver.resolve(type, childProps, ctx));
        }
        return out;
    }
}
