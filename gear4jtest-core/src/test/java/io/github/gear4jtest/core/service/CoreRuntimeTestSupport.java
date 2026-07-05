package io.github.gear4jtest.core.service;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.config.ParallelExecutionConfiguration;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.engine.AssemblyLineEngine;
import io.github.gear4jtest.core.engine.RuntimeExtensionResolver;
import io.github.gear4jtest.core.event.Event;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.service.steps.Step10;
import io.github.gear4jtest.core.service.steps.Step11;
import io.github.gear4jtest.core.service.steps.Step12;
import io.github.gear4jtest.core.service.steps.Step13;
import io.github.gear4jtest.core.service.steps.Step3;
import io.github.gear4jtest.core.service.steps.Step7;
import io.github.gear4jtest.core.service.steps.Step8;
import io.github.gear4jtest.core.service.steps.Step9;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;

import static org.assertj.core.api.Assertions.assertThat;

public final class CoreRuntimeTestSupport {
    private CoreRuntimeTestSupport() {
    }

    static AssemblyLineEngine newEngine(ResourceFactory resourceFactory) {
        return newEngine(resourceFactory, ParallelExecutionConfiguration.defaults());
    }

    static AssemblyLineEngine newEngine(ResourceFactory resourceFactory,
                                        ParallelExecutionConfiguration parallelExecutionConfiguration) {
        return AssemblyLineEngine.builder()
                .resourceFactory(resourceFactory)
                .extensionResolver(new RuntimeExtensionResolver(null))
                .executionContextRegistry(new ExecutionContextRegistry())
                .parallelExecutionConfiguration(parallelExecutionConfiguration)
                .build();
    }

    static ResourceFactory testResourceFactory() {
        return new TestResourceFactory();
    }

    static Map<String, Object> contextWithA() {
        Map<String, Object> context = new HashMap<>();
        context.put("a", 45612);
        return context;
    }

    static void awaitUntilAsserted(Runnable assertion) {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        AssertionError lastFailure = null;
        while (System.nanoTime() < deadlineNanos) {
            try {
                assertion.run();
                return;
            } catch (AssertionError e) {
                lastFailure = e;
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        assertion.run();
    }

    static final class TestResourceFactory implements ResourceFactory {
        private static final Map<Class<?>, Object> BEANS = createBeans();

        @Override
        public <T> T getResource(Class<T> clazz) {
            return clazz.cast(BEANS.get(clazz));
        }

        private static Map<Class<?>, Object> createBeans() {
            Map<Class<?>, Object> beans = new HashMap<>();
            beans.put(Step3.class, new Step3());
            beans.put(Step7.class, new Step7());
            beans.put(Step8.class, new Step8());
            beans.put(Step9.class, new Step9());
            beans.put(Step10.class, new Step10());
            beans.put(Step11.class, new Step11());
            beans.put(Step12.class, new Step12());
            beans.put(Step13.class, new Step13());
            beans.put(FailingPrimary.class, new FailingPrimary());
            beans.put(FallbackStep.class, new FallbackStep());
            return beans;
        }
    }

    static final class TestEventListener {
        private final AtomicInteger counter = new AtomicInteger();

        void handleEvent(Event e) {
            counter.incrementAndGet();
        }

        int getCounter() {
            return counter.get();
        }
    }

    public static final class TestPayload<T> implements Serializable {
        private final T object;

        public TestPayload(T object) {
            this.object = object;
        }

        public T getObject() {
            return object;
        }
    }

    public static final class FailingPrimary implements Operator<String, String> {
        @Override
        public String transform(String input,
                                StationExecutionContext operationExecution) {
            throw new IllegalStateException("primary failed");
        }
    }

    public static final class FallbackStep implements Operator<String, String> {
        @Override
        public String transform(String input,
                                StationExecutionContext operationExecution) {
            return "fallback-ok";
        }
    }

    static void assertParameterEventsPublished(TestEventListener listener) {
        awaitUntilAsserted(() -> assertThat(listener.getCounter()).isEqualTo(15));
    }
}
