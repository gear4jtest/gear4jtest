package io.github.gear4jtest.xml2java

import io.github.gear4jtest.external.api.ExecutionMode
import io.github.gear4jtest.xml.capability.XmlOperatorCapabilityPolicy
import io.github.gear4jtest.xml.translator.XmlOperationChainTranslator
import io.github.gear4jtest.xml.translator.XmlTranslationLimits
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import javax.inject.Inject

/**
 * Translates XML pipeline definitions into generated Java source files.
 */
@CacheableTask
abstract class XmlAssemblyLineGenerateTask extends DefaultTask {
    private final ConfigurableFileCollection xmlFiles
    private final DirectoryProperty outputDir
    private final Property<String> mediaType
    private final Property<Boolean> trustedXml
    private final MapProperty<String, String> operatorCapabilities
    private final Property<Integer> maxOperations
    private final Property<Integer> maxDependencies
    private final Property<Integer> maxNestingDepth
    private final Property<Long> maxGeneratedSourceBytes

    @Inject
    XmlAssemblyLineGenerateTask(ObjectFactory objects) {
        this.xmlFiles = objects.fileCollection()
        this.outputDir = objects.directoryProperty()
        this.mediaType = objects.property(String)
        this.trustedXml = objects.property(Boolean).convention(false)
        this.operatorCapabilities = objects.mapProperty(String, String).convention([:])
        this.maxOperations = objects.property(Integer).convention(XmlTranslationLimits.DEFAULT_MAX_OPERATIONS)
        this.maxDependencies = objects.property(Integer).convention(XmlTranslationLimits.DEFAULT_MAX_DEPENDENCIES)
        this.maxNestingDepth = objects.property(Integer).convention(XmlTranslationLimits.DEFAULT_MAX_NESTING_DEPTH)
        this.maxGeneratedSourceBytes = objects.property(Long)
            .convention(XmlTranslationLimits.DEFAULT_MAX_GENERATED_SOURCE_BYTES)
    }

    @Inject
    abstract FileSystemOperations getFileSystemOperations()

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    ConfigurableFileCollection getXmlFiles() {
        return xmlFiles
    }

    @OutputDirectory
    DirectoryProperty getOutputDir() {
        return outputDir
    }

    @Input
    Property<String> getMediaType() {
        return mediaType
    }

    @Input
    Property<Boolean> getTrustedXml() {
        return trustedXml
    }

    @Input
    MapProperty<String, String> getOperatorCapabilities() {
        return operatorCapabilities
    }

    @Input
    Property<Integer> getMaxOperations() {
        return maxOperations
    }

    @Input
    Property<Integer> getMaxDependencies() {
        return maxDependencies
    }

    @Input
    Property<Integer> getMaxNestingDepth() {
        return maxNestingDepth
    }

    @Input
    Property<Long> getMaxGeneratedSourceBytes() {
        return maxGeneratedSourceBytes
    }

    @TaskAction
    void generate() {
        File destination = outputDir.get().asFile
        XmlTranslationLimits limits = new XmlTranslationLimits(
            maxOperations.get(),
            maxDependencies.get(),
            maxNestingDepth.get(),
            maxGeneratedSourceBytes.get()
        )
        XmlOperationChainTranslator translator = trustedXml.get()
            ? XmlOperationChainTranslator.trusted(limits)
            : XmlOperationChainTranslator.gelOnly(restrictedCapabilities(), limits)

        def generatedSources = xmlFiles.files
            .findAll { File file -> file.isFile() && file.name.endsWith('.xml') }
            .sort { File file -> file.path }
            .collect { File file ->
                def result = translator.translate(file.bytes, mediaType.get(), ExecutionMode.RUN)
                new GeneratedSource(file, result.className(), result.formattedSource())
            }

        getFileSystemOperations().delete { spec -> spec.delete(destination) }
        destination.mkdirs()
        generatedSources.each { GeneratedSource generated ->
            XmlAssemblyLineGenerateTask.writeJavaSource(destination, generated.className, generated.formattedSource)
            logger.info('Generated Gear4J Java source {} from XML {}', generated.className, generated.sourceFile)
        }
    }

    private XmlOperatorCapabilityPolicy restrictedCapabilities() {
        def builder = XmlOperatorCapabilityPolicy.builder()
        operatorCapabilities.get().each { String capabilityId, String operatorClassName ->
            builder.allowClassName(capabilityId, operatorClassName, ExecutionMode.RUN)
        }
        return builder.build()
    }

    private static final class GeneratedSource {
        final File sourceFile
        final String className
        final String formattedSource

        private GeneratedSource(File sourceFile, String className, String formattedSource) {
            this.sourceFile = sourceFile
            this.className = className
            this.formattedSource = formattedSource
        }
    }

    static void writeJavaSource(File outputRoot, String className, String formattedSource) {
        File target = new File(outputRoot, className.replace('.', File.separator) + '.java')
        target.parentFile.mkdirs()
        target.setText(formattedSource, 'UTF-8')
    }
}
