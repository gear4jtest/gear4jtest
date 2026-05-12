package io.test.gear4jtest.external.api.storage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import io.test.gear4jtest.external.api.artifact.ArtifactStore;
import io.test.gear4jtest.external.api.artifact.CompositeArtifactStore;
import io.test.gear4jtest.external.api.model.OperationChainConfig;
import io.test.gear4jtest.external.api.spi.ArtifactStorePlugin;
import io.test.gear4jtest.external.api.spi.ArtifactStoreResolver;

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
        this.asyncExec = asyncExec != null ? asyncExec : Executors.newCachedThreadPool();
    }

    /**
     * Creates a provider with an already initialized resolver.
     */
    public DefaultArtifactStoreProvider(ArtifactStoreResolver resolver,
                                        ArtifactStorePlugin.Context ctx,
                                        Executor asyncExec) {
        this.resolver = Objects.requireNonNull(resolver);
        this.ctx = ctx != null ? ctx : key -> null;
        this.asyncExec = asyncExec != null ? asyncExec : Executors.newCachedThreadPool();
    }

    private static CompositeArtifactStore.WriteMode parseWriteMode(String s) {
        try {
            return CompositeArtifactStore.WriteMode.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return CompositeArtifactStore.WriteMode.PRIMARY_ONLY;
        }
    }

    // ---------- helpers ----------

    private static CompositeArtifactStore.ReadMode parseReadMode(String s) {
        try {
            return CompositeArtifactStore.ReadMode.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return CompositeArtifactStore.ReadMode.PREFER_PRIMARY;
        }
    }

    private static boolean isTrue(String v) {
        return v != null && (v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes") || v.equals("1"));
    }

    private static String opt(Map<String, String> m, String k) {
        return m.get(k);
    }

    @Override
    public ArtifactStore forConfig(OperationChainConfig cfg) {
        Objects.requireNonNull(cfg, "cfg");
        Map<String, String> props = cfg.storeProps();

        // Primary store type declared by the pipeline configuration.
        ArtifactStore primary = resolver.resolve(cfg.storeType().name(), props, ctx);

        // Numbered fallback stores: fallback.N.type and fallback.N.props.*
        List<ArtifactStore> fallbacks = buildFallbacks(props);

        if (fallbacks.isEmpty()) {
            return primary;
        }

        CompositeArtifactStore.WriteMode writeMode = parseWriteMode(props.getOrDefault("mode.write", "PRIMARY_ONLY"));
        CompositeArtifactStore.ReadMode readMode = parseReadMode(props.getOrDefault("mode.read", "PREFER_PRIMARY"));
        boolean verifyOnRead = isTrue(props.get("verifyOnRead"));
        boolean selfHealing = isTrue(props.get("selfHealing"));

        return new CompositeArtifactStore(primary, fallbacks, writeMode, readMode, verifyOnRead, selfHealing,
                asyncExec);
    }

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

        var ordered = groups.keySet().stream().sorted(Comparator.comparingInt(Integer::parseInt))
                .collect(Collectors.toList());

        List<ArtifactStore> out = new ArrayList<>();
        for (String idx : ordered) {
            Map<String, String> g = groups.get(idx);
            String type = opt(g, "type");
            if (type == null || type.isBlank())
                continue;

            // Convert props.* entries into the child store property map.
            Map<String, String> childProps = g.entrySet().stream().filter(en -> en.getKey().startsWith("props."))
                    .collect(Collectors.toMap(en -> en.getKey().substring("props.".length()), Map.Entry::getValue));

            out.add(resolver.resolve(type, childProps, ctx));
        }
        return out;
    }
}
