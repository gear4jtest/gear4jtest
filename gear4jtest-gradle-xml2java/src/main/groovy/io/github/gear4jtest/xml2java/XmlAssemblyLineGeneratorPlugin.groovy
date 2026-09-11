package io.github.gear4jtest.xml2java

import io.github.gear4jtest.core.api.annotation.Internal
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar

/**
 * Gradle plugin that generates Java Gear4J assembly line classes from XML files before Java compilation.
 */
@Internal
class XmlAssemblyLineGeneratorPlugin implements Plugin<Project> {
    static final String EXTENSION_NAME = 'xmlAssemblyLineGenerator'
    static final String TASK_NAME = 'xmlGenerateAssemblyLine'

    @Override
    void apply(Project project) {
        XmlAssemblyLineGeneratorExtension extension = project.extensions.create(
            EXTENSION_NAME,
            XmlAssemblyLineGeneratorExtension,
            project
        )

        def generateTask = project.tasks.register(TASK_NAME, XmlAssemblyLineGenerateTask) { task ->
            task.group = 'code generation'
            task.description = 'Generates Java Gear4J assembly line classes from XML pipeline definitions.'
            task.xmlFiles.from(extension.xmlFiles)
            task.operatorClasspath.from(extension.operatorClasspath)
            task.outputDir.set(extension.outputDir)
            task.mediaType.set(extension.mediaType)
            task.trustedXml.set(extension.trustedXml)
            task.operatorCapabilities.set(extension.operatorCapabilities)
            task.maxOperations.set(extension.maxOperations)
            task.maxDependencies.set(extension.maxDependencies)
            task.maxNestingDepth.set(extension.maxNestingDepth)
            task.maxXmlBytes.set(extension.maxXmlBytes)
            task.maxGeneratedSourceBytes.set(extension.maxGeneratedSourceBytes)
        }

        project.plugins.withType(JavaPlugin) {
            project.extensions.configure(JavaPluginExtension) { JavaPluginExtension java ->
                SourceSet operators = java.sourceSets.maybeCreate('gear4jOperators')
                project.configurations.named(operators.implementationConfigurationName) {
                    extendsFrom(project.configurations.getByName('implementation'))
                }
                project.configurations.named(operators.compileOnlyConfigurationName) {
                    extendsFrom(project.configurations.getByName('compileOnly'))
                }
                project.configurations.named(operators.runtimeOnlyConfigurationName) {
                    extendsFrom(project.configurations.getByName('runtimeOnly'))
                }
                java.sourceSets.named(SourceSet.MAIN_SOURCE_SET_NAME) { SourceSet sourceSet ->
                    sourceSet.compileClasspath += operators.output
                    sourceSet.runtimeClasspath += operators.output
                    sourceSet.java.srcDir(generateTask.flatMap { it.outputDir })
                    extension.operatorClasspath.from(sourceSet.compileClasspath, operators.runtimeClasspath)
                }
                java.sourceSets.named(SourceSet.TEST_SOURCE_SET_NAME) { SourceSet sourceSet ->
                    sourceSet.compileClasspath += operators.output
                    sourceSet.runtimeClasspath += operators.output
                }
                project.tasks.named(JavaPlugin.JAR_TASK_NAME, Jar) {
                    from(operators.output)
                }
                project.tasks.withType(Jar).matching { it.name == 'sourcesJar' }.configureEach {
                    from(operators.allSource)
                }
            }
            project.tasks.named(JavaPlugin.COMPILE_JAVA_TASK_NAME).configure { task ->
                task.dependsOn(generateTask)
            }
        }
    }
}
