package io.github.gear4jtest.xml.generator;

import java.util.List;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Condition;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ConditionalOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ContainerOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ErrorHandler;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.IfElseOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Operation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Parameters;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ProcessingOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.SignalOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.SubLine;
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
        XmlAssemblyLineDefinition definition = definition(ifElse);
        XmlToJavaGenerator safeGenerator = XmlToJavaGenerator.builder("io.test.generated")
                .classLoader(XmlToJavaGeneratorTest.class.getClassLoader())
                .formatter(JavaSourceFormatter.none())
                .build();

        // When / Then
        assertThatThrownBy(() -> safeGenerator.generate(definition)).isInstanceOf(SecurityException.class)
                .hasMessageContaining("Inline Java expressions are not allowed")
                .hasMessageContaining("expressionLength=")
                .hasMessageNotContaining("input.endsWith");
    }

    @Test
    void generate_shouldAllowGelConditionWithDefaultUntrustedGenerator() {
        // Given
        IfElseOperation ifElse = new IfElseOperation("choice", "java.lang.String", "java.lang.String",
                List.of(new ConditionalOperation("when-a",
                        new Condition("input == \"a\"", Condition.LANGUAGE_GEL, null),
                        processingOperation("then-operation"))),
                processingOperation("else-operation"));
        XmlAssemblyLineDefinition definition = definition(ifElse);
        XmlToJavaGenerator safeGenerator = XmlToJavaGenerator.builder("io.test.generated")
                .classLoader(XmlToJavaGeneratorTest.class.getClassLoader())
                .formatter(JavaSourceFormatter.none())
                .build();

        // When
        String source = safeGenerator.generate(definition).formattedSource();

        // Then
        assertThat(source).contains("private static final Map<String, GearExpression> GEL_EXPRESSIONS")
                .contains("evaluateGel(\"input == \\\"a\\\"\", input, ctx)")
                .contains("GearExpressionParser::parse")
                .doesNotContain("input.endsWith");
    }

    @Test
    void generate_shouldRenderSingleBranchContainerWithContainerResultsApi() {
        // Given
        ContainerOperation container = new ContainerOperation("single-container", "java.lang.String",
                "java.lang.String", false, null,
                List.of(new SubLine("only", null, processingOperation("only-operation"))),
                "results -> results.get(\"only\", String.class)");
        XmlAssemblyLineDefinition definition = definition(container);

        // When
        String source = generator.generate(definition).formattedSource();

        // Then
        assertThat(source).contains("private ContainerBaseStation<String, String> containerSingle_container()")
                .contains("return container(String.class)")
                .contains(".withBranch(\"only\", processOnly_operation())")
                .contains(".returns(results -> results.get(\"only\", String.class));")
                .doesNotContain("withSubLine")
                .doesNotContain("ContainerFunction")
                .doesNotContain("ElementModelBuilders");
    }

    @Test
    void generate_shouldRenderTwoBranchContainerWithContainerResultsApi() {
        // Given
        ContainerOperation container = new ContainerOperation("two-container", "java.lang.String",
                "java.lang.String", false, null,
                List.of(new SubLine("left", null, processingOperation("left-operation")),
                        new SubLine("right", null, processingOperation("right-operation"))),
                "results -> results.get(\"left\", String.class) + results.get(\"right\", String.class)");
        XmlAssemblyLineDefinition definition = definition(container);

        // When
        String source = generator.generate(definition).formattedSource();

        // Then
        assertThat(source).contains(".withBranch(\"left\", processLeft_operation())")
                .contains(".withBranch(\"right\", processRight_operation())")
                .contains(".returns(results -> results.get(\"left\", String.class) "
                        + "+ results.get(\"right\", String.class));")
                .doesNotContain("withSubLine")
                .doesNotContain("Object...");
    }

    @Test
    void generate_shouldRenderParallelThreeBranchContainerWithOrderedContainerResultsFallback() {
        // Given
        ContainerOperation container = new ContainerOperation("parallel-three", "java.lang.String",
                "java.util.List<java.lang.String>", true, 3,
                List.of(new SubLine("alpha", null, processingOperation("alpha-operation")),
                        new SubLine("beta", null, processingOperation("beta-operation")),
                        new SubLine("gamma", null, processingOperation("gamma-operation"))),
                "results -> results.orderedOutputs()");
        XmlAssemblyLineDefinition definition = definition(container);

        // When
        String source = generator.generate(definition).formattedSource();

        // Then
        assertThat(source).contains("@Inject(\"gear4j.executor.parallel-three\")")
                .contains("requireExecutorService(gear4jParallel_threeExecutorService, "
                        + "\"gear4j.executor.parallel-three\")")
                .contains(".withBranch(\"alpha\", processAlpha_operation())")
                .contains(".withBranch(\"beta\", processBeta_operation())")
                .contains(".withBranch(\"gamma\", processGamma_operation())")
                .contains(".returns(results -> results.orderedOutputs());")
                .doesNotContain("withSubLine")
                .doesNotContain("Container1Station")
                .doesNotContain("Container2Station");
    }

    @Test
    void generate_shouldKeepFlowSignalsSeparateFromErrorSignalPolicies() {
        // Given
        ProcessingOperation processing = new ProcessingOperation("guarded", StringOperator.class.getName(),
                "java.lang.String", new Parameters(List.of()),
                List.of(new ErrorHandler(false, "IGNORE", RuntimeException.class.getName(), null, null)),
                List.of(), null);
        SignalOperation signal = new SignalOperation("fatal-flow", "FATAL", "java.lang.String",
                new Condition("input.isBlank()", null));
        XmlAssemblyLineDefinition definition = definition(processing, signal);

        // When
        String source = generator.generate(definition).formattedSource();

        // Then
        assertThat(source).contains("Errors.<String>ignore(RuntimeException.class)")
                .contains(".type(SignalType.FATAL)")
                .doesNotContain("StationSignalType")
                .doesNotContain(".type(SignalType.IGNORE)");
    }

    @Test
    void generate_shouldRequireInjectedExecutorForParallelContainer() {
        // Given
        ProcessingOperation first = processingOperation("first");
        ProcessingOperation second = processingOperation("second");
        ContainerOperation container = new ContainerOperation("parallel-container", "java.lang.String",
                "java.lang.String", true, 2, List.of(new SubLine("first-line", null, first),
                                                     new SubLine("second-line", null, second)),
                "results -> results.get(\"first-line\", String.class)");
        XmlAssemblyLineDefinition definition = definition(container);

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
        XmlAssemblyLineDefinition definition = definition(ifElse);

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
        XmlAssemblyLineDefinition definition = definition(processingOperation("same-id"),
                                                          processingOperation("same_id"));

        // When / Then
        assertThatThrownBy(() -> generator.generate(definition)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Generated method name collision")
                .hasMessageContaining("same-id")
                .hasMessageContaining("same_id");
    }

    private static XmlAssemblyLineDefinition definition(Operation... operations) {
        return new XmlAssemblyLineDefinition("pipeline", "java.lang.String", "java.lang.String", List.of(operations),
                null,
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
