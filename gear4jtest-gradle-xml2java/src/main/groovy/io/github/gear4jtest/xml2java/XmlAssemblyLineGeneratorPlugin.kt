package io.github.gear4jtest.xml2java

import com.squareup.javapoet.JavaFile
import io.github.gear4jtest.core.model.ElementModelBuilders
import io.test.gear4test.xml.generator.AssemblyLineGenerator
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.util.stream.Stream

class XmlAssemblyLineGeneratorPlugin implements Plugin<Project> {

	void apply(Project project) {
		def extension = project.extensions.create(
			"xmlAssemblyLineGenerator",
			XmlAssemblyLineGeneratorExtension.class
		)

		project.task("xmlGenerateAssemblyLine") {
			doLast {
				println("${extension.filePaths.get()} from ${extension.outputDir.get()}")
				Stream.of(new File(extension.filePaths).listFiles({ file -> file.extension == "xml" }))
					.forEach ((File file) -> {
						def typeSpec = AssemblyLineGenerator.generateFlatAssemblyLines(new FileInputStream(file));
						def javaFile = JavaFile.builder("whatever", typeSpec)
								.indent("    ")
								.skipJavaLangImports(true)
								.addStaticImport(ElementModelBuilders::class, "*")
								.build()
						javaFile.writeTo(new File(extension.outputDir))
					})
			}
		}
	}

}
