package io.github.gear4jtest.core.api.station;

import java.util.List;
import java.util.Objects;

import io.github.gear4jtest.core.api.StationMetadata;
import io.github.gear4jtest.core.api.behavior.BaseError;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.behavior.Processor;
import io.github.gear4jtest.core.api.behavior.StationSkipper;

/**
 * Base class for all station definitions.
 *
 * <p>
 * A station definition describes one immutable node of a pipeline graph. It
 * carries runtime behavior such as processors, error handlers, skip predicates,
 * fallback behavior and metadata, but it is not itself an execution trace.
 * Runtime outcomes are recorded separately by station log traces.
 * </p>
 */
public abstract class AbstractStation<I, O> {
    private final String id;
    private final StationKind kind;
    private final List<Processor> processors;
    private final List<BaseError<I>> onErrors;
    private final Operator<I, O> fallbackOperator;
    private final boolean unary;
    private final List<StationSkipper> skippers;
    private final StationMetadata metadata;

    /**
     * Creates a fully initialized immutable station definition.
     *
     * @param id               station identifier used in traces and generated code
     * @param kind             station kind used for strategy dispatch
     * @param processors       processors applied around the station execution
     * @param onErrors         station error policies
     * @param fallbackOperator fallback operator used by skip/error policies
     * @param unary            whether the station input and output types are
     *                         identical
     * @param skippers         ordered skip predicates
     * @param metadata         typed metadata attached to the station
     */
    protected AbstractStation(String id,
                              StationKind kind,
                              List<Processor> processors,
                              List<BaseError<I>> onErrors,
                              Operator<I, O> fallbackOperator,
                              boolean unary,
                              List<StationSkipper> skippers,
                              StationMetadata metadata) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.kind = Objects.requireNonNull(kind, "kind is required");
        this.processors = immutableList(processors);
        this.onErrors = immutableList(onErrors);
        this.fallbackOperator = fallbackOperator;
        this.unary = unary;
        this.skippers = immutableList(skippers);
        this.metadata = metadata == null ? StationMetadata.empty() : metadata;
    }

    private static <T> List<T> immutableList(List<T> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().filter(Objects::nonNull).toList();
    }

    public String getId() {
        return id;
    }

    public StationKind getKind() {
        return kind;
    }

    public List<Processor> getProcessors() {
        return processors;
    }

    public List<BaseError<I>> getOnErrors() {
        return onErrors;
    }

    /**
     * Returns the ordered skip predicates configured on this station.
     */
    public List<StationSkipper> getSkippers() {
        return skippers;
    }

    public StationMetadata getMetadata() {
        return metadata;
    }

    public Operator<I, O> getFallbackOperator() {
        return fallbackOperator;
    }

    public boolean isUnary() {
        return unary;
    }
}
