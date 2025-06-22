package io.github.gear4jtest.core.model.refactor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.gear4jtest.core.event.OperationCompletedEvent;
import io.github.gear4jtest.core.event.OperationErrorEvent;
import io.github.gear4jtest.core.event.OperationStartedEvent;

public abstract class AbstractOperationDefinition<I, O> implements OperationDefinition<I, O> {
    protected String id;
    protected List<Processor> processors = new ArrayList<>();
    protected List<BaseError<I>> onErrors;
    protected List<Condition<I>> conditions = new ArrayList<>();
    protected Transformer<I, O> skipTransformer;

    public AbstractOperationDefinition(String id) { this.id = id; }

    public abstract O execute(I input, ExecutionContext context, OperationExecution operationExecution) throws Exception;

    @Override
    public final OperationResult<O> run(I input, ExecutionContext context) {
        var operationExecution = new OperationExecution(id);
        context.getEventManager().publishEvent(new OperationStartedEvent(context.getPipelineId(), context.getExecutionId().toString(), id, input));

        try {
            initialize(input, context, operationExecution);
            for (Condition<I> condition: conditions) {
                if (condition != null && !condition.test(input, context)) {
                    if (skipTransformer != null) {
                        O result = skipTransformer.transform(input, context);
                        return operationExecution.complete(result);
//                    return new ExecutionResult<>(result, true, null, report);
                    }
                    throw new RuntimeException("Operation skipped without transformer");
                }
            }

            for (Processor processor : processors) {
                processor.process(input, context, this, operationExecution);
            }

            O result = execute(input, context, operationExecution);

//            report.addOperation(id, true, System.currentTimeMillis() - start, null);
//            return new ExecutionResult<>(result, true, null, report);
            context.getEventManager().publishEvent(new OperationCompletedEvent(context.getPipelineId(), context.getExecutionId().toString(), id, input, result));
            return operationExecution.complete(result);
        } catch (Exception e) {
            context.getEventManager().publishEvent(new OperationErrorEvent(context.getPipelineId(), context.getExecutionId().toString(), id, input, e));

            return handleOnError(input, context, e, operationExecution);
//            report.addOperation(id, false, System.currentTimeMillis() - start, e);
//            return new ExecutionResult<>(null, false, e, report);
//            return operationExecution.fail(e);
        }
    }

    public void initialize(I input, ExecutionContext context, OperationExecution operationExecution) throws Exception {
        // Default implementation does nothing, can be overridden by subclasses
    }

    private OperationResult<O> handleOnError(I input, ExecutionContext context, Exception e, OperationExecution operationExecution) {
        for (BaseError<I> error : Optional.ofNullable(onErrors).orElse(List.of())) {
            try {
                if (error.throwableType.isInstance(e) && (error.condition == null || error.condition.test(input, context))) {
                    OperationResult<O> result = switch(error.signalType) {
                        case FATAL -> operationExecution.fail(e);
                        case STOP -> operationExecution.stop(e);
                        case IGNORE -> operationExecution.ignore(e);
                    };

                    if (error.action != null) {
                        error.action.run();
                    }
                    return result;
                }
            } catch (Exception handlerException) {
                // Log the error from the error handler
                operationExecution.getReport().addErrorHandlerException(handlerException);
            }
        }
        return operationExecution.fail(e);
    }

    public String getId() { return id; }

}
