package io.github.gear4jtest.core.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

final class ApiBoundarySourceTest {
    private static final List<String> MODULES_WITH_STABILITY_MARKERS = List.of(
                                                                               "gear4jtest-core",
                                                                               "gear4jtest-experimental-cache",
                                                                               "gear4jtest-external-api",
                                                                               "gear4jtest-external-jdbc",
                                                                               "gear4jtest-jackson",
                                                                               "gear4jtest-jdbc",
                                                                               "gear4jtest-micrometer",
                                                                               "gear4jtest-spring",
                                                                               "gear4jtest-spring-boot-starter",
                                                                               "gear4jtest-xml");
    private static final Set<String> STABILITY_MARKERS = Set.of(
                                                                "@io.github.gear4jtest.core.api.annotation.PublicApi",
                                                                "@io.github.gear4jtest.core.api.annotation.Spi",
                                                                "@io.github.gear4jtest.core.api.annotation.Internal",
                                                                "@io.github.gear4jtest.core.api.annotation.Experimental");

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
    void publishedPublicApiAndSpiSignatures_shouldNotDependOnInternalTypes() throws IOException {
        List<String> violations = PublishedApiBoundaryAnalyzer.findViolations(
                                                                              findRepositoryRoot(),
                                                                              MODULES_WITH_STABILITY_MARKERS);

        assertThat(violations).isEmpty();
    }

    @Test
    void providerNeutralExternalApi_shouldNotImportJdbcPackages() throws IOException {
        Path sourceRoot = findRepositoryRoot().resolve("gear4jtest-external-api/src/main/java");
        List<String> violations = new ArrayList<>();

        for (Path sourceFile : javaSources(sourceRoot)) {
            String source = Files.readString(sourceFile);
            if (source.contains("import java.sql.") || source.contains("import javax.sql.")) {
                violations.add(sourceRoot.relativize(sourceFile).toString());
            }
        }

        assertThat(violations).isEmpty();
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

}
