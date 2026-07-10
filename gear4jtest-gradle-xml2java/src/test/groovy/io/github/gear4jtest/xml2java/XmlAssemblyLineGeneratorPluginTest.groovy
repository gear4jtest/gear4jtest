package io.github.gear4jtest.xml2java

import org.gradle.api.plugins.JavaPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

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
    void should_reject_inline_java_by_default_until_xml_is_explicitly_trusted() {
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

        // When / Then
        assertThatThrownBy { project.tasks.getByName(XmlAssemblyLineGeneratorPlugin.TASK_NAME).generate() }
            .isInstanceOf(SecurityException)
            .hasMessageContaining('Inline Java expressions are not allowed')
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

        // When / Then
        assertThatThrownBy { project.tasks.getByName(XmlAssemblyLineGeneratorPlugin.TASK_NAME).generate() }
            .isInstanceOf(SecurityException)
            .hasMessageContaining('Inline Java expressions are not allowed')
        assertThat(previous).exists().hasContent('keep me')
    }

}
