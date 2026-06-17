package io.github.gear4jtest.xml2java

import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property

/**
 * Configures build-time Java source generation from Gear4J XML pipeline definitions.
 */
abstract class XmlAssemblyLineGeneratorExtension {
    private final Project project

    /** XML files to translate. Defaults to every XML file under src/main/gear4j. */
    final ConfigurableFileCollection xmlFiles

    /** Directory where generated Java sources are written. */
    final DirectoryProperty outputDir

    /** Media type passed to the XML translator. */
    final Property<String> mediaType

    /**
     * Whether XML definitions are trusted Java source inputs.
     * Defaults to false, so inline Java expressions are rejected unless the build opts in explicitly.
     */
    final Property<Boolean> trustedXml

    XmlAssemblyLineGeneratorExtension(Project project) {
        this.project = project
        this.xmlFiles = project.objects.fileCollection()
        this.outputDir = project.objects.directoryProperty()
        this.mediaType = project.objects.property(String)
        this.trustedXml = project.objects.property(Boolean)

        this.xmlFiles.from(project.fileTree('src/main/gear4j') { include '**/*.xml' })
        this.outputDir.convention(project.layout.buildDirectory.dir('generated/sources/gear4j/xml2java/main'))
        this.mediaType.convention('application/xml')
        this.trustedXml.convention(false)
    }

    /** Adds XML files or file collections to translate. */
    void xmlFiles(Object... paths) {
        xmlFiles.from(paths)
    }

    /** Adds every XML file below the supplied directory. */
    void inputDir(Object path) {
        xmlFiles.from(project.fileTree(path) { include '**/*.xml' })
    }

    /** Backward-compatible alias for older builds that configured a single XML directory through filePaths. */
    void setFilePaths(Object path) {
        inputDir(path)
    }

    /** Backward-compatible readable property for older build scripts. */
    ConfigurableFileCollection getFilePaths() {
        return xmlFiles
    }

    /** Allows Groovy DSL assignment such as outputDir = layout.buildDirectory.dir(...). */
    void setOutputDir(Object path) {
        outputDir.fileValue(project.file(path))
    }

    /** Allows Groovy DSL assignment such as trustedXml = true. */
    void setTrustedXml(boolean trusted) {
        trustedXml.set(trusted)
    }

    /** Convenience DSL method for trusted, reviewed XML definitions. */
    void trustedXml() {
        trustedXml.set(true)
    }
}
