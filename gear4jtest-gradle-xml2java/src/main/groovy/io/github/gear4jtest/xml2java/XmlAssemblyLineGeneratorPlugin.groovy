package io.github.gear4jtest.xml2java

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet

/**
 * Gradle plugin that generates Java Gear4J assembly line classes from XML files before Java compilation.
 */
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
            task.outputDir.set(extension.outputDir)
            task.mediaType.set(extension.mediaType)
            task.trustedXml.set(extension.trustedXml)
            task.operatorCapabilities.set(extension.operatorCapabilities)
            task.maxOperations.set(extension.maxOperations)
            task.maxDependencies.set(extension.maxDependencies)
            task.maxNestingDepth.set(extension.maxNestingDepth)
            task.maxGeneratedSourceBytes.set(extension.maxGeneratedSourceBytes)
        }

        project.plugins.withType(JavaPlugin) {
            project.extensions.configure(JavaPluginExtension) { JavaPluginExtension java ->
                java.sourceSets.named(SourceSet.MAIN_SOURCE_SET_NAME) { SourceSet sourceSet ->
                    sourceSet.java.srcDir(extension.outputDir)
                }
            }
            project.tasks.named(JavaPlugin.COMPILE_JAVA_TASK_NAME).configure { task ->
                task.dependsOn(generateTask)
            }
        }
    }
}
