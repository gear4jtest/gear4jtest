package io.test.gear4jtest.xml.generator;

import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import io.github.gear4jtest.core.model.ElementModelBuilders;
import io.test.gear4test.xml.generator.AssemblyLineGenerator;
import io.test.gear4test.xml.validator.AssemblyLineValidator;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXParseException;

import javax.tools.*;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AssemblyLineGeneratorTest {

    @Test
    void testXsdGenerationSuccessful() throws IOException {
        // Given
        InputStream io = getClass().getResourceAsStream("/samples/sample-assembly-line.xml");

        // When
        TypeSpec typeSpec = AssemblyLineGenerator.generateFlatAssemblyLines(io);
        JavaFile javaFile = JavaFile.builder("com.myorg.assemblylines.generated", typeSpec)
                .indent("    ")
                .skipJavaLangImports(true)
                .addStaticImport(ElementModelBuilders.class, "*")
                .build();

        // Then
        javaFile.writeTo(new File("/tmp/a.java"));
        Appendable writer = new StringWriter();
        javaFile.writeTo(writer);
        assertThat(writer.toString())
                .as("Java file content should be equal")
                .isEqualTo(inputStreamToString(getClass().getResourceAsStream("/samples/MyInsaneAssemblyLine.java")));

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
