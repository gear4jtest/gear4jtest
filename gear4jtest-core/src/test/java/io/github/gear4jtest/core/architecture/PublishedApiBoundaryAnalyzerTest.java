package io.github.gear4jtest.core.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

final class PublishedApiBoundaryAnalyzerTest {
    @Test
    void findViolations_shouldInspectPublicSignaturesAndIgnoreImplementationImports(@TempDir Path repositoryRoot)
            throws IOException {
        // Given
        writeSource(repositoryRoot,
                    "sample-module/src/main/java/sample/internal/package-info.java",
                    """
                            @io.github.gear4jtest.core.api.annotation.Internal
                            package sample.internal;
                            """);
        writeSource(repositoryRoot,
                    "sample-module/src/main/java/sample/internal/InternalType.java",
                    """
                            package sample.internal;

                            public final class InternalType {
                            }
                            """);
        writeSource(repositoryRoot,
                    "sample-module/src/main/java/sample/api/package-info.java",
                    """
                            @io.github.gear4jtest.core.api.annotation.PublicApi
                            package sample.api;
                            """);
        writeSource(repositoryRoot,
                    "sample-module/src/main/java/sample/api/SafeApi.java",
                    """
                            package sample.api;

                            import sample.internal.InternalType;

                            public final class SafeApi {
                                private InternalType createInternalValue() {
                                    return null;
                                }
                            }
                            """);
        writeSource(repositoryRoot,
                    "sample-module/src/main/java/sample/api/LeakyApi.java",
                    """
                            package sample.api;

                            import sample.internal.InternalType;

                            public interface LeakyApi {
                                InternalType leak();
                            }
                            """);

        // When
        List<String> violations = PublishedApiBoundaryAnalyzer.findViolations(
                                                                              repositoryRoot,
                                                                              List.of("sample-module"));

        // Then
        assertThat(violations).singleElement()
                .satisfies(violation -> {
                    assertThat(violation).contains("LeakyApi.java");
                    assertThat(violation).contains("LeakyApi#leak()");
                    assertThat(violation).contains("sample.internal.InternalType");
                });
    }

    @Test
    void findViolations_shouldHonorTypeAndMemberInternalMarkers(@TempDir Path repositoryRoot) throws IOException {
        // Given
        writeSource(repositoryRoot,
                    "sample-module/src/main/java/sample/api/package-info.java",
                    """
                            @io.github.gear4jtest.core.api.annotation.PublicApi
                            package sample.api;
                            """);
        writeSource(repositoryRoot,
                    "sample-module/src/main/java/sample/api/InternalHelper.java",
                    """
                            package sample.api;

                            @io.github.gear4jtest.core.api.annotation.Internal
                            public final class InternalHelper {
                            }
                            """);
        writeSource(repositoryRoot,
                    "sample-module/src/main/java/sample/api/PublicFacade.java",
                    """
                            package sample.api;

                            public interface PublicFacade {
                                InternalHelper leak();

                                @io.github.gear4jtest.core.api.annotation.Internal
                                InternalHelper internalHook();
                            }
                            """);

        // When
        List<String> violations = PublishedApiBoundaryAnalyzer.findViolations(
                                                                              repositoryRoot,
                                                                              List.of("sample-module"));

        // Then
        assertThat(violations).singleElement()
                .satisfies(violation -> {
                    assertThat(violation).contains("PublicFacade#leak()");
                    assertThat(violation).doesNotContain("internalHook");
                });
    }

    private static void writeSource(Path repositoryRoot, String relativePath, String source) throws IOException {
        Path sourcePath = repositoryRoot.resolve(relativePath);
        Files.createDirectories(sourcePath.getParent());
        Files.writeString(sourcePath, source);
    }
}
