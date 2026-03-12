//package io.github.gear4jtest.core.sidecompute;
//
//import java.util.List;
//import java.util.concurrent.CompletableFuture;
//
//import io.github.gear4jtest.core.event.Event;
//import io.github.gear4jtest.core.event.OperationCompletedEvent;
//import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
//import io.github.gear4jtest.core.model.ExecutionContext;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//class SideComputeListenerTest {
//
//    @Test
//    void handleEvent_shouldIgnoreNonOperationCompletedEvent() {
//        ExecutionContextRegistry registry = mock(ExecutionContextRegistry.class);
//        SideComputer<String> sc = mock(SideComputer.class);
//
//        SideComputeListener listener = new SideComputeListener(List.of(sc), registry);
//
//        Event e = mock(Event.class);
//        listener.handleEvent(e);
//
//        verifyNoInteractions(registry, sc);
//    }
//
//    @Test
//    void handleEvent_shouldDoNothingWhenExecutionContextNotFound() {
//        ExecutionContextRegistry registry = mock(ExecutionContextRegistry.class);
//        SideComputer<String> sc = mock(SideComputer.class);
//        OperationCompletedEvent event = new OperationCompletedEvent("pipe", "exec-1", "op-1", "in", "out");
//
//        when(registry.get("exec-1")).thenReturn(null);
//
//        SideComputeListener listener = new SideComputeListener(List.of(sc), registry);
//
//        listener.handleEvent(event);
//
//        verify(registry).get("exec-1");
//        verifyNoMoreInteractions(registry);
//        verifyNoInteractions(sc);
//    }
//
//    @Test
//    void handleEvent_shouldRunMatchingSideComputerAndCompleteFuture() {
//        ExecutionContextRegistry registry = mock(ExecutionContextRegistry.class);
//        ExecutionContext execCtx = mock(ExecutionContext.class);
//        SideComputeContext scCtx = new SideComputeContext();
//
//        when(registry.get("exec-1")).thenReturn(execCtx);
//        when(execCtx.getSideComputeContext()).thenReturn(scCtx);
//
//        OperationCompletedEvent event = new OperationCompletedEvent("pipe", "exec-1", "op-1", "in", "out");
//
//        // SideComputer réel
//        SideComputer<String> sc = SideComputer.of("op-1", "key1", ev -> "computed-" + ev.getExecutionId());
//
//        SideComputeListener listener = new SideComputeListener(List.of(sc), registry);
//
//        listener.handleEvent(event);
//
//        CompletableFuture<String> future = scCtx.getOrCreateFuture("key1");
//        assertThat(future)
//                .isCompletedWithValue("computed-exec-1");
//    }
//
//    @Test
//    void handleEvent_shouldCompleteFutureExceptionallyWhenComputerThrows() {
//        ExecutionContextRegistry registry = mock(ExecutionContextRegistry.class);
//        ExecutionContext execCtx = mock(ExecutionContext.class);
//        SideComputeContext scCtx = new SideComputeContext();
//
//        when(registry.get("exec-1")).thenReturn(execCtx);
//        when(execCtx.getSideComputeContext()).thenReturn(scCtx);
//
//        OperationCompletedEvent event = new OperationCompletedEvent("pipe", "exec-1", "op-1", "in", "out");
//
//        RuntimeException boom = new RuntimeException("boom");
//        SideComputer<String> sc = SideComputer.of("op-1", "key1", ev -> { throw boom; });
//
//        SideComputeListener listener = new SideComputeListener(List.of(sc), registry);
//        listener.handleEvent(event);
//
//        CompletableFuture<String> future = scCtx.getOrCreateFuture("key1");
//
//        assertThat(future)
//                .isCompletedExceptionally();
//    }
//}
