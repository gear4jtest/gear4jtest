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
        project.delete(destination)
        destination.mkdirs()

        XmlOperationChainTranslator translator = trustedXml.get()
            ? XmlOperationChainTranslator.trusted()
            : XmlOperationChainTranslator.gelOnly()
        xmlFiles.files
            .findAll { File file -> file.isFile() && file.name.endsWith('.xml') }
            .sort { File file -> file.path }
            .each { File file ->
                def result = translator.translate(file.bytes, mediaType.get())
                XmlAssemblyLineGenerateTask.writeJavaSource(destination, result.className(), result.formattedSource())
                logger.info('Generated Gear4J Java source {} from XML {}', result.className(), file)
            }
    }

    static void writeJavaSource(File outputRoot, String className, String formattedSource) {
        File target = new File(outputRoot, className.replace('.', File.separator) + '.java')
        target.parentFile.mkdirs()
        target.setText(formattedSource, 'UTF-8')
    }
}
