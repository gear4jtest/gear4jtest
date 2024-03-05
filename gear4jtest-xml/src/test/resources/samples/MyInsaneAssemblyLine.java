package com.myorg.assemblylines.generated;

import static io.github.gear4jtest.core.model.ElementModelBuilders.*;

import com.myorg.operation.Step11;
import io.github.gear4jtest.core.model.refactor.AssemblyLineDefinition;
import io.github.gear4jtest.core.model.refactor.ContainerDefinition;
import io.github.gear4jtest.core.model.refactor.LineDefinition;
import io.github.gear4jtest.core.model.refactor.ProcessingOperationDefinition;
import java.util.List;

public class MyInsaneAssemblyLine {
    public static AssemblyLineDefinition<String, List<String>> assemblyLine() {
        return asssemblyLineDefinition("MyInsaneAssemblyLine")
                .definition(MainLine.mainLine())
                .build();
    }

    private static final class MainLine {
        private static ProcessingOperationDefinition<String, String> step11() {
            return processingOperation(Step11.class)
                    .parameter(Step11::getParam, "a")
                    .build();
        }

        private static ContainerDefinition<String, List<String>> containerDefinition() {
            return container(ContainerFirstSubLine.mainLine())
                    .withSubLine(ContainerSecondSubLine.mainLine())
                    .returns((a, b) -> java.util.Arrays.asList(a, b));
        }

        private static LineDefinition<String, List<String>> mainLine() {
            return line(step11())
                    .operator(containerDefinition())
                    .build();
        }

        private static final class ContainerFirstSubLine {
            private static ProcessingOperationDefinition<String, String> step11() {
                return processingOperation(Step11.class)
                        .parameter(Step11::getParam, "b")
                        .build();
            }

            private static LineDefinition<String, String> mainLine() {
                return line(step11())
                        .build();
            }
        }

        private static final class ContainerSecondSubLine {
            private static ProcessingOperationDefinition<String, String> step11() {
                return processingOperation(Step11.class)
                        .parameter(Step11::getParam, "c")
                        .build();
            }

            private static LineDefinition<String, String> mainLine() {
                return line(step11())
                        .build();
            }
        }
    }
}
