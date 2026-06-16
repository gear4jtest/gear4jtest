package io.github.gear4jtest.xml.generator;

import java.util.List;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.Condition;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.ConditionalOperation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.ContainerOperation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.IfElseOperation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.Operation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.Parameters;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.ProcessingOperation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.SubLine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XmlToJavaGeneratorTest {
    private final XmlToJavaGenerator generator = XmlToJavaGenerator.trusted("io.test.generated",
                                                                            XmlToJavaGeneratorTest.class
                                                                                    .getClassLoader(),
                                                                            JavaSourceFormatter.none());

    @Test
    void generate_shouldRejectInlineJavaByDefault() {
        // Given
        IfElseOperation ifElse = new IfElseOperation("choice", "java.lang.String", "java.lang.String",
                List.of(new ConditionalOperation("when-a", new Condition("input.endsWith(\"a\")", null),
                        processingOperation("then-operation"))),
                processingOperation("else-operation"));
        XmlPipelineDefinition definition = definition(ifElse);
        XmlToJavaGenerator safeGenerator = new XmlToJavaGenerator("io.test.generated",
                XmlToJavaGeneratorTest.class.getClassLoader(), JavaSourceFormatter.none());

        // When / Then
        assertThatThrownBy(() -> safeGenerator.generate(definition)).isInstanceOf(SecurityException.class)
                .hasMessageContaining("Inline Java expressions are not allowed");
    }

    @Test
    void generate_shouldAllowGelConditionWithDefaultUntrustedGenerator() {
        // Given
        IfElseOperation ifElse = new IfElseOperation("choice", "java.lang.String", "java.lang.String",
                List.of(new ConditionalOperation("when-a",
                        new Condition("input == \"a\"", Condition.LANGUAGE_GEL, null),
                        processingOperation("then-operation"))),
                processingOperation("else-operation"));
        XmlPipelineDefinition definition = definition(ifElse);
        XmlToJavaGenerator safeGenerator = new XmlToJavaGenerator("io.test.generated",
                XmlToJavaGeneratorTest.class.getClassLoader(), JavaSourceFormatter.none());

        // When
        String source = safeGenerator.generate(definition).formattedSource();

        // Then
        assertThat(source).contains("private static final Map<String, GearExpression> GEL_EXPRESSIONS")
                .contains("evaluateGel(\"input == \\\"a\\\"\", input, ctx)")
                .contains("GearExpressionParser::parse")
                .doesNotContain("input.endsWith");
    }

    @Test
    void generate_shouldRequireInjectedExecutorForParallelContainer() {
        // Given
        ProcessingOperation first = processingOperation("first");
        ProcessingOperation second = processingOperation("second");
        ContainerOperation container = new ContainerOperation("parallel-container", "java.lang.String",
                "java.lang.String", true, 2, List.of(new SubLine("first-line", null, first),
                                                     new SubLine("second-line", null, second)),
                "(first, second) -> first");
        XmlPipelineDefinition definition = definition(container);

        // When
        String source = generator.generate(definition).formattedSource();

        // Then
        assertThat(source).contains("@Inject(\"gear4j.executor.parallel-container\")")
                .contains("private ExecutorService gear4jParallel_containerExecutorService;")
                .contains("requireExecutorService(gear4jParallel_containerExecutorService, \"gear4j.executor.parallel-container\")")
                .contains("throw new IllegalStateException(\"Missing required ExecutorService bean '\" + beanName + \"' for parallel XML container\")")
                .doesNotContain("newFixedThreadPool")
                .doesNotContain("newCachedThreadPool")
                .doesNotContain("java.util.concurrent.Executors");
    }

    @Test
    void generate_shouldNotInjectExecutorForSequentialIfElseOperation() {
        // Given
        IfElseOperation ifElse = new IfElseOperation("choice", "java.lang.String", "java.lang.String",
                List.of(new ConditionalOperation("when-a", new Condition("input.endsWith(\"a\")", null),
                        processingOperation("then-operation"))),
                processingOperation("else-operation"));
        XmlPipelineDefinition definition = definition(ifElse);

        // When
        String source = generator.generate(definition).formattedSource();

        // Then
        assertThat(source).contains("ifElseContainer(String.class)")
                .doesNotContain("gear4j.executor.choice")
                .doesNotContain("ExecutorService")
                .doesNotContain("requireExecutorService");
    }

    @Test
    void generate_shouldRejectMethodNameCollisions() {
        // Given
        XmlPipelineDefinition definition = definition(processingOperation("same-id"), processingOperation("same_id"));

        // When / Then
        assertThatThrownBy(() -> generator.generate(definition)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Generated method name collision")
                .hasMessageContaining("same-id")
                .hasMessageContaining("same_id");
    }

    private static XmlPipelineDefinition definition(Operation... operations) {
        return new XmlPipelineDefinition("pipeline", "java.lang.String", "java.lang.String", List.of(operations), null,
                List.of());
    }

    private static ProcessingOperation processingOperation(String id) {
        return new ProcessingOperation(id, StringOperator.class.getName(), "java.lang.String",
                new Parameters(List.of()),
                List.of(), List.of(), null);
    }

    public static final class StringOperator implements Operator<String, String> {
        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            return input;
        }
    }
}
