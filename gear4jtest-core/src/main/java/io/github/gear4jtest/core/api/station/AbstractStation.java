package io.github.gear4jtest.core.api.station;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.github.gear4jtest.core.api.behavior.BaseError;
import io.github.gear4jtest.core.api.behavior.Condition;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.behavior.Processor;
import io.github.gear4jtest.core.api.behavior.StationSkipper;
import io.github.gear4jtest.core.api.MutableStationMetadata;
import io.github.gear4jtest.core.api.StationMetadata;
import io.github.gear4jtest.core.api.behavior.StationSkipTest;

/**
 * Template commun à toutes les OperationDefinition.
 * - Gère la création du record
 * - Gère les events
 * - Gère les processors
 * - Gère le wiring avec le PipelineExecutionManager
 */
public abstract class AbstractStation<I, O> {

    protected String id;
    protected StationKind kind;
    protected List<Processor> processors;
    protected List<BaseError<I>> onErrors;
    protected List<Condition<I>> conditions;

    private final List<StationSkipper> skippers = new ArrayList<>();

    private final MutableStationMetadata metadata = new MutableStationMetadata();

    protected Operator<I, O> fallbackOperator;
    protected Boolean unary;

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

    public List<StationSkipper> getSkippers() {
        return Collections.unmodifiableList(skippers);
    }

    public AbstractStation<I, O> addSkipper(StationSkipper skipper) {
        if (skipper != null) {
            skippers.add(skipper);
        }
        return this;
    }

    /**
     * DSL : skipper PRE (équivalent de l'ancien concept "condition" runtime).
     */
    public AbstractStation<I, O> skipIf(StationSkipTest predicate) {
        return addSkipper(StationSkipper.pre(predicate));
    }

    /**
     * DSL : skipper POST (accès au StationExecutionContext).
     */
    public AbstractStation<I, O> skipIfPost(StationSkipTest predicate) {
        return addSkipper(StationSkipper.post(predicate));
    }

    /**
     * Compat optionnelle : alias "condition" -> skipper PRE.
     * Si tu veux conserver l'API existante.
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

    public MutableStationMetadata mutableMetadata() {
        return metadata;
    }

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
