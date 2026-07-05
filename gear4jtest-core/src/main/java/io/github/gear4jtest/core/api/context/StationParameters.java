package io.github.gear4jtest.core.api.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Station-scoped parameter definitions prepared by station builders.
 */
public final class StationParameters {
    private final List<StationParameterModel<?, ?>> parameters;

    public StationParameters() {
        this(List.of());
    }

    private StationParameters(List<StationParameterModel<?, ?>> parameters) {
        this.parameters = new ArrayList<>(parameters);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public boolean hasParameters() {
        return !this.parameters.isEmpty();
    }

    public List<StationParameterModel<?, ?>> getParameters() {
        return Collections.unmodifiableList(parameters);
    }

    public static final class Builder {
        private final StationParameters instance = new StationParameters();

        private Builder() {
        }

        public Builder withParameter(StationParameterModel<?, ?> parameter) {
            instance.parameters.add(parameter);
            return this;
        }

        public Builder withParameters(Optional<StationParameters> parameters) {
            parameters.ifPresent(p -> instance.parameters.addAll(p.parameters));
            return this;
        }

        public StationParameters build() {
            return new StationParameters(instance.parameters);
        }
    }
}
