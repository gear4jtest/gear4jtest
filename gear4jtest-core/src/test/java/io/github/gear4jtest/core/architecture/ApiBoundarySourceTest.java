package io.github.gear4jtest.core.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

final class ApiBoundarySourceTest {
    private static final List<String> MODULES_WITH_STABILITY_MARKERS = List.of(
                                                                               "gear4jtest-core",
                                                                               "gear4jtest-external-api",
                                                                               "gear4jtest-jdbc",
                                                                               "gear4jtest-xml");
    private static final Set<String> STABILITY_MARKERS = Set.of(
                                                                "@io.github.gear4jtest.core.api.annotation.PublicApi",
                                                                "@io.github.gear4jtest.core.api.annotation.Spi",
                                                                "@io.github.gear4jtest.core.api.annotation.Internal",
                                                                "@io.github.gear4jtest.core.api.annotation.Experimental");
    private static final List<String> KNOWN_CORE_API_SPI_INTERNAL_DEPENDENCIES = List.of(
                                                                                         "io/github/gear4jtest/core/api/ExecutionResult.java -> execution",
                                                                                         "io/github/gear4jtest/core/api/assemblyline/NestedRunContext.java -> execution",
                                                                                         "io/github/gear4jtest/core/api/config/FlowDecider.java -> execution",
                                                                                         "io/github/gear4jtest/core/api/context/DefaultStationExecutionContext.java -> engine, execution",
                                                                                         "io/github/gear4jtest/core/api/context/ExecutionContext.java -> execution",
                                                                                         "io/github/gear4jtest/core/api/context/ResolvedParameters.java -> engine",
                                                                                         "io/github/gear4jtest/core/api/context/StationContextUtils.java -> engine",
                                                                                         "io/github/gear4jtest/core/api/context/StationExecutionContext.java -> engine, execution",
                                                                                         "io/github/gear4jtest/core/api/station/UnaryWorkStation.java -> engine",
                                                                                         "io/github/gear4jtest/core/api/station/WorkStation.java -> engine",
                                                                                         "io/github/gear4jtest/core/api/util/Concurrency.java -> engine",
                                                                                         "io/github/gear4jtest/core/api/util/Persistence.java -> execution",
                                                                                         "io/github/gear4jtest/core/persistence/AssemblyRunRecord.java -> execution",
                                                                                         "io/github/gear4jtest/core/persistence/StationLogRecord.java -> execution",
                                                                                         "io/github/gear4jtest/core/sidecompute/SideComputer.java -> execution",
                                                                                         "io/github/gear4jtest/core/spi/extension/AbstractStationHooksExtension.java -> execution",
                                                                                         "io/github/gear4jtest/core/spi/extension/RunLifecycleExtension.java -> execution",
                                                                                         "io/github/gear4jtest/core/spi/runner/StationRunner.java -> execution");

    @Test
    void productionPackages_shouldDeclareExactlyOneStabilityMarker() throws IOException {
        Path repositoryRoot = findRepositoryRoot();
        List<String> violations = new ArrayList<>();

        for (String module : MODULES_WITH_STABILITY_MARKERS) {
            Path sourceRoot = repositoryRoot.resolve(module).resolve("src/main/java");
            if (!Files.isDirectory(sourceRoot)) {
                continue;
            }

            for (Path packageDirectory : productionPackageDirectories(sourceRoot)) {
                Path packageInfo = packageDirectory.resolve("package-info.java");
                if (!Files.isRegularFile(packageInfo)) {
                    violations.add(sourceRoot.relativize(packageDirectory) + " has no package-info.java");
                    continue;
                }

                long markerCount = Files.readAllLines(packageInfo).stream()
                        .map(String::trim)
                        .filter(STABILITY_MARKERS::contains)
                        .count();
                if (markerCount != 1L) {
                    violations.add(sourceRoot.relativize(packageInfo)
                            + " must declare exactly one API stability marker but declares " + markerCount);
                }
            }
        }

        assertThat(violations).isEmpty();
    }

