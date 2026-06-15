package io.github.gear4jtest.xml.translator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.myorg.services.ModelsService;
import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.engine.PipelineEngine;
import io.github.gear4jtest.core.engine.RuntimeExtensionResolver;
import io.github.gear4jtest.core.engine.runner.RunnerChainFactory;
import io.github.gear4jtest.core.engine.strategy.StrategyRegistry;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.external.api.compiler.JDTInMemoryCompiler;
import io.github.gear4jtest.external.api.loader.GeneratedAssemblyLine;
import io.github.gear4jtest.external.api.loader.InMemoryClassLoader;
import io.github.gear4jtest.external.api.loader.SimpleDependencyInjector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XmlOperationChainTranslatorTest {
    private final XmlOperationChainTranslator translator = XmlOperationChainTranslator.trusted();

    private static PipelineEngine engine() {
        return PipelineEngine.builder().resourceFactory(reflectiveResourceFactory())
                .runnerChainFactory(new RunnerChainFactory(StrategyRegistry.defaultRegistry()))
                .extensionResolver(new RuntimeExtensionResolver(null))
                .executionContextRegistry(new ExecutionContextRegistry()).build();
    }

    private static ResourceFactory reflectiveResourceFactory() {
        return new ReflectiveResourceFactory();
    }

    private static byte[] resource(String name) throws IOException {
        try (var input = XmlOperationChainTranslatorTest.class.getResourceAsStream(name)) {
            if (input == null) {
                throw new IOException("Missing test resource: " + name);
            }
            return input.readAllBytes();
        }
    }

    @Test
    void defaultTranslator_shouldRejectInlineJavaExpressions() throws IOException {
        // Given
        byte[] xml = resource("/samples/assembly-line-signal.xml");
        XmlOperationChainTranslator safeTranslator = new XmlOperationChainTranslator();

        // When / Then
        assertThatThrownBy(() -> safeTranslator.translate(xml, "application/xml"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Inline Java expressions are not allowed");
    }

    @Test
    void should_translate_xml_to_external_api_generated_assembly_line() throws IOException {
        // Given
        byte[] xml = resource("/samples/assembly-line-iterator.xml");

        // When
        var result = translator.translate(xml, "application/xml");

        // Then
        assertThat(result.className()).isEqualTo("io.github.gear4jtest.xml.generated.Test_iteratorLine");
        assertThat(result.formattedSource()).contains("implements GeneratedAssemblyLine")
                .contains("@Inject(\"modelsService\")")
                .contains("private WorkStation<String, Map<String, String>> processStep3()")
                .contains("private IteratorStation<List<Integer>, List<List<String>>> iterateIterator()")
                .contains("ElementModelBuilders.<List<Integer>>iterate(\"iterator\")")
                .contains("public AssemblyLine<String, List<List<String>>> getAssemblyLineDefinition()")
                .contains("builder.skipIf((input, ctx) -> input.equals(modelsService.getModel(\"fjeifj\")))")
                .doesNotContain("@SuppressWarnings").doesNotContain("AbstractStation)")
                .doesNotContain("core.model.refactor").doesNotContain(".conditional(")
                .doesNotContain(".eventHandlingDefinition(");
    }

    @Test
    void should_generate_signal_condition_with_uniform_input_and_ctx_expression_variables() throws IOException {
        // Given
        byte[] xml = resource("/samples/assembly-line-signal.xml");

        // When
        var result = translator.translate(xml, XmlOperationChainTranslator.VENDOR_MEDIA_TYPE);

        // Then
        assertThat(result.formattedSource()).contains("private SignalStation<String> signalStop_when_a()")
                .contains("new SignalStation.Builder<String>()").contains(".id(\"stop_when_a\")")
                .contains(".type(SignalType.STOP)").contains("String input = sig.getItem();")
                .contains("var ctx = sig.getItemExecution();").contains("return input.endsWith(\"a\");");
    }

    @Test
    void should_compile_representative_generated_sources_against_current_core_api() throws IOException {
        // Given
        List<String> samples = List
                .of("/samples/good-assembly-line.xml", "/samples/sample-assembly-line.xml",
                    "/samples/assembly-line-iterator.xml", "/samples/assembly-line-parallel-container.xml",
                    "/samples/assembly-line-ifelse-container.xml", "/samples/assembly-line-signal.xml",
                    "/samples/assembly-line-v2.xml", "/samples/assembly-line-database-persistence.xml");
        var compiler = new JDTInMemoryCompiler(Thread.currentThread().getContextClassLoader());

        for (String sample : samples) {
            // When
            var result = translator.translate(resource(sample), XmlOperationChainTranslator.VENDOR_MEDIA_TYPE);
            var compiledClasses = compiler.compile(result.className(),
                                                   result.formattedSource().getBytes(StandardCharsets.UTF_8));

            // Then
            assertThat(compiledClasses).as("compiled classes for %s", sample).containsKey(result.className());
        }
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void should_translate_compile_instantiate_inject_and_execute_generated_pipeline() throws Exception {
        // Given
        byte[] xml = resource("/samples/assembly-line-iterator.xml");
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        var translated = translator.translate(xml, XmlOperationChainTranslator.VENDOR_MEDIA_TYPE);
        var compiledClasses = new JDTInMemoryCompiler(parent)
                .compile(translated.className(), translated.formattedSource().getBytes(StandardCharsets.UTF_8));
        var generatedClassLoader = new InMemoryClassLoader(parent);
        generatedClassLoader.addCompiledClasses(compiledClasses);

        // When
        Class<?> generatedClass = generatedClassLoader.loadClass(translated.className());
        var generated = (GeneratedAssemblyLine) generatedClass.getDeclaredConstructor().newInstance();
        var injector = new SimpleDependencyInjector();
        injector.registerBean("modelsService", new ModelsService());
        injector.injectDependencies(generated, ExecutionMode.TEST);
        AssemblyLine pipeline = generated.getAssemblyLineDefinition();
        ExecutionResult<?> result = engine().execute(pipeline, RunRequest.builder().input("b")
                .context(new HashMap<>(Map.of("a", 45612))).resourceFactory(reflectiveResourceFactory()).build());

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResult()).isEqualTo(List.of(List.of("1")));
    }

    private static final class ReflectiveResourceFactory implements ResourceFactory {
        @Override
        public <T> T getResource(Class<T> clazz) {
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
