package io.github.gear4jtest.xml2java

import org.gradle.api.GradleException
import org.gradle.api.plugins.JavaPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import io.github.gear4jtest.xml.validator.AssemblyLineValidator

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatThrownBy

class XmlAssemblyLineGeneratorPluginTest {

    @Test
    void should_register_generation_task_and_extension() {
        // Given
        def project = ProjectBuilder.builder()
            .withName('my-library')
            .build()

        // When
        project.plugins.apply(XmlAssemblyLineGeneratorPlugin)

        // Then
        assertThat(project.extensions.findByName(XmlAssemblyLineGeneratorPlugin.EXTENSION_NAME))
            .isInstanceOf(XmlAssemblyLineGeneratorExtension)
        assertThat(project.tasks.findByName(XmlAssemblyLineGeneratorPlugin.TASK_NAME))
            .isInstanceOf(XmlAssemblyLineGenerateTask)
    }

    @Test
    void should_wire_generation_task_before_java_compilation() {
        // Given
        def project = ProjectBuilder.builder()
            .withName('my-library')
            .build()

        // When
        project.plugins.apply(JavaPlugin)
        project.plugins.apply(XmlAssemblyLineGeneratorPlugin)

        // Then
        def compileJava = project.tasks.getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME)
        assertThat(compileJava.taskDependencies.getDependencies(compileJava))
            .extracting('name')
            .contains(XmlAssemblyLineGeneratorPlugin.TASK_NAME)
    }

    @Test
    void should_share_xml_input_limit_between_extension_and_task() {
        // Given
        def project = ProjectBuilder.builder()
            .withName('my-library')
            .build()

        // When
        project.plugins.apply(XmlAssemblyLineGeneratorPlugin)
        def extension = project.extensions.getByType(XmlAssemblyLineGeneratorExtension)
        def task = project.tasks.getByName(XmlAssemblyLineGeneratorPlugin.TASK_NAME)

        // Then
        assertThat(extension.maxXmlBytes.get()).isEqualTo(AssemblyLineValidator.DEFAULT_MAX_XML_BYTES)
        assertThat(task.maxXmlBytes.get()).isEqualTo(AssemblyLineValidator.DEFAULT_MAX_XML_BYTES)

        // When
        extension.maxXmlBytes.set(512L)

        // Then
        assertThat(task.maxXmlBytes.get()).isEqualTo(512L)
    }

    @Test
    void should_read_only_one_byte_beyond_xml_input_limit() {
        // Given
        def input = new CountingInputStream(new ByteArrayInputStream(new byte[1_024]))

        // When / Then
        assertThatThrownBy {
            BoundedXmlInput.read(input, 'oversized.xml', 32L)
        }
            .isInstanceOf(GradleException)
            .hasMessage('Gear4J XML definition exceeds maxXmlBytes=32: oversized.xml')
        assertThat(input.bytesRead()).isEqualTo(33)
    }

    @Test
    void should_generate_java_sources_from_configured_xml_files() {
        // Given
        def project = ProjectBuilder.builder()
            .withName('my-library')
            .build()
        project.plugins.apply(XmlAssemblyLineGeneratorPlugin)

        def xmlDir = new File(project.projectDir, 'src/main/gear4j')
        assertThat(xmlDir.mkdirs()).isTrue()
        new File(xmlDir, 'simple-line.xml').text = '''<?xml version="1.0" encoding="UTF-8"?>
<assemblyLine xmlns="http://github.com/gear4jtest/core/model"
              id="simple_line"
              inputType="java.lang.String"
              outputType="java.lang.String">
  <operations>
    <processingOperation id="append_a" type="com.myorg.operation.Step11">
      <parameters>
        <valueParameter retriever="com.myorg.operation.Step11::getParam" value="a"/>
      </parameters>
    </processingOperation>
  </operations>
</assemblyLine>
'''
        def outputDir = new File(project.buildDir, 'generated-test')
        def extension = project.extensions.getByType(XmlAssemblyLineGeneratorExtension)
        extension.outputDir.fileValue(outputDir)
        extension.trustedXml()

        // When
        project.tasks.getByName(XmlAssemblyLineGeneratorPlugin.TASK_NAME).generate()

        // Then
        def generated = new File(outputDir, 'io/github/gear4jtest/xml/generated/Simple_lineLine.java')
        assertThat(generated)
            .as('generated Java source must be written under the package path')
            .exists()
        assertThat(generated.text)
            .contains('public final class Simple_lineLine')
            .contains('implements GeneratedAssemblyLine')
    }

    @Test
    void should_reject_duplicate_generated_classes_before_replacing_outputs() {
        // Given
        def project = ProjectBuilder.builder()
            .withName('my-library')
            .build()
        project.plugins.apply(XmlAssemblyLineGeneratorPlugin)

        def xmlDir = new File(project.projectDir, 'src/main/gear4j')
        def nestedXmlDir = new File(xmlDir, 'nested')
        assertThat(nestedXmlDir.mkdirs()).isTrue()
        def duplicatePipeline = '''<?xml version="1.0" encoding="UTF-8"?>
<assemblyLine xmlns="http://github.com/gear4jtest/core/model"
              id="@ID@"
              inputType="java.lang.String"
              outputType="java.lang.String">
  <operations>
    <signal id="stop" type="STOP" inputType="java.lang.String"/>
  </operations>
</assemblyLine>
'''
        new File(xmlDir, 'first-a.xml').text = duplicatePipeline.replace('@ID@', 'duplicate_a')
        new File(nestedXmlDir, 'second-a.xml').text = duplicatePipeline.replace('@ID@', 'duplicate_a')
        new File(xmlDir, 'first-b.xml').text = duplicatePipeline.replace('@ID@', 'duplicate_b')
        new File(nestedXmlDir, 'second-b.xml').text = duplicatePipeline.replace('@ID@', 'duplicate_b')

        def outputDir = new File(project.buildDir, 'generated-test')
        assertThat(outputDir.mkdirs()).isTrue()
        def previous = new File(outputDir, 'previous.java')
        previous.text = 'keep me'
        def extension = project.extensions.getByType(XmlAssemblyLineGeneratorExtension)
        extension.outputDir.fileValue(outputDir)

        // When / Then
        assertThatThrownBy { project.tasks.getByName(XmlAssemblyLineGeneratorPlugin.TASK_NAME).generate() }
            .isInstanceOf(GradleException)
            .hasMessageContaining('Duplicate generated Java classes detected:')
            .hasMessageContaining("Generated Java class 'io.github.gear4jtest.xml.generated.Duplicate_aLine'")
            .hasMessageContaining("Generated Java class 'io.github.gear4jtest.xml.generated.Duplicate_bLine'")
            .hasMessageContaining('first-a.xml')
            .hasMessageContaining('nested' + File.separator + 'second-b.xml')
        assertThat(previous).exists().hasContent('keep me')
    }

    @Test
    void should_reject_inline_java_by_default_until_xml_is_explicitly_trusted() {
        // Given
        def project = ProjectBuilder.builder()
            .withName('my-library')
            .build()
        project.plugins.apply(XmlAssemblyLineGeneratorPlugin)
        def extension = project.extensions.getByType(XmlAssemblyLineGeneratorExtension)
        extension.operatorCapability('com.myorg.operation.Step11', 'com.myorg.operation.Step11')

        def xmlDir = new File(project.projectDir, 'src/main/gear4j')
        assertThat(xmlDir.mkdirs()).isTrue()
        new File(xmlDir, 'untrusted-line.xml').text = '''<?xml version="1.0" encoding="UTF-8"?>
<assemblyLine xmlns="http://github.com/gear4jtest/core/model"
              id="untrusted_line"
              inputType="java.lang.String"
              outputType="java.lang.String">
  <operations>
    <processingOperation id="append_a" type="com.myorg.operation.Step11">
      <parameters>
        <valueParameter retriever="com.myorg.operation.Step11::getParam" value="a"/>
      </parameters>
    </processingOperation>
  </operations>
</assemblyLine>
'''

        // When / Then
        assertThatThrownBy { project.tasks.getByName(XmlAssemblyLineGeneratorPlugin.TASK_NAME).generate() }
            .isInstanceOf(SecurityException)
            .hasMessageContaining('Inline Java expressions are not allowed')
    }

    @Test
    void should_generate_gel_only_xml_with_registered_operator_capability() {
        // Given
        def project = ProjectBuilder.builder()
            .withName('my-library')
            .build()
        project.plugins.apply(XmlAssemblyLineGeneratorPlugin)

        def xmlDir = new File(project.projectDir, 'src/main/gear4j')
        assertThat(xmlDir.mkdirs()).isTrue()
        new File(xmlDir, 'restricted-line.xml').text = '''<?xml version="1.0" encoding="UTF-8"?>
<assemblyLine xmlns="http://github.com/gear4jtest/core/model"
              id="restricted_line"
              inputType="java.lang.String"
              outputType="java.lang.String">
  <operations>
    <processingOperation id="append" type="text.append"/>
  </operations>
</assemblyLine>
'''
        def outputDir = new File(project.buildDir, 'generated-test')
        def extension = project.extensions.getByType(XmlAssemblyLineGeneratorExtension)
        extension.outputDir.fileValue(outputDir)
        extension.operatorCapability('text.append', 'com.myorg.operation.Step11')

        // When
        project.tasks.getByName(XmlAssemblyLineGeneratorPlugin.TASK_NAME).generate()

        // Then
        def generated = new File(outputDir, 'io/github/gear4jtest/xml/generated/Restricted_lineLine.java')
        assertThat(generated).exists()
        assertThat(generated.text)
            .contains('Step11.class')
            .doesNotContain('text.append')
    }

    @Test
    void should_keep_previous_generated_sources_when_translation_fails() {
        // Given
        def project = ProjectBuilder.builder()
            .withName('my-library')
            .build()
        project.plugins.apply(XmlAssemblyLineGeneratorPlugin)

        def xmlDir = new File(project.projectDir, 'src/main/gear4j')
        assertThat(xmlDir.mkdirs()).isTrue()
        new File(xmlDir, 'untrusted-line.xml').text = '''<?xml version="1.0" encoding="UTF-8"?>
<assemblyLine xmlns="http://github.com/gear4jtest/core/model"
              id="untrusted_line"
              inputType="java.lang.String"
              outputType="java.lang.String">
  <operations>
    <processingOperation id="append_a" type="com.myorg.operation.Step11">
      <parameters>
        <valueParameter retriever="com.myorg.operation.Step11::getParam" value="a"/>
      </parameters>
    </processingOperation>
  </operations>
</assemblyLine>
'''
        def outputDir = new File(project.buildDir, 'generated-test')
        assertThat(outputDir.mkdirs()).isTrue()
        def previous = new File(outputDir, 'previous.java')
        previous.text = 'keep me'
        def extension = project.extensions.getByType(XmlAssemblyLineGeneratorExtension)
        extension.outputDir.fileValue(outputDir)
        extension.operatorCapability('com.myorg.operation.Step11', 'com.myorg.operation.Step11')

        // When / Then
        assertThatThrownBy { project.tasks.getByName(XmlAssemblyLineGeneratorPlugin.TASK_NAME).generate() }
            .isInstanceOf(SecurityException)
            .hasMessageContaining('Inline Java expressions are not allowed')
        assertThat(previous).exists().hasContent('keep me')
    }

    @Test
    void should_enforce_configured_generation_limits_before_replacing_outputs() {
        // Given
        def project = ProjectBuilder.builder()
            .withName('my-library')
            .build()
        project.plugins.apply(XmlAssemblyLineGeneratorPlugin)
        def xmlDir = new File(project.projectDir, 'src/main/gear4j')
        assertThat(xmlDir.mkdirs()).isTrue()
        new File(xmlDir, 'bounded.xml').text = '''<?xml version="1.0" encoding="UTF-8"?>
<assemblyLine xmlns="http://github.com/gear4jtest/core/model"
              id="bounded"
              inputType="java.lang.String"
              outputType="java.lang.String">
  <operations>
    <signal id="first" type="STOP" inputType="java.lang.String"/>
    <signal id="second" type="STOP" inputType="java.lang.String"/>
  </operations>
</assemblyLine>
'''
        def outputDir = new File(project.buildDir, 'generated-test')
        assertThat(outputDir.mkdirs()).isTrue()
        def previous = new File(outputDir, 'previous.java')
        previous.text = 'keep me'
        def extension = project.extensions.getByType(XmlAssemblyLineGeneratorExtension)
        extension.outputDir.fileValue(outputDir)
        extension.trustedXml()
        extension.maxOperations.set(1)

        // When / Then
        assertThatThrownBy { project.tasks.getByName(XmlAssemblyLineGeneratorPlugin.TASK_NAME).generate() }
            .isInstanceOf(IllegalArgumentException)
            .hasMessageContaining('maxOperations=1')
        assertThat(previous).exists().hasContent('keep me')
    }

    private static final class CountingInputStream extends InputStream {
        private final InputStream delegate
        private int bytesRead

        private CountingInputStream(InputStream delegate) {
            this.delegate = delegate
        }

        @Override
        int read() throws IOException {
            int value = delegate.read()
            if (value != -1) {
                bytesRead++
            }
            return value
        }

        @Override
        int read(byte[] buffer, int offset, int length) throws IOException {
            int read = delegate.read(buffer, offset, length)
            if (read > 0) {
                bytesRead += read
            }
            return read
        }

        private int bytesRead() {
            return bytesRead
        }
    }

}
