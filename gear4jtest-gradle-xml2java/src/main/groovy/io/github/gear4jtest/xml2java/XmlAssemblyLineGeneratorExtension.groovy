package io.github.gear4jtest.xml2java

import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import io.github.gear4jtest.xml.translator.XmlTranslationLimits
import io.github.gear4jtest.xml.validator.AssemblyLineValidator

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

    /**
     * Stable operator capability id to Java class name mappings used by restricted XML.
     */
    final MapProperty<String, String> operatorCapabilities
    final Property<Integer> maxOperations
    final Property<Integer> maxDependencies
    final Property<Integer> maxNestingDepth
    final Property<Long> maxXmlBytes
    final Property<Long> maxGeneratedSourceBytes

    XmlAssemblyLineGeneratorExtension(Project project) {
        this.project = project
        this.xmlFiles = project.objects.fileCollection()
        this.outputDir = project.objects.directoryProperty()
        this.mediaType = project.objects.property(String)
        this.trustedXml = project.objects.property(Boolean)
        this.operatorCapabilities = project.objects.mapProperty(String, String)
        this.maxOperations = project.objects.property(Integer)
        this.maxDependencies = project.objects.property(Integer)
        this.maxNestingDepth = project.objects.property(Integer)
        this.maxXmlBytes = project.objects.property(Long)
        this.maxGeneratedSourceBytes = project.objects.property(Long)

        this.xmlFiles.from(project.fileTree('src/main/gear4j') { include '**/*.xml' })
        this.outputDir.convention(project.layout.buildDirectory.dir('generated/sources/gear4j/xml2java/main'))
        this.mediaType.convention('application/xml')
        this.trustedXml.convention(false)
        this.operatorCapabilities.convention([:])
        this.maxOperations.convention(XmlTranslationLimits.DEFAULT_MAX_OPERATIONS)
        this.maxDependencies.convention(XmlTranslationLimits.DEFAULT_MAX_DEPENDENCIES)
        this.maxNestingDepth.convention(XmlTranslationLimits.DEFAULT_MAX_NESTING_DEPTH)
        this.maxXmlBytes.convention(AssemblyLineValidator.DEFAULT_MAX_XML_BYTES)
        this.maxGeneratedSourceBytes.convention(XmlTranslationLimits.DEFAULT_MAX_GENERATED_SOURCE_BYTES)
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

    /** Configures the generated-source directory from a path accepted by {@link Project#file(Object)}. */
    void outputDir(Object path) {
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

    /** Allows one stable operator capability in restricted build-time XML. */
    void operatorCapability(String capabilityId, String operatorClassName) {
        operatorCapabilities.put(capabilityId, operatorClassName)
    }
}
