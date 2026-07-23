package io.github.gear4jtest.xml2java

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path

import static org.assertj.core.api.Assertions.assertThat

class XmlAssemblyLineGeneratorFunctionalTest {
    @TempDir
    Path projectDirectory

    @Test
    void generation_shouldReuseConfigurationCacheAndRestoreOutputsFromBuildCache() {
        // Given
        writeBuild()
        writePipeline('simple_line')
        def arguments = generationArguments()

        // When
        BuildResult first = runner(arguments).build()
        Path outputRoot = generatedSourceRoot()
        Path generated = outputRoot.resolve('io/github/gear4jtest/xml/generated/Simple_lineLine.java')

        // Then
        assertThat(first.task(':xmlGenerateAssemblyLine').outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(generated).exists()

        // When
        deleteRecursively(outputRoot)
        BuildResult second = runner(arguments).build()

        // Then
        assertThat(second.task(':xmlGenerateAssemblyLine').outcome).isEqualTo(TaskOutcome.FROM_CACHE)
        assertThat(second.output).contains('Reusing configuration cache.')
        assertThat(generated).exists()
    }

    @Test
    void generation_shouldInvalidateTaskWhenXmlInputChangesAndReplaceOldOutputs() {
        // Given
        writeBuild()
        Path pipeline = writePipeline('simple_line')
        def arguments = generationArguments()
        BuildResult first = runner(arguments).build()
        assertThat(first.task(':xmlGenerateAssemblyLine').outcome).isEqualTo(TaskOutcome.SUCCESS)
        Path oldGenerated = generatedSourceRoot()
            .resolve('io/github/gear4jtest/xml/generated/Simple_lineLine.java')
        assertThat(oldGenerated).exists()

        // When
        Files.writeString(pipeline, pipelineXml('changed_line'))
        BuildResult second = runner(arguments).build()

        // Then
        assertThat(second.task(':xmlGenerateAssemblyLine').outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(second.output).contains('Reusing configuration cache.')
        assertThat(oldGenerated).doesNotExist()
        assertThat(generatedSourceRoot()
            .resolve('io/github/gear4jtest/xml/generated/Changed_lineLine.java')).exists()
    }

    private void writeBuild() {
        Files.writeString(projectDirectory.resolve('settings.gradle'), "rootProject.name = 'functional-test'\n")
        Files.writeString(projectDirectory.resolve('build.gradle'), '''
plugins {
    id 'java'
    id 'io.github.gear4jtest.xml2java'
}

'''.stripIndent())
    }

    private Path writePipeline(String id) {
        Path pipeline = projectDirectory.resolve('src/main/gear4j/pipeline.xml')
        Files.createDirectories(pipeline.parent)
        Files.writeString(pipeline, pipelineXml(id))
        return pipeline
    }

    private static String pipelineXml(String id) {
        return """<?xml version="1.0" encoding="UTF-8"?>
<assemblyLine xmlns="http://github.com/gear4jtest/core/model"
              id="${id}"
              inputType="java.lang.String"
              outputType="java.lang.String">
  <operations>
    <signal id="stop" type="STOP" inputType="java.lang.String"/>
  </operations>
</assemblyLine>
"""
    }

    private List<String> generationArguments() {
        return [
            'xmlGenerateAssemblyLine',
            '--configuration-cache',
            '--configuration-cache-problems=fail',
            '--build-cache',
            '--warning-mode=all',
            '--stacktrace'
        ]
    }

    private GradleRunner runner(List<String> arguments) {
        return GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withPluginClasspath()
            .withTestKitDir(projectDirectory.resolve('.test-kit').toFile())
            .withArguments(arguments)
    }

    private Path generatedSourceRoot() {
        return projectDirectory.resolve('build/generated/sources/gear4j/xml2java/main')
    }

    private static void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return
        }
        def paths = Files.walk(root)
        try {
            paths.sorted(Comparator.reverseOrder()).forEach { path -> Files.delete(path) }
        } finally {
            paths.close()
        }
    }
}
