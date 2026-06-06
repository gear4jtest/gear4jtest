package io.github.gear4jtest.xml.model;

import java.util.List;

public record XmlPipelineDefinition(String id,
                                    String inputType,
                                    String outputType,
                                    List<Operation> operations,
                                    Configuration configuration,
                                    List<Dependency> dependencies) {
    public sealed interface Operation
            permits ProcessingOperation, IteratorOperation, ContainerOperation, IfElseOperation, SignalOperation {
        String id();
    }

    public sealed interface Parameter permits ValueParameter, SupplierParameter, ContextParameter {
        String retriever();
    }

    public record ProcessingOperation(String id,
                                      String type,
                                      String inputType,
                                      Parameters parameters,
                                      List<ErrorHandler> errorHandlers,
                                      List<Condition> conditions,
                                      Transformer fallbackTransformer)
            implements Operation {}

    public record IteratorOperation(String id,
                                    String inputType,
                                    String outputType,
                                    String iterableFunction,
                                    Operation operation,
                                    String accumulator,
                                    String collector)
            implements Operation {}

    public record ContainerOperation(String id,
                                     String inputType,
                                     String outputType,
                                     boolean parallel,
                                     Integer threadPoolSize,
                                     List<SubLine> subLines,
                                     String returnsFunction)
            implements Operation {}

    public record IfElseOperation(String id,
                                  String inputType,
                                  String outputType,
                                  List<ConditionalOperation> conditionalOperations,
                                  ProcessingOperation elseOperation)
            implements Operation {}

    public record SignalOperation(String id, String type, String inputType, Condition condition) implements Operation {}

    public record Parameters(List<Parameter> parameters) {}

    public record ValueParameter(String retriever, String value, String valueType) implements Parameter {}

    public record SupplierParameter(String retriever, String supplier) implements Parameter {}

    public record ContextParameter(String retriever, String function) implements Parameter {}

    public record ErrorHandler(boolean safe,
                               String signalType,
                               String throwableType,
                               Condition condition,
                               Action action) {}

    public record Condition(String expression, String description) {}

    public record Action(String expression, String description) {}

    public record Transformer(String expression, String inputType, String outputType) {}

    public record SubLine(String id, Condition condition, Operation operation) {}

    public record ConditionalOperation(String id, Condition condition, ProcessingOperation operation) {}

    public record Configuration(EventHandling eventHandling, Persistence persistence) {}

    public record EventHandling(Boolean eventOnParameterChanged) {}

    public record Persistence(Boolean storeResultObject) {}

    public record Dependency(String name, String type) {}
}
