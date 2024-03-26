package io.github.gear4jtest.core.service;

import io.github.gear4jtest.core.model.refactor.AssemblyLineDefinition;
import io.github.gear4jtest.core.model.refactor.ContainerBaseDefinition;
import io.github.gear4jtest.core.model.refactor.LineDefinition;
import io.github.gear4jtest.core.model.refactor.ProcessingOperationDefinition;
import io.github.gear4jtest.core.processor.operation.OperationParamsInjector;
import io.github.gear4jtest.core.service.steps.Step11;

import java.util.Arrays;
import java.util.List;

import static io.github.gear4jtest.core.model.ElementModelBuilders.*;

public class AssemblyLineSampleNoBrainConfig {

    private static LineDefinition<String, String> line1() {
        return line(startingPointt(String.class))
                .operator(step11())
                .build();
    }

    private static ProcessingOperationDefinition<String, String> step11() {
        return processingOperation(Step11.class)
                .parameter(Step11::getParam, "b")
                .build();
    }

    private static LineDefinition<String, String> line2() {
        return line(startingPointt(String.class))
                .operator(step112())
                .build();
    }

    private static ProcessingOperationDefinition<String, String> step112() {
        return processingOperation(Step11.class)
                .parameter(Step11::getParam, "c")
                .build();
    }

    private static ContainerBaseDefinition<String, List<String>> container1() {
        return container(String.class)
                .withSubLine(line1()).withSubLine(line2()).returns((a, b) -> Arrays.asList(a, b));
    }

    private static LineDefinition<String, List<String>> mainLine() {
        return line(startingPointt(String.class))
                .operator(step110())
                .operator(container1())
                .build();
    }

    private static ProcessingOperationDefinition<String, String> step110() {
        return processingOperation(Step11.class)
                .parameter(Step11::getParam, "a")
                .build();
    }

    public static AssemblyLineDefinition<String, List<String>> assemblyLine() {
        return asssemblyLineDefinition("my-basic-assembly-line")
                .definition(mainLine())
                .configuration(configuration()
                        .stepDefaultConfiguration(operationConfiguration()
                                .preProcessors(Arrays.asList(OperationParamsInjector.class))
                                .build())
                        .eventHandlingDefinition(eventHandling()
                                .queue(queue().eventListener(new SimpleChainBuilderTest.TestEventListener()).build())
                                .globalEventConfiguration(eventConfiguration().eventOnParameterChanged(true).build())
                                .build())
                        .build())
                .build();
    }
}
