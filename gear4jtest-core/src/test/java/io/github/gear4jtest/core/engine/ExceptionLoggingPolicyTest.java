package io.github.gear4jtest.core.engine;

import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.trace.RunTrace;
import io.github.gear4jtest.core.api.util.AssemblyLines;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.core.spi.extension.LifecycleFailureMode;
import io.github.gear4jtest.core.spi.extension.RunLifecycleExtension;
import io.github.gear4jtest.core.spi.extension.RuntimeExtension;
import io.github.gear4jtest.core.spi.extension.StationLifecycleExtension;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import io.github.gear4jtest.core.util.ExceptionDiagnostics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class ExceptionLoggingPolicyTest {
    private final String previousProperty = System.getProperty(ExceptionDiagnostics.INCLUDE_DETAILS_PROPERTY);

    @AfterEach
    void restoreProperty() {
        setProperty(previousProperty);
    }

    @ParameterizedTest
    @EnumSource(Callback.class)
    void protectedLogs_shouldExcludeMessagesCausesAndSuppressedWhileResultKeepsThem(Callback callback) {
        // Given
        setProperty(null);
        var original = sensitiveFailure();
        try (var logs = new CapturedLogs()) {
            // When
            var result = execute(extension(callback, original));

            // Then: assertions inspect actual Logback rendering, not a mocked logger.
            assertThat(logs.appender.list).isNotEmpty();
            for (ILoggingEvent event : logs.appender.list) {
                String rendered = event.getFormattedMessage() + ThrowableProxyUtil.asString(event.getThrowableProxy());
                assertThat(rendered).contains(IllegalStateException.class.getName())
                        .doesNotContain("SECRET_MESSAGE", "SECRET_CAUSE", "SECRET_SUPPRESSED", "SECRET_FRAME");
                assertThat(event.getThrowableProxy().getCause()).isNull();
            }
            if (callback == Callback.STATION_STARTED) {
                assertThat(result.getError().getCause()).hasCause(original);
            } else {
                assertThat(result.getError()).isSameAs(original);
            }
            assertThat(original.getMessage()).contains("SECRET_MESSAGE");
            assertThat(original.getCause()).hasMessage("SECRET_CAUSE");
            assertThat(original.getSuppressed()).hasSize(1);
        }
    }

    @Test
    void explicitOptIn_shouldRenderTheOriginalExceptionGraph() {
        // Given
        setProperty("true");
        var original = sensitiveFailure();
        try (var logs = new CapturedLogs()) {
            // When
            execute(extension(Callback.RUN_STARTED, original));
            // Then
            assertThat(logs.appender.list).isNotEmpty();
            String rendered = ThrowableProxyUtil.asString(logs.appender.list.get(0).getThrowableProxy());
            assertThat(rendered).contains("SECRET_MESSAGE", "SECRET_CAUSE", "SECRET_SUPPRESSED", "SECRET_FRAME");
            assertThat(ExceptionDiagnostics.forLogging(original)).isSameAs(original);
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "false", "yes", "", " true " })
    void absentOrInvalidOptIn_shouldFailClosedWithoutInspectingTheFailure(String configured) {
        // Given
        setProperty(configured);
        var hostile = new RuntimeException() {
            @Override
            public String getMessage() {
                throw new AssertionError("message must not be read");
            }

            @Override
            public String toString() {
                throw new AssertionError("toString must not be called");
            }
        };
        // When / Then
        Throwable diagnostic = ExceptionDiagnostics.forLogging(hostile);
        assertThat(diagnostic).isNotSameAs(hostile);
        assertThat(diagnostic.getCause()).isNull();
        assertThat(diagnostic.getSuppressed()).isEmpty();
        assertThat(diagnostic.getStackTrace()).isEmpty();
        assertThat(ExceptionDiagnostics.forLogging(null)).isNull();
    }

    private static IllegalStateException sensitiveFailure() {
        var failure = new IllegalStateException("SECRET_MESSAGE", new IllegalArgumentException("SECRET_CAUSE"));
        failure.addSuppressed(new IllegalArgumentException("SECRET_SUPPRESSED"));
        failure.setStackTrace(new StackTraceElement[] { new StackTraceElement("SECRET_FRAME", "method", "source", 1) });
        return failure;
    }

    private static RuntimeExtension extension(Callback callback, RuntimeException failure) {
        if (callback == Callback.STATION_STARTED) {
            return new StationLifecycleExtension() {
                @Override
                public LifecycleFailureMode failureMode() {
                    return LifecycleFailureMode.CRITICAL;
                }

                @Override
                public void onStationStarted(ExecutionContext run,
                                             StationExecutionContext station,
                                             StationLogRecord log) {
                    throw failure;
                }
            };
        }
        return new RunLifecycleExtension() {
            @Override
            public LifecycleFailureMode failureMode() {
                return LifecycleFailureMode.CRITICAL;
            }

            @Override
            public void onRunStarted(ExecutionContext context, RunTrace trace) {
                if (callback == Callback.RUN_STARTED) {
                    throw failure;
                }
            }

            @Override
            public void onRunCompleted(ExecutionContext context, RunTrace trace) {
                if (callback == Callback.RUN_COMPLETED) {
                    throw failure;
                }
            }
        };
    }

    private static io.github.gear4jtest.core.api.ExecutionResult<String> execute(RuntimeExtension extension) {
        var engine = AssemblyLineEngine.builder().resourceFactory(new ResourceFactory() {
            @Override
            public <T> T getResource(Class<T> type) {
                return null;
            }
        }).extensionResolver(new RuntimeExtensionResolver(List.of(extension)))
                .executionContextRegistry(new ExecutionContextRegistry()).build();
        return engine.execute(AssemblyLines.<String>createAssemblyLine("logging-policy").build(),
                              RunRequest.<String>builder().input("input").build());
    }

    private static void setProperty(String value) {
        if (value == null) {
            System.clearProperty(ExceptionDiagnostics.INCLUDE_DETAILS_PROPERTY);
        } else {
            System.setProperty(ExceptionDiagnostics.INCLUDE_DETAILS_PROPERTY, value);
        }
    }

    private enum Callback {
        RUN_STARTED, RUN_COMPLETED, STATION_STARTED
    }

    private static final class CapturedLogs implements AutoCloseable {
        private final Logger logger = (Logger) LoggerFactory.getLogger("io.github.gear4jtest.core");
        private final Level previousLevel = logger.getLevel();
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

        private CapturedLogs() {
            logger.setLevel(Level.ERROR);
            appender.start();
            logger.addAppender(appender);
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
        }
    }
}
