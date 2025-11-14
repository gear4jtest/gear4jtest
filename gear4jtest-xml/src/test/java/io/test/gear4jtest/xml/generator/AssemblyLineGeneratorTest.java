package io.test.gear4jtest.xml.generator;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import io.test.gear4test.xml.generator.XmlToJavaGeneratorV4;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssemblyLineGeneratorTest {

    @Test
    void testXsdGenerationSuccessful() throws Exception {
        // Given
//        InputStream io = getClass().getResourceAsStream("/samples/sample-assembly-line.xml");

        // When
//        JavaFile javaFile = new XmlToJavaGenerator("com.myorg.assemblylines.generated", "Whatever")
//                .generateFromXml(new File("src/test/resources/samples/assembly-line-iterator.xml"));
        XmlToJavaGeneratorV4.GenerationResult javaFile = new XmlToJavaGeneratorV4("com.myorg.assemblylines.generated")
                .generateFromAssemblyLine(new File("src/test/resources/samples/assembly-line-iterator.xml"));

        // Then
        assertThat(javaFile.formattedSource())
                .as("Java file content should be equal")
                .isEqualTo(inputStreamToString(getClass().getResourceAsStream("/samples/AssertedAssemblyLine.java")));

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> ds = new DiagnosticCollector<>();
        try (StandardJavaFileManager mgr = compiler.getStandardFileManager(ds, null, null)) {
            File file = new File(getClass().getResource("/samples/MyInsaneAssemblyLine.java").toURI());
            Iterable<? extends JavaFileObject> sources = mgr.getJavaFileObjectsFromFiles(Arrays.asList(file));
            JavaCompiler.CompilationTask task = compiler.getTask(null, mgr, ds, null, null, sources);
            task.call();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        for (Diagnostic<? extends JavaFileObject> d : ds.getDiagnostics()) {
            System.err.format("Line: %d, %s in %s", d.getLineNumber(), d.getMessage(null), d.getSource().getName());
        }
        assertThat(ds.getDiagnostics().stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR))
                .as("There should not be any error")
                .isFalse();
    }

    private static String inputStreamToString(InputStream is) {
        StringBuilder textBuilder = new StringBuilder();
        try (Reader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            int c = 0;
            while ((c = reader.read()) != -1) {
                textBuilder.append((char) c);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return textBuilder.toString();
    }
}
