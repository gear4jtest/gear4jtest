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
    void conventionsPreserveTheJavaQualityTestCoverageAndBenchmarkContractWithoutTargetVersionCatalog() {
        write('settings.gradle', "rootProject.name = 'convention-fixture'\n")
        write('LICENSE', 'fixture license\n')
        write('NOTICE', 'fixture notice\n')
        write('build.gradle', '''
plugins {
    id 'gear4j.root-quality'
    id 'gear4j.java-library'
    id 'gear4j.quality'
    id 'gear4j.test-suite'
    id 'gear4j.benchmark'
}

ext.moduleName = 'io.github.gear4jtest.fixture'

tasks.register('probeJavaExec', JavaExec)

tasks.register('verifyConventionModel') {
    doLast {
        assert plugins.hasPlugin('java-library')
        assert plugins.hasPlugin('maven-publish')
        assert plugins.hasPlugin('checkstyle')
        assert plugins.hasPlugin('com.diffplug.spotless')
        assert plugins.hasPlugin('jacoco')
        assert plugins.hasPlugin('me.champeau.jmh')
        assert tasks.findByName('spotlessRootMiscCheck') != null
        assert java.toolchain.languageVersion.get().asInt() == 17
        assert jacoco.toolVersion == '0.8.14'

        def compileJava = tasks.named('compileJava', JavaCompile).get()
        assert compileJava.options.release.get() == 17
        assert compileJava.options.encoding == 'UTF-8'
        assert compileJava.options.deprecation
        assert compileJava.options.compilerArgs.containsAll(['-Xlint:unchecked', '-parameters'])

        def testTask = tasks.named('test', Test).get()
        assert testTask.javaLauncher.get().metadata.languageVersion.asInt() == 17
        assert testTask.systemProperties['junit.jupiter.execution.timeout.default'] == '2 m'
        assert testTask.systemProperties['junit.jupiter.execution.timeout.lifecycle.method.default'] == '5 m'
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
        assert integrationTask.systemProperties['junit.jupiter.execution.timeout.default'] == '2 m'
        assert integrationTask.systemProperties['junit.jupiter.execution.timeout.lifecycle.method.default'] == '5 m'

        def jacocoReport = tasks.named('jacocoTestReport').get()
        assert jacocoReport.reports.xml.required.get()

        assert jmh.jmhVersion.get() == '1.37'
        assert jmh.warmupIterations.get() == 2
        assert jmh.iterations.get() == 3
        assert jmh.fork.get() == 1
        assert jmh.threads.get() == 1
        assert jmh.timeOnIteration.get() == '1s'
        assert jmh.failOnError.get()
        assert jmh.jvmArgs.get() == ['-Xms256m', '-Xmx1024m']
        assert jmh.resultFormat.get() == 'JSON'
        assert jmh.profilers.get() == ['gc', 'io.github.gear4jtest.benchmark.LiveThreadProfiler']

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

    @Test
    void rootConventionsPreserveCoverageRatchetAndPerformanceBudgetTasks() {
        write('settings.gradle', '''
rootProject.name = 'root-convention-fixture'
include 'gear4jtest-core'
'''.stripIndent())
        write('LICENSE', 'fixture license\n')
        write('NOTICE', 'fixture notice\n')
        write('config/module-coverage-thresholds.json', '''
{
  "schemaVersion": 1,
  "modules": [
    {
      "projectPath": ":gear4jtest-core",
      "minimumLineRatio": 0.50
    }
  ]
}
'''.stripIndent())
        write('config/critical-coverage-thresholds.json', '''
{
  "schemaVersion": 1,
  "classes": [
    {
      "className": "fixture.CriticalPath",
      "minimumBranchRatio": 0.50
    }
  ]
}
'''.stripIndent())
        write('config/performance-budgets.json', '''
{
  "schemaVersion": 1,
  "scoreUnits": {},
  "defaults": {
    "maximumHeapUsedBytes": 1
  },
  "benchmarks": []
}
'''.stripIndent())
        write('build.gradle', '''
plugins {
    id 'gear4j.root-coverage'
    id 'gear4j.root-performance'
}

tasks.register('verifyRootConventionModel') {
    doLast {
        assert plugins.hasPlugin('base')
        assert plugins.hasPlugin('jacoco')
        assert jacoco.toolVersion == '0.8.14'

        def aggregateReport = tasks.named('jacocoRootAllReport').get()
        assert aggregateReport.reports.xml.required.get()
        assert aggregateReport.reports.xml.outputLocation.get().asFile ==
            layout.buildDirectory.file('reports/jacoco/report.xml').get().asFile
        assert aggregateReport.reports.html.required.get()
        assert !aggregateReport.reports.csv.required.get()

        assert tasks.findByName('jacocoCriticalCoverageVerification') != null
        assert tasks.findByName('jacocoModuleReportGear4jtestCore') != null
        assert tasks.findByName('jacocoModuleCoverageGear4jtestCore') != null
        assert tasks.findByName('coverageCalibrationReport') != null
        assert tasks.findByName('coverageReport') != null
        assert tasks.findByName('coverageVerification') != null
        assert tasks.findByName('verifyCoveragePolicy') != null
        assert tasks.findByName('integrationCheck') != null
        assert tasks.findByName('verifyPerformanceBudgets') != null

        def checkTask = tasks.named('check').get()
        def checkDependencies = checkTask.taskDependencies.getDependencies(checkTask)
        assert checkDependencies.contains(tasks.named('integrationCheck').get())
        assert checkDependencies.contains(tasks.named('coverageVerification').get())

        def core = project(':gear4jtest-core')
        assert core.plugins.hasPlugin('jacoco')
        assert core.plugins.hasPlugin('me.champeau.jmh')
        assert core.jacoco.toolVersion == '0.8.14'
        assert core.tasks.named('jacocoTestReport').get().reports.xml.required.get()
        assert core.jmh.jmhVersion.get() == '1.37'
        assert core.jmh.resultsFile.get().asFile ==
            core.layout.buildDirectory.file('reports/jmh/results.json').get().asFile
        assert core.jmh.humanOutputFile.get().asFile ==
            core.layout.buildDirectory.file('reports/jmh/human.txt').get().asFile
    }
}
'''.stripIndent())
        write('gear4jtest-core/build.gradle', '''
plugins {
    id 'gear4j.java-library'
    id 'gear4j.test-suite'
    id 'gear4j.benchmark'
}

ext.moduleName = 'io.github.gear4jtest.fixture.core'
'''.stripIndent())
        write('gear4jtest-core/src/main/java/fixture/CriticalPath.java', '''
package fixture;

public final class CriticalPath {
    private CriticalPath() {
    }
}
'''.stripIndent())

        BuildResult result = GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withArguments('verifyRootConventionModel', '--stacktrace', '--warning-mode=all')
            .withPluginClasspath()
            .build()

        assertThat(result.task(':verifyRootConventionModel').outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    void publishingAndReleaseConventionsPreserveStagingMetadataAndPublicTasks() {
        write('settings.gradle', '''
rootProject.name = 'release-convention-fixture'
includeBuild 'release-tools'
include 'sample-library'
'''.stripIndent())
        write('LICENSE', '''
Apache License
Version 2.0
'''.stripIndent())
        write('NOTICE', '''
Gear4J
Copyright Gear4J contributors
'''.stripIndent())
        write('jreleaser.yml', 'project: fixture\n')
        write('gradlew', '#!/bin/sh\n')
        write('config/consumer-smoke/build.gradle', '\n')
        write('release-tools/settings.gradle', "rootProject.name = 'release-tools'\n")
        write('release-tools/build.gradle', '''
tasks.register('jreleaserConfig')
tasks.register('jreleaserDeploy')
'''.stripIndent())
        write('sample-library/build.gradle', '\n')
        write('sample-library/src/main/java/fixture/PublishedType.java', '''
package fixture;

public final class PublishedType {
    private PublishedType() {
    }
}
'''.stripIndent())
        write('build.gradle', '''
plugins {
    id 'base'
    id 'gear4j.publishing' apply false
    id 'gear4j.root-release' apply false
}

group = 'io.github.gear4jtest'
version = findProperty('projectVersion') ?: '1.0.0'

['verifyDocumentationLinks', 'verifyDecisionIdentifiers', 'verifyLivingDocumentationMetadata',
 'dependencyCheckAggregate',
 'verifyDependencyCheckSuppressions', 'verifyPerformanceBudgets'].each { taskName ->
    tasks.register(taskName)
}

project(':sample-library') {
    group = rootProject.group
    version = rootProject.version
    description = 'Published fixture library.'
    ext.moduleName = 'io.github.gear4jtest.fixture.published'

    apply plugin: 'gear4j.publishing'
}

apply plugin: 'gear4j.root-release'

def publishedProject = project(':sample-library')
def rootTasks = tasks
def stagingDirectory = layout.buildDirectory.dir('staging-deploy')

tasks.register('verifyReleaseConventionModel') {
    doLast {
        assert publishedProject.plugins.hasPlugin('java-library')
        assert publishedProject.plugins.hasPlugin('maven-publish')

        assert publishedProject.tasks.findByName('sourcesJar') != null
        assert publishedProject.tasks.findByName('javadocJar') != null

        def publishing = publishedProject.extensions
            .getByType(org.gradle.api.publish.PublishingExtension)
        def repository = publishing.repositories.getByName('mavenCentralStaging')
        assert new File(repository.url).canonicalFile ==
            stagingDirectory.get().asFile.canonicalFile
        def publication = publishing.publications.getByName('mavenJava')
        assert publication.artifactId == 'sample-library'
        assert publication.pom.name.get() == 'sample library'
        assert publication.pom.description.get() == 'Published fixture library.'
        assert publication.pom.url.get() == 'https://github.com/gear4jtest/gear4jtest'

        def jarTask = publishedProject.tasks.named('jar', Jar).get()
        assert jarTask.manifest.attributes['Specification-Title'] == 'sample-library'
        assert jarTask.manifest.attributes['Specification-Version'] == '1.0.0'
        assert jarTask.manifest.attributes['Implementation-Title'] == 'sample-library'
        assert jarTask.manifest.attributes['Implementation-Version'] == '1.0.0'
        assert jarTask.manifest.attributes['Automatic-Module-Name'] ==
            'io.github.gear4jtest.fixture.published'

        ['verifyReleaseAssets', 'verifyReleaseVersion', 'jreleaserConfig', 'jreleaserDeploy', 'releaseMetadataCheck',
         'stageMavenCentral', 'verifyStagedReleaseArtifacts', 'consumerSmokeTest',
         'verifyReleaseDatabaseMatrixSelection', 'verifyJava17AndArchiveConfiguration',
         'verifyApiCompatibilityConfiguration', 'apiCompatibilityCheck', 'releaseCheck'].each {
            assert rootTasks.findByName(it) != null
        }

        def checkTask = rootTasks.named('check').get()
        assert checkTask.taskDependencies.getDependencies(checkTask)
            .contains(rootTasks.named('verifyJava17AndArchiveConfiguration').get())

        def releaseMetadataCheck = rootTasks.named('releaseMetadataCheck').get()
        assert releaseMetadataCheck.taskDependencies.getDependencies(releaseMetadataCheck)
            .contains(rootTasks.named('verifyLivingDocumentationMetadata').get())

        def releaseCheck = rootTasks.named('releaseCheck').get()
        def releaseDependencies = releaseCheck.taskDependencies.getDependencies(releaseCheck)
        assert releaseDependencies.contains(rootTasks.named('check').get())
        assert releaseDependencies.contains(rootTasks.named('consumerSmokeTest').get())
        assert releaseDependencies.contains(rootTasks.named('verifyStagedReleaseArtifacts').get())
        assert releaseDependencies.contains(rootTasks.named('apiCompatibilityCheck').get())
        assert releaseDependencies.contains(rootTasks.named('releaseMetadataCheck').get())
        assert releaseDependencies.contains(rootTasks.named('verifyReleaseVersion').get())
    }
}
'''.stripIndent())

        BuildResult result = GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withArguments('verifyReleaseConventionModel', 'verifyStagedReleaseArtifacts',
                'verifyJava17AndArchiveConfiguration', 'verifyReleaseVersion',
                '--stacktrace', '--warning-mode=all')
            .withPluginClasspath()
            .build()

        assertThat(result.task(':verifyReleaseConventionModel').outcome)
            .isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.task(':verifyStagedReleaseArtifacts').outcome)
            .isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.task(':verifyJava17AndArchiveConfiguration').outcome)
            .isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.task(':verifyReleaseVersion').outcome)
            .isEqualTo(TaskOutcome.SUCCESS)
        assertThat(Files.readString(projectDirectory.resolve(
            'build/reports/release/staged-artifacts.txt')))
            .isEqualTo('Verified 3 JARs and 1 POMs.\n')

        def stagedPom = new groovy.xml.XmlSlurper(false, false).parse(projectDirectory.resolve(
            'build/staging-deploy/io/github/gear4jtest/sample-library/1.0.0/'
                + 'sample-library-1.0.0.pom').toFile())
        assertThat(stagedPom.licenses.license.name.text())
            .isEqualTo('Apache License, Version 2.0')
        assertThat(stagedPom.licenses.license.url.text())
            .isEqualTo('https://www.apache.org/licenses/LICENSE-2.0.txt')
        assertThat(stagedPom.licenses.license.distribution.text()).isEqualTo('repo')
        assertThat(stagedPom.developers.developer.id.text()).isEqualTo('gear4jtest')
        assertThat(stagedPom.developers.developer.name.text())
            .isEqualTo('Gear4J contributors')
        assertThat(stagedPom.scm.connection.text())
            .isEqualTo('scm:git:https://github.com/gear4jtest/gear4jtest.git')
        assertThat(stagedPom.scm.developerConnection.text())
            .isEqualTo('scm:git:ssh://git@github.com/gear4jtest/gear4jtest.git')
        assertThat(stagedPom.scm.url.text())
            .isEqualTo('https://github.com/gear4jtest/gear4jtest')
        assertThat(stagedPom.scm.tag.text()).isEqualTo('v1.0.0')

        BuildResult rejectedSnapshot = GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withArguments('verifyReleaseVersion', '-PprojectVersion=1.0.0-SNAPSHOT',
                '--stacktrace', '--warning-mode=all')
            .withPluginClasspath()
            .buildAndFail()
        assertThat(rejectedSnapshot.task(':verifyReleaseVersion').outcome)
            .isEqualTo(TaskOutcome.FAILED)
        assertThat(rejectedSnapshot.output)
            .contains('releaseCheck requires a SemVer-like non-snapshot project version')
    }

    private void write(String relativePath, String content) {
        Path destination = projectDirectory.resolve(relativePath)
        Files.createDirectories(destination.parent)
        Files.writeString(destination, content)
    }
}
