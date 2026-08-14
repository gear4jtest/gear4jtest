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
                                                                               "gear4jtest-gradle-xml2java",
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
            Path moduleRoot = repositoryRoot.resolve(module);
            List<Path> sourceRoots = productionSourceRoots(moduleRoot);
            if (sourceRoots.isEmpty()) {
                continue;
            }

            for (Path packageDirectory : productionPackageDirectories(sourceRoots)) {
                List<Path> packageInfos = sourceRoots.stream()
                        .map(sourceRoot -> sourceRoot.resolve(packageDirectory).resolve("package-info.java"))
                        .filter(Files::isRegularFile)
                        .toList();
                if (packageInfos.isEmpty()) {
                    violations.add(module + "/" + packageDirectory + " has no package-info.java");
                    continue;
                }

                long markerCount = 0L;
                for (Path packageInfo : packageInfos) {
                    markerCount += Files.readAllLines(packageInfo).stream()
                            .map(String::trim)
                            .filter(STABILITY_MARKERS::contains)
                            .count();
                }
                if (markerCount != 1L) {
                    violations.add(module + "/" + packageDirectory + "/package-info.java"
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

    @Test
    void externalApi_shouldConfineEclipseCompilerInternalsToTheInternalJdtAdapter() throws IOException {
        Path sourceRoot = findRepositoryRoot().resolve("gear4jtest-external-api/src/main/java");
        Path allowedAdapter = sourceRoot.resolve(
                                                 "io/github/gear4jtest/external/api/compiler/JDTInMemoryCompiler.java");
        List<String> violations = new ArrayList<>();

        for (Path sourceFile : javaSources(sourceRoot)) {
            if (!sourceFile.equals(allowedAdapter)
                    && Files.readString(sourceFile).contains("import org.eclipse.jdt.internal.")) {
                violations.add(sourceRoot.relativize(sourceFile).toString());
            }
        }

        assertThat(violations).isEmpty();
        assertThat(Files.readString(allowedAdapter))
                .contains("@Internal")
                .contains("final class JDTInMemoryCompiler");
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

    private static List<Path> productionSourceRoots(Path moduleRoot) {
        return List.of(moduleRoot.resolve("src/main/java"), moduleRoot.resolve("src/main/groovy")).stream()
                .filter(Files::isDirectory)
                .toList();
    }

    private static List<Path> productionPackageDirectories(List<Path> sourceRoots) throws IOException {
        List<Path> packageDirectories = new ArrayList<>();
        for (Path sourceRoot : sourceRoots) {
            try (Stream<Path> sourceFiles = productionSourcesStream(sourceRoot)) {
                sourceFiles.filter(source -> !source.getFileName().toString().equals("package-info.java"))
                        .map(Path::getParent)
                        .map(sourceRoot::relativize)
                        .forEach(packageDirectories::add);
            }
        }
        return packageDirectories.stream().distinct().sorted().toList();
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

    private static Stream<Path> productionSourcesStream(Path sourceRoot) throws IOException {
        return Files.walk(sourceRoot).filter(path -> Files.isRegularFile(path)
                && (path.getFileName().toString().endsWith(".java")
                        || path.getFileName().toString().endsWith(".groovy")));
    }

}
