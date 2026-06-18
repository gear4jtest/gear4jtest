package io.github.gear4jtest.xml2java

import io.github.gear4jtest.xml.translator.XmlOperationChainTranslator
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Translates XML pipeline definitions into generated Java source files.
 */
abstract class XmlAssemblyLineGenerateTask extends DefaultTask {
    private final ConfigurableFileCollection xmlFiles = project.objects.fileCollection()
    private final DirectoryProperty outputDir = project.objects.directoryProperty()
    private final Property<String> mediaType = project.objects.property(String)
    private final Property<Boolean> trustedXml = project.objects.property(Boolean).convention(false)

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

    @TaskAction
    void generate() {
        File destination = outputDir.get().asFile
        XmlOperationChainTranslator translator = trustedXml.get()
            ? XmlOperationChainTranslator.trusted()
            : XmlOperationChainTranslator.gelOnly()

        def generatedSources = xmlFiles.files
            .findAll { File file -> file.isFile() && file.name.endsWith('.xml') }
            .sort { File file -> file.path }
            .collect { File file ->
                def result = translator.translate(file.bytes, mediaType.get())
                new GeneratedSource(file, result.className(), result.formattedSource())
            }

        project.delete(destination)
        destination.mkdirs()
        generatedSources.each { GeneratedSource generated ->
            XmlAssemblyLineGenerateTask.writeJavaSource(destination, generated.className, generated.formattedSource)
            logger.info('Generated Gear4J Java source {} from XML {}', generated.className, generated.sourceFile)
        }
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
