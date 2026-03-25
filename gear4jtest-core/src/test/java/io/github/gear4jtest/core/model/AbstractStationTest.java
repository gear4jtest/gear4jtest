//package io.github.gear4jtest.core.model;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.*;
//
//import java.util.List;
//import java.util.UUID;
//
//import io.github.gear4jtest.core.api.station.AbstractStation;
//import io.github.gear4jtest.core.api.context.ExecutionContext;
//import io.github.gear4jtest.core.api.behavior.Processor;
//import io.github.gear4jtest.core.api.context.StationExecutionContext;
//import io.github.gear4jtest.core.api.station.StationKind;
//import org.junit.jupiter.api.Test;
//
//import io.github.gear4jtest.core.event.EventManager;
//import io.github.gear4jtest.core.event.OperationCompletedEvent;
//import io.github.gear4jtest.core.event.OperationErrorEvent;
//import io.github.gear4jtest.core.event.OperationStartedEvent;
//import io.github.gear4jtest.core.execution.AssemblyRunManager;
//import io.github.gear4jtest.core.spi.factory.ResourceFactory;
//import io.github.gear4jtest.core.persistence.StationLog;
//
//class AbstractStationTest {
//
//    static class RecordingProcessor implements Processor {
//        int beforeCount;
//        int afterCount;
//
//        @Override
//        public <I> void beforeExecution(I input, StationExecutionContext ctx) throws Exception {
//            beforeCount++;
//        }
//
//        @Override
//        public void afterExecution(Object result, StationExecutionContext context) {
//            afterCount++;
//        }
//    }
//
//    static class TestOperation extends AbstractStation<String, String> {
//
//        boolean setUpCalled;
//        boolean releaseCalled;
//        String releasedResult;
//        List<Throwable> releasedErrors;
//
//        TestOperation(String id, StationKind kind) {
//            super(id, kind);
//        }
//
//        @Override
//        protected void setUp(String input,
//                             ExecutionContext context,
//                             StationExecutionContext operationExecution) {
//            setUpCalled = true;
//        }
//
//        @Override
//        protected void release(StationExecutionContext context,
//                               String result,
//                               List<Throwable> errors) {
//            releaseCalled = true;
//            releasedResult = result;
//            releasedErrors = errors;
//        }
//
//        @Override
//        protected String doExecute(String input,
//                                   ExecutionContext globalContext,
//                                   StationExecutionContext opContext) {
//            return input.toUpperCase();
//        }
//    }
//
//    static class FailingOperation extends AbstractStation<String, String> {
//
//        FailingOperation(String id, StationKind kind) {
//            super(id, kind);
//        }
//
//        @Override
//        protected String doExecute(String input,
//                                   ExecutionContext globalContext,
//                                   StationExecutionContext opContext) {
//            throw new RuntimeException("boom");
//        }
//    }
//
//    @Test
//    void run_shouldExecuteSuccessfullyWithProcessorsAndEventsAndAppendRecord() {
//        EventManager eventManager = mock(EventManager.class);
//        ResourceFactory resourceFactory = mock(ResourceFactory.class);
//        AssemblyRunManager executionManager = mock(AssemblyRunManager.class);
//
//        ExecutionContext globalContext =
//                new ExecutionContext(UUID.randomUUID(), "pipeline-1", eventManager, resourceFactory, executionManager, null);
//
//        TestOperation op = new TestOperation("op-1", StationKind.PROCESSING);
//        RecordingProcessor processor = new RecordingProcessor();
//        op.processors = List.of(processor);
//
//        StationLog record = op.run("hello", globalContext);
//
//        assertThat(record.getStatus()).isEqualTo(StationLog.Status.SUCCEEDED);
//        assertThat(record.getOutput(String.class)).isEqualTo("HELLO");
//
//        assertThat(processor.beforeCount).isEqualTo(1);
//        assertThat(processor.afterCount).isEqualTo(1);
//
//        assertThat(op.setUpCalled).isTrue();
//        assertThat(op.releaseCalled).isTrue();
//        assertThat(op.releasedResult).isEqualTo("HELLO");
//
//        // Le record doit avoir été appended dans le manager
////        verify(executionManager).append(record);
//
//        // Les events STARTED et COMPLETED doivent être publiés
//        verify(eventManager).publish(any(OperationStartedEvent.class));
//        verify(eventManager).publish(any(OperationCompletedEvent.class));
//        verify(eventManager, never()).publish(any(OperationErrorEvent.class));
//    }
//
//    @Test
//    void run_shouldMarkFailedAndPublishErrorEventOnException() {
//        EventManager eventManager = mock(EventManager.class);
//        ResourceFactory resourceFactory = mock(ResourceFactory.class);
//        AssemblyRunManager executionManager = mock(AssemblyRunManager.class);
//
//        ExecutionContext globalContext =
//                new ExecutionContext(UUID.randomUUID(), "pipeline-1", eventManager, resourceFactory, executionManager, null);
//
//        FailingOperation op = new FailingOperation("op-err", StationKind.PROCESSING);
//
//        StationLog record = op.run("input", globalContext);
//
//        assertThat(record.getStatus()).isEqualTo(StationLog.Status.FAILED);
//        assertThat(record.getOutput(Object.class)).isNull();
//
////        verify(executionManager).append(record);
//        verify(eventManager).publish(any(OperationStartedEvent.class));
//        verify(eventManager).publish(any(OperationErrorEvent.class));
//        // Pas de COMPLETED dans ce scénario
//        verify(eventManager, never()).publish(any(OperationCompletedEvent.class));
//    }
//
//    @Test
//    void run_shouldCollectProcessorExceptionsAsErrorHandlers() {
//        EventManager eventManager = mock(EventManager.class);
//        ResourceFactory resourceFactory = mock(ResourceFactory.class);
//        AssemblyRunManager executionManager = mock(AssemblyRunManager.class);
//
//        ExecutionContext globalContext =
//                new ExecutionContext(UUID.randomUUID(), "pipeline-1", eventManager, resourceFactory, executionManager, null);
//
//        TestOperation op = new TestOperation("op-1", StationKind.PROCESSING);
//
//        Processor throwingProcessor = new Processor() {
//            @Override
//            public <I> void beforeExecution(I input, StationExecutionContext ctx) throws Exception {
//                throw new RuntimeException("pre boom");
//            }
//
//            @Override
//            public void afterExecution(Object result, StationExecutionContext context) {
//                // no-op
//            }
//        };
//
//        op.processors = List.of(throwingProcessor);
//
//        StationLog record = op.run("hello", globalContext);
//
//        // L'opération reste SUCCEEDED (la logique métier passe)
//        assertThat(record.getStatus()).isEqualTo(StationLog.Status.SUCCEEDED);
//        assertThat(record.getOutput(String.class)).isEqualTo("HELLO");
//
//        // Les exceptions de processors doivent apparaître dans les throwables
//        assertThat(record.getThrowables()).isNotNull();
//        assertThat(record.getThrowables()).hasSize(1);
//        assertThat(record.getThrowables().get(0))
//                .isInstanceOf(RuntimeException.class)
//                .hasMessageContaining("pre boom");
//    }
//}
