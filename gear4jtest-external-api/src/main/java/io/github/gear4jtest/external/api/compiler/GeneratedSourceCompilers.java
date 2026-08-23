package io.github.gear4jtest.external.api.compiler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Supplier;

/**
 * Factory methods for generated-source compiler implementations.
 */
public final class GeneratedSourceCompilers {
    private GeneratedSourceCompilers() {
    }

    public static GeneratedSourceCompiler jdt() {
        return jdt(contextClassLoader());
    }

    public static GeneratedSourceCompiler jdt(ClassLoader parentClassLoader) {
        return new JDTInMemoryCompiler(parentClassLoader);
    }

    public static GeneratedSourceCompiler javac() {
        return javac(contextClassLoader());
    }

    public static GeneratedSourceCompiler javac(ClassLoader parentClassLoader) {
        return new JavaxToolsGeneratedSourceCompiler(parentClassLoader);
    }

    /**
     * Returns Gear4J's built-in default compiler for generated Java source.
     *
     * <p>
     * The standard JDK {@code javax.tools.JavaCompiler} is preferred when the
     * runtime image provides it. Eclipse JDT remains the fallback for stripped
     * runtime images or deployments that do not include {@code jdk.compiler}.
     * </p>
     */
    public static GeneratedSourceCompiler defaultCompiler() {
        return defaultCompiler(contextClassLoader());
    }

    public static GeneratedSourceCompiler defaultCompiler(ClassLoader parentClassLoader) {
        ClassLoader effectiveClassLoader = parentClassLoader != null ? parentClassLoader : contextClassLoader();
        return new DefaultGeneratedSourceCompiler(effectiveClassLoader);
    }

    /**
     * Loads the only {@link GeneratedSourceCompiler} provider visible from the
     * supplied classloader. Falls back to the built-in default compiler when no
     * provider is registered and rejects ambiguous classpaths.
     */
    public static GeneratedSourceCompiler fromServiceLoader(ClassLoader classLoader) {
        return fromServiceLoader(classLoader, null);
    }

    /**
     * Loads one compiler provider by stable {@link GeneratedSourceCompiler#id()
     * id}. A non-blank id is required to disambiguate multiple providers.
     */
    public static GeneratedSourceCompiler fromServiceLoader(ClassLoader classLoader, String compilerId) {
        ClassLoader effectiveClassLoader = classLoader != null ? classLoader : contextClassLoader();
        List<GeneratedSourceCompiler> compilers = new ArrayList<>();
        ServiceLoader.load(GeneratedSourceCompiler.class, effectiveClassLoader).forEach(compilers::add);
        return selectServiceProvider(compilers, compilerId, () -> defaultCompiler(effectiveClassLoader));
    }

    static GeneratedSourceCompiler selectServiceProvider(Iterable<? extends GeneratedSourceCompiler> providers,
                                                         String compilerId,
                                                         Supplier<GeneratedSourceCompiler> defaultSupplier) {
        Objects.requireNonNull(providers, "providers must not be null");
        Objects.requireNonNull(defaultSupplier, "defaultSupplier must not be null");
        String requestedId = compilerId == null || compilerId.isBlank() ? null : compilerId.trim();
        List<GeneratedSourceCompiler> discovered = new ArrayList<>();
        providers.forEach(provider -> discovered.add(Objects.requireNonNull(provider,
                                                                            "compiler provider must not be null")));
        discovered.sort(Comparator.comparing(GeneratedSourceCompilers::requiredId));
        for (int index = 1; index < discovered.size(); index++) {
            String previousId = requiredId(discovered.get(index - 1));
            String currentId = requiredId(discovered.get(index));
            if (previousId.equals(currentId)) {
                throw new IllegalStateException("Ambiguous GeneratedSourceCompiler id=" + currentId + ": "
                        + List.of(discovered.get(index - 1).getClass().getName(),
                                  discovered.get(index).getClass().getName()));
            }
        }
        if (requestedId != null) {
            return discovered.stream()
                    .filter(provider -> requestedId.equals(requiredId(provider)))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No GeneratedSourceCompiler found with id=" + requestedId + ". Available ids: "
                                    + discovered.stream().map(GeneratedSourceCompilers::requiredId).toList()));
        }
        if (discovered.size() > 1) {
            throw new IllegalStateException("Ambiguous GeneratedSourceCompiler providers: "
                    + discovered.stream().map(GeneratedSourceCompilers::requiredId).toList()
                    + ". Select one explicitly by id.");
        }
        return discovered.isEmpty() ? defaultSupplier.get() : discovered.get(0);
    }

    private static String requiredId(GeneratedSourceCompiler compiler) {
        String id = Objects.requireNonNull(compiler.id(), "compiler id must not be null").trim();
        if (id.isEmpty()) {
            throw new IllegalStateException("GeneratedSourceCompiler id must not be blank: "
                    + compiler.getClass().getName());
        }
        return id;
    }

    private static ClassLoader contextClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader != null ? classLoader : ClassLoader.getSystemClassLoader();
    }
}
