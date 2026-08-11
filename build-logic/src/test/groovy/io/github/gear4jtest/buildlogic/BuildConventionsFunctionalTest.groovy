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

    private void write(String relativePath, String content) {
        Path destination = projectDirectory.resolve(relativePath)
        Files.createDirectories(destination.parent)
        Files.writeString(destination, content)
    }
}
