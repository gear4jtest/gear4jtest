package io.github.gear4jtest.core.service;

import io.github.gear4jtest.core.model.refactor.AssemblyLineDefinition;
import io.github.gear4jtest.core.model.refactor.ContainerDefinition;
import io.github.gear4jtest.core.model.refactor.LineDefinition;
import io.github.gear4jtest.core.model.refactor.ProcessingOperationDefinition;
import io.github.gear4jtest.core.processor.operation.OperationParamsInjector;
import io.github.gear4jtest.core.service.steps.Step11;

import java.util.Arrays;
import java.util.List;

import static io.github.gear4jtest.core.model.ElementModelBuilders.*;

public class AssemblyLineSampleRefinedConfig {

    public static AssemblyLineDefinition<String, List<String>> assemblyLine() {
        return asssemblyLineDefinition("my-basic-assembly-line")
                .definition(MainLine.mainLine())
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

    private static class MainLine {
        private static LineDefinition<String, List<String>> mainLine() {
            return line(startingPointt(String.class))
                    .operator(step11())
                    .operator(containerr())
                    .build();
        }

        private static ProcessingOperationDefinition<String, String> step11() {
            return processingOperation(Step11.class)
                    .parameter(Step11::getParam, "a")
                    .build();
        }

        private static ContainerDefinition<String, List<String>> containerr() {
            return container(String.class)
                    .withSubLine(SubLine1.mainLine()).withSubLine(SubLine2.mainLine()).returns(Arrays::asList);
        }

        private static class SubLine1 {

            private static LineDefinition<String, String> mainLine() {
                return line(startingPointt(String.class))
                        .operator(step11())
                        .build();
            }

            private static ProcessingOperationDefinition<String, String> step11() {
                return processingOperation(Step11.class)
                        .parameter(Step11::getParam, "b")
                        .build();
            }
        }

        private static class SubLine2 {
            private static LineDefinition<String, String> mainLine() {
                return line(startingPointt(String.class))
                        .operator(step11())
                        .build();
            }

            private static ProcessingOperationDefinition<String, String> step11() {
                return processingOperation(Step11.class)
                        .parameter(Step11::getParam, "c")
                        .build();
            }
        }
    }
}
