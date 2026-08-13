package io.github.gear4jtest.consumer;

import java.util.Map;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.AssemblyLineExecutor;
import io.github.gear4jtest.core.api.AssemblyLineExecutors;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.util.AssemblyLines;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;

import static io.github.gear4jtest.core.api.util.Stations.processingOperation;

/** Compile-backed source for the progressive first-use tutorial. */
public final class GettingStartedExample {
    private GettingStartedExample() {
    }

    /** Builds and executes the tutorial pipeline using only public API and SPI types. */
    public static ExecutionResult<String> run(String input) {
        ResourceFactory resources = new ReflectiveResourceFactory();

        AssemblyLine<String, String> greetingLine = AssemblyLines.<String>createAssemblyLine("greeting")
                .then(processingOperation("normalize-name", NormalizeName.class).build())
                .then(processingOperation("format-greeting", FormatGreeting.class).build())
                .build();

        AssemblyLineExecutor executor = AssemblyLineExecutors.create(resources);
        RunRequest<String> request = RunRequest.builder()
                .input(input)
                .context(Map.of("salutation", "Hello"))
                .build();

        return executor.execute(greetingLine, request);
    }

    /** Trims the value passed from the request. */
    public static final class NormalizeName implements Operator<String, String> {
        @Override
        public String transform(String input, StationExecutionContext context) {
            return input.trim();
        }
    }

    /** Reads run context without coupling the operator to engine internals. */
    public static final class FormatGreeting implements Operator<String, String> {
        @Override
        public String transform(String input, StationExecutionContext context) {
            String salutation = context.getGlobalContext()
                    .find("salutation", String.class)
                    .orElse("Hello");
            return salutation + ", " + input + "!";
        }
    }

    private static final class ReflectiveResourceFactory implements ResourceFactory {
        @Override
        public <T> T getResource(Class<T> type) {
            try {
                return type.cast(type.getDeclaredConstructor().newInstance());
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot create " + type.getName(), exception);
            }
        }
    }
}