    @Test
    void corePublicApiAndSpiInternalDependencies_shouldNotGrowSilently() throws IOException {
        Path sourceRoot = findRepositoryRoot().resolve("gear4jtest-core/src/main/java");

        List<String> currentDependencies = new ArrayList<>();
        for (Path sourceFile : javaSources(sourceRoot)) {
            if (sourceFile.getFileName().toString().equals("package-info.java")) {
                continue;
            }
            StabilityMarker marker = stabilityMarker(sourceFile.getParent());
            if (marker != StabilityMarker.PUBLIC_API && marker != StabilityMarker.SPI) {
                continue;
            }

            String source = Files.readString(sourceFile);
            if (isInternalType(source)) {
                continue;
            }

            List<String> dependencies = internalDependencyGroups(source);
            if (!dependencies.isEmpty()) {
                currentDependencies.add(sourceRoot.relativize(sourceFile) + " -> "
                        + String.join(", ", dependencies));
            }
        }

        currentDependencies.sort(Comparator.naturalOrder());
        assertThat(currentDependencies).containsExactlyElementsOf(KNOWN_CORE_API_SPI_INTERNAL_DEPENDENCIES);
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("gear4jtest-core"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("gear4jtest-core"))) {
            return parent;
        }
        throw new IllegalStateException("Unable to locate Gear4J repository root from " + current);
    }

    private static List<Path> productionPackageDirectories(Path sourceRoot) throws IOException {
        try (Stream<Path> sourceFiles = javaSourcesStream(sourceRoot)) {
            return sourceFiles.filter(source -> !source.getFileName().toString().equals("package-info.java"))
                    .map(Path::getParent)
                    .distinct()
                    .sorted()
                    .toList();
        }
    }

    private static List<Path> javaSources(Path sourceRoot) throws IOException {
        try (Stream<Path> sourceFiles = javaSourcesStream(sourceRoot)) {
            return sourceFiles.sorted().toList();
        }
    }

    private static Stream<Path> javaSourcesStream(Path sourceRoot) throws IOException {
        return Files.walk(sourceRoot).filter(path -> Files.isRegularFile(path)
                && path.getFileName().toString().endsWith(".java"));
    }

    private static StabilityMarker stabilityMarker(Path packageDirectory) throws IOException {
        Path packageInfo = packageDirectory.resolve("package-info.java");
        if (!Files.isRegularFile(packageInfo)) {
            return StabilityMarker.NONE;
        }
        List<String> annotationLines = Files.readAllLines(packageInfo).stream()
                .map(String::trim)
                .filter(STABILITY_MARKERS::contains)
                .toList();
        if (annotationLines.size() != 1) {
            return StabilityMarker.NONE;
        }
        String marker = annotationLines.get(0);
        if (marker.endsWith(".PublicApi")) {
            return StabilityMarker.PUBLIC_API;
        }
        if (marker.endsWith(".Spi")) {
            return StabilityMarker.SPI;
        }
        if (marker.endsWith(".Internal")) {
            return StabilityMarker.INTERNAL;
        }
        if (marker.endsWith(".Experimental")) {
            return StabilityMarker.EXPERIMENTAL;
        }
        return StabilityMarker.NONE;
    }

    private static boolean isInternalType(String source) {
        return source.contains("\n@Internal\npublic")
                || source.contains("\n@io.github.gear4jtest.core.api.annotation.Internal\npublic");
    }

    private static List<String> internalDependencyGroups(String source) {
        List<String> dependencies = new ArrayList<>();
        if (source.contains("io.github.gear4jtest.core.engine.")) {
            dependencies.add("engine");
        }
        if (source.contains("io.github.gear4jtest.core.execution.")) {
            dependencies.add("execution");
        }
        if (source.contains("io.github.gear4jtest.core.internal.")) {
            dependencies.add("internal");
        }
        return dependencies;
    }

    private enum StabilityMarker {
        PUBLIC_API, SPI, INTERNAL, EXPERIMENTAL, NONE
    }
}
