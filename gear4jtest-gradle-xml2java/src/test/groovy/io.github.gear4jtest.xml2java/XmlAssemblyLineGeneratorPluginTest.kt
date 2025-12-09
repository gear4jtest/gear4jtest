package io.github.gear4jtest.xml2java

import static org.assertj.core.api.Assertions.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class XmlAssemblyLineGeneratorPluginTest {

	@Test
	void should_contain_default_plugins() {
		// Given
		def project = ProjectBuilder.builder()
				.withName("my-library")
				.build()

		// When
		project.plugins.apply("io.github.gear4jtest.xml2java")

		// Then
		assertThat(project.tasks)
				.extracting("name")
				.containsExactlyInAnyOrder("xmlGenerateAssemblyLine")
	}

}
