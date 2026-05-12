package io.github.gear4jtest.core.api.station;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.github.gear4jtest.core.api.MutableStationMetadata;
import io.github.gear4jtest.core.api.StationMetadata;
import io.github.gear4jtest.core.api.behavior.BaseError;
import io.github.gear4jtest.core.api.behavior.Condition;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.behavior.Processor;
import io.github.gear4jtest.core.api.behavior.StationSkipTest;
import io.github.gear4jtest.core.api.behavior.StationSkipper;

/**
 * Base class for all station definitions.
 *
 * <p>
 * A station definition describes one node of a pipeline graph. It carries
 * runtime behavior such as processors, error handlers, skip predicates,
 * fallback behavior and metadata, but it is not itself an execution trace.
 * Runtime outcomes are recorded separately by station log traces.
 * </p>
 */
public abstract class AbstractStation<I, O> {

    private final List<StationSkipper> skippers = new ArrayList<>();
    private final MutableStationMetadata metadata = new MutableStationMetadata();
    protected String id;
    protected StationKind kind;
    protected List<Processor> processors;
    protected List<BaseError<I>> onErrors;
    protected List<Condition<I>> conditions;
    protected Operator<I, O> fallbackOperator;
    protected Boolean unary;

    /**
     * Creates a station definition.
     *
     * @param id   station identifier used in traces and generated code
     * @param kind station kind used for strategy dispatch
     */
    protected AbstractStation(String id, StationKind kind) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.kind = Objects.requireNonNull(kind, "kind is required");
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

    // ------------------------------------------------------------------------
    // Skippers
    // ------------------------------------------------------------------------

    /**
     * Returns the ordered skip predicates configured on this station.
     */
    public List<StationSkipper> getSkippers() {
        return Collections.unmodifiableList(skippers);
    }

    /**
     * Adds a station skipper to the station definition.
     */
    public AbstractStation<I, O> addSkipper(StationSkipper skipper) {
        if (skipper != null) {
            skippers.add(skipper);
        }
        return this;
    }

    /**
     * Adds a pre-execution skip predicate.
     *
     * @param predicate predicate evaluated before station execution
     * @return this station definition
     */
    public AbstractStation<I, O> skipIf(StationSkipTest predicate) {
        return addSkipper(StationSkipper.pre(predicate));
    }

    /**
     * Adds a post-execution skip predicate.
     *
     * @param predicate predicate evaluated with access to the station execution
     *                  context
     * @return this station definition
     */
    public AbstractStation<I, O> skipIfPost(StationSkipTest predicate) {
        return addSkipper(StationSkipper.post(predicate));
    }

    /**
     * Compatibility alias for {@link #skipIf(StationSkipTest)}.
     */
    public AbstractStation<I, O> condition(StationSkipTest predicate) {
        return skipIf(predicate);
    }

    // ------------------------------------------------------------------------
    // Metadata
    // ------------------------------------------------------------------------

    public StationMetadata getMetadata() {
        return metadata;
    }

    /**
     * Returns mutable metadata attached to this station definition.
     */
    public MutableStationMetadata mutableMetadata() {
        return metadata;
    }

    /**
     * Stores a typed metadata value on this station definition.
     */
    public <T> AbstractStation<I, O> putMetadata(Class<T> type, T value) {
        metadata.put(type, value);
        return this;
    }

    public Operator<I, O> getFallbackOperator() {
        return fallbackOperator;
    }

    public Boolean getUnary() {
        return unary;
    }
}
