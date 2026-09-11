package io.github.gear4jtest.xml2java

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import javax.tools.ToolProvider
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.regex.Pattern

import static org.assertj.core.api.Assertions.assertThat

class XmlAssemblyLineOperatorClasspathFunctionalTest {
    @TempDir
    Path projectDirectory

    @Test
    void consumer_shouldCompileWithAnExternalOperatorAndInvalidateOnClasspathChanges() {
        // Given: this operator is not on the plugin-under-test classpath.
        writeBuild("implementation files('operators.jar')")
        writeOperatorJar('first')
        writePipeline()

        // When
        def first = runner('clean', 'compileJava').build()

        // Then
        assertThat(first.task(':xmlGenerateAssemblyLine').outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(first.task(':compileJava').outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(generatedSource()).exists()
        assertThat(Files.readString(generatedSource())).contains('BusinessOperator.class')
        def unchanged = runner('compileJava').build()
        assertThat(unchanged.task(':xmlGenerateAssemblyLine').outcome).isEqualTo(TaskOutcome.UP_TO_DATE)

        // When: change bytecode only, leaving XML, the class name and its ABI unchanged.
        writeOperatorJar('second')
        def changed = runner('compileJava').build()

        // Then: @Classpath, not ABI-only @CompileClasspath, must invalidate generation.
        assertThat(changed.task(':xmlGenerateAssemblyLine').outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(changed.output).contains('Reusing configuration cache.')
    }

    @Test
    void consumer_shouldCompileAndPackageSameProjectOperatorsWithoutATaskCycle() {
        // Given
        writeBuild('')
        Path operator = projectDirectory.resolve('src/gear4jOperators/java/fixture/BusinessOperator.java')
        Files.createDirectories(operator.parent)
        Files.writeString(operator, operatorSource('local'))
        Path testConsumer = projectDirectory.resolve('src/test/java/fixture/OperatorConsumer.java')
        Files.createDirectories(testConsumer.parent)
        Files.writeString(testConsumer, '''
package fixture;
class OperatorConsumer { BusinessOperator operator = new BusinessOperator(); }
''')
        writePipeline()

        // When
        def result = runner('clean', 'jar', 'sourcesJar', 'compileTestJava').build()

        // Then
        def tasks = result.tasks*.path
        assertThat(tasks.indexOf(':compileGear4jOperatorsJava')).isLessThan(tasks.indexOf(':xmlGenerateAssemblyLine'))
        assertThat(tasks.indexOf(':xmlGenerateAssemblyLine')).isLessThan(tasks.indexOf(':compileJava'))
        assertThat(result.task(':compileTestJava').outcome).isEqualTo(TaskOutcome.SUCCESS)
        new java.util.zip.ZipFile(projectDirectory.resolve('build/libs/operator-consumer.jar').toFile())
            .withCloseable { archive ->
                assertThat(archive.getEntry('fixture/BusinessOperator.class')).isNotNull()
                assertThat(archive.getEntry('io/github/gear4jtest/xml/generated/ConsumerLine.class')).isNotNull()
            }
        new java.util.zip.ZipFile(projectDirectory.resolve('build/libs/operator-consumer-sources.jar').toFile())
            .withCloseable { archive ->
                assertThat(archive.getEntry('fixture/BusinessOperator.java')).isNotNull()
            }
    }

    @Test
    void consumer_shouldKeepPreviousGeneratedSourcesWhenAnOperatorDisappears() {
        // Given
        writeBuild("implementation files('operators.jar')")
        writeOperatorJar('first')
        writePipeline()
        runner('compileJava').build()
        String previous = Files.readString(generatedSource())

        // When
        new JarOutputStream(Files.newOutputStream(projectDirectory.resolve('operators.jar'))).close()
        def result = runner('compileJava').buildAndFail()

        // Then
        assertThat(result.task(':xmlGenerateAssemblyLine').outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output).contains("Unable to load operator class 'fixture.BusinessOperator'")
        assertThat(Files.readString(generatedSource())).isEqualTo(previous)
    }

    private void writeBuild(String extraDependencies) {
        Files.writeString(projectDirectory.resolve('settings.gradle'), "rootProject.name = 'operator-consumer'\n")
        // Reuse already resolved plugin dependencies so the consumer needs no repository/network.
        String dependencies = pluginClasspath().collect { quote(it) }.join(', ')
        Files.writeString(projectDirectory.resolve('build.gradle'), """
plugins {
    id 'java'
    id 'io.github.gear4jtest.xml2java'
}
java { withSourcesJar() }
dependencies {
    implementation files(${dependencies})
    ${extraDependencies}
}
xmlAssemblyLineGenerator {
    operatorCapability('business', 'fixture.BusinessOperator')
}
""".stripIndent())
    }

    private void writePipeline() {
        Path pipeline = projectDirectory.resolve('src/main/gear4j/pipeline.xml')
        Files.createDirectories(pipeline.parent)
        Files.writeString(pipeline, '''<?xml version="1.0" encoding="UTF-8"?>
<assemblyLine xmlns="http://github.com/gear4jtest/core/model"
              id="consumer" inputType="java.lang.String" outputType="java.lang.String">
  <operations>
    <processingOperation id="business" type="business"/>
  </operations>
</assemblyLine>
''')
    }

    private void writeOperatorJar(String marker) {
        Path source = projectDirectory.resolve('fixture-source/fixture/BusinessOperator.java')
        Path classes = projectDirectory.resolve('fixture-classes')
        Files.createDirectories(source.parent)
        Files.createDirectories(classes)
        Files.writeString(source, operatorSource(marker))
        def diagnostics = new ByteArrayOutputStream()
        def arguments = ['--release', '17', '-cp', pluginClasspath().join(File.pathSeparator),
                         '-d', classes.toString(), source.toString()]
        int result = ToolProvider.systemJavaCompiler.run(null, diagnostics, diagnostics, arguments as String[])
        assertThat(result).as(diagnostics.toString('UTF-8')).isZero()
        new JarOutputStream(Files.newOutputStream(projectDirectory.resolve('operators.jar'))).withCloseable { jar ->
            jar.putNextEntry(new JarEntry('fixture/BusinessOperator.class'))
            Files.copy(classes.resolve('fixture/BusinessOperator.class'), jar)
            jar.closeEntry()
        }
    }

    private static String operatorSource(String marker) {
        return """
package fixture;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
public final class BusinessOperator implements Operator<String, String> {
    public String transform(String input, StationExecutionContext context) { return input + "${marker}"; }
}
""".stripIndent()
    }

    private static List<String> pluginClasspath() {
        Properties metadata = new Properties()
        XmlAssemblyLineOperatorClasspathFunctionalTest.class.classLoader
            .getResourceAsStream('plugin-under-test-metadata.properties').withCloseable { metadata.load(it) }
        return metadata.getProperty('implementation-classpath').split(Pattern.quote(File.pathSeparator)).toList()
    }

    private static String quote(String path) {
        return "'" + path.replace('\\', '\\\\').replace("'", "\\'") + "'"
    }

    private GradleRunner runner(String... tasks) {
        return GradleRunner.create().withProjectDir(projectDirectory.toFile()).withPluginClasspath()
            .withArguments(tasks.toList() + ['--offline', '--configuration-cache',
                '--configuration-cache-problems=fail', '--max-workers=2', '--stacktrace'])
    }

    private Path generatedSource() {
        return projectDirectory.resolve(
            'build/generated/sources/gear4j/xml2java/main/io/github/gear4jtest/xml/generated/ConsumerLine.java')
    }
}
