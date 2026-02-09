package io.github.gear4jtest.core.model;

import java.util.List;
import java.util.Objects;

/**
 * Template commun à toutes les OperationDefinition.
 * - Gère la création du record
 * - Gère les events
 * - Gère les processors
 * - Gère le wiring avec le PipelineExecutionManager
 */
public abstract class AbstractStation<I, O> implements Station<I, O> {

    protected String id;
    protected StationKind kind;
    protected List<Processor> processors;
    protected List<BaseError<I>> onErrors;
    protected List<Condition<I>> conditions;
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

    public List<Condition<I>> getConditions() {
        return conditions;
    }

    public Operator<I, O> getFallbackOperator() {
        return fallbackOperator;
    }

    public Boolean getUnary() {
        return unary;
    }
}
