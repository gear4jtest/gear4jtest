package io.github.gear4jtest.buildlogic

import static org.assertj.core.api.Assertions.assertThat

import java.nio.file.Files
import java.nio.file.Path

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class BuildConventionsFunctionalTest {

    @TempDir
    Path projectDirectory

    @Test
    void conventionsPreserveTheJavaQualityAndIntegrationTestContractWithoutTargetVersionCatalog() {
        write('settings.gradle', "rootProject.name = 'convention-fixture'\n")
        write('LICENSE', 'fixture license\n')
        write('NOTICE', 'fixture notice\n')
        write('build.gradle', '''
plugins {
    id 'gear4j.root-quality'
    id 'gear4j.java-library'
    id 'gear4j.quality'
    id 'gear4j.integration-test'
}

ext.moduleName = 'io.github.gear4jtest.fixture'

tasks.register('probeJavaExec', JavaExec)

tasks.register('verifyConventionModel') {
    doLast {
        assert plugins.hasPlugin('java-library')
        assert plugins.hasPlugin('maven-publish')
        assert plugins.hasPlugin('checkstyle')
        assert plugins.hasPlugin('com.diffplug.spotless')
        assert tasks.findByName('spotlessRootMiscCheck') != null
        assert java.toolchain.languageVersion.get().asInt() == 17

        def compileJava = tasks.named('compileJava', JavaCompile).get()
        assert compileJava.options.release.get() == 17
        assert compileJava.options.encoding == 'UTF-8'
        assert compileJava.options.deprecation
        assert compileJava.options.compilerArgs.containsAll(['-Xlint:unchecked', '-parameters'])

        def testTask = tasks.named('test', Test).get()
        assert testTask.javaLauncher.get().metadata.languageVersion.asInt() == 17
        assert tasks.named('probeJavaExec', JavaExec).get()
            .javaLauncher.get().metadata.languageVersion.asInt() == 17

        def jarTask = tasks.named('jar', Jar).get()
        assert !jarTask.preserveFileTimestamps
        assert jarTask.reproducibleFileOrder
        assert jarTask.manifest.attributes['Automatic-Module-Name'] == moduleName

        def integrationSourceSet = sourceSets.named('integrationTest').get()
        assert integrationSourceSet.java.srcDirs == [file('src/integrationTest/java')] as Set
        assert integrationSourceSet.resources.srcDirs == [file('src/integrationTest/resources')] as Set
        assert configurations.integrationTestImplementation.extendsFrom
            .contains(configurations.testImplementation)
        assert configurations.integrationTestRuntimeOnly.extendsFrom
            .contains(configurations.testRuntimeOnly)

        def integrationTask = tasks.named('integrationTest', Test).get()
        assert integrationTask.group == 'verification'
        assert integrationTask.description ==
            'Runs integration tests. Database-dependent tests use Testcontainers.'
        assert integrationTask.testClassesDirs.files == integrationSourceSet.output.classesDirs.files
        assert integrationTask.javaLauncher.get().metadata.languageVersion.asInt() == 17

        def checkTask = tasks.named('check').get()
        assert checkTask.taskDependencies.getDependencies(checkTask)
            .contains(tasks.named('spotlessCheck').get())
        assert tasks.named('checkstyleMain', Checkstyle).get().reports.xml.required.get()
        assert tasks.named('checkstyleMain', Checkstyle).get().reports.html.required.get()
    }
}
'''.stripIndent())

        BuildResult result = GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withArguments('verifyConventionModel', '--stacktrace', '--warning-mode=all')
            .withPluginClasspath()
            .build()

        assertThat(result.task(':verifyConventionModel').outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    private void write(String relativePath, String content) {
        Path destination = projectDirectory.resolve(relativePath)
        Files.createDirectories(destination.parent)
        Files.writeString(destination, content)
    }
}
