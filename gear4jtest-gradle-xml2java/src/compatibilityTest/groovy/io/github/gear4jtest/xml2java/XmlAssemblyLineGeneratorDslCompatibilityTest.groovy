package io.github.gear4jtest.xml2java

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path

import static org.assertj.core.api.Assertions.assertThat

class XmlAssemblyLineGeneratorDslCompatibilityTest {
    private static final String CANONICAL_PLUGIN_ID = 'io.github.gear4jtest.xml2java'
    private static final String LEGACY_PLUGIN_ID = 'io.github.gear4jtest.gradle.xml2java'

    @TempDir
    Path testDirectory

    @Test
    void version_1_0_fixture_shouldRemainCompatibleWithCanonicalPluginId() {
        verifyFixture(CANONICAL_PLUGIN_ID, 'canonical')
    }

    @Test
    void version_1_0_fixture_shouldRemainCompatibleWithLegacyPluginId() {
        verifyFixture(LEGACY_PLUGIN_ID, 'legacy')
    }

    private void verifyFixture(String pluginId, String fixtureName) {
        // Given
        Path projectDirectory = testDirectory.resolve(fixtureName)
        Files.createDirectories(projectDirectory.resolve('src/main/gear4j'))
        writeResource(projectDirectory.resolve('settings.gradle'), 'compatibility/1.0/settings.gradle')
        String buildScript = readResource('compatibility/1.0/build.gradle')
            .replace('@PLUGIN_ID@', pluginId)
        Files.writeString(projectDirectory.resolve('build.gradle'), buildScript)
        writeResource(
            projectDirectory.resolve('src/main/gear4j/compatibility-line.xml'),
            'compatibility/1.0/compatibility-line.xml'
        )
        List<String> arguments = [
            'xmlGenerateAssemblyLine',
            '--configuration-cache',
            '--configuration-cache-problems=fail',
            '--no-build-cache',
            '--warning-mode=all',
            '--stacktrace'
        ]

        // When
        BuildResult first = runner(projectDirectory, arguments).build()
        Path generated = projectDirectory.resolve(
            'build/generated-compatibility/io/github/gear4jtest/xml/generated/Compatibility_lineLine.java'
        )

        // Then
        assertThat(first.task(':xmlGenerateAssemblyLine').outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(generated).exists()

        // When
        BuildResult second = runner(projectDirectory, arguments).build()

        // Then
        assertThat(second.task(':xmlGenerateAssemblyLine').outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        assertThat(second.output).contains('Reusing configuration cache.')
        assertThat(generated).exists()
    }

    private GradleRunner runner(Path projectDirectory, List<String> arguments) {
        return GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withPluginClasspath()
            .withArguments(arguments)
    }

    private static void writeResource(Path destination, String resourceName) {
        Files.writeString(destination, readResource(resourceName))
    }

    private static String readResource(String resourceName) {
        InputStream resource = XmlAssemblyLineGeneratorDslCompatibilityTest.classLoader
            .getResourceAsStream(resourceName)
        if (resource == null) {
            throw new IllegalStateException("Missing compatibility fixture resource: ${resourceName}")
        }
        resource.withCloseable { input -> new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8) }
    }
}
