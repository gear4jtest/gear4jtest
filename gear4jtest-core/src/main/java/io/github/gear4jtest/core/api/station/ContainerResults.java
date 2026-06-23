package io.github.gear4jtest.core.api.station;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Named view of completed container branch outputs.
 *
 * <p>
 * Results preserve branch declaration order for compatibility, but new
 * multi-branch aggregators should prefer {@link #get(ContainerBranch)}. The
 * branch declaration itself carries the output type, so callers do not need to
 * repeat an output {@code Class<T>} or depend on positional {@code Object...}
 * indexes.
 */
public final class ContainerResults {
    private final Map<String, Object> byBranchId;
    private final List<Object> orderedOutputs;

    private ContainerResults(Map<String, Object> byBranchId, List<Object> orderedOutputs) {
        this.byBranchId = Collections.unmodifiableMap(new LinkedHashMap<>(byBranchId));
        this.orderedOutputs = Collections.unmodifiableList(new ArrayList<>(orderedOutputs));
    }

    public static ContainerResults of(Map<String, Object> byBranchId, List<Object> orderedOutputs) {
        Objects.requireNonNull(byBranchId, "byBranchId must not be null");
        Objects.requireNonNull(orderedOutputs, "orderedOutputs must not be null");
        return new ContainerResults(byBranchId, orderedOutputs);
    }

    public Object get(String branchId) {
        requireKnownBranch(branchId);
        return byBranchId.get(branchId);
    }

    public <T> T get(String branchId, Class<T> type) {
        Objects.requireNonNull(type, "type must not be null");
        return cast(branchId, byBranchId.get(requireKnownBranch(branchId)), type);
    }

    public <T> T get(ContainerBranch<?, T> branch) {
        Objects.requireNonNull(branch, "branch must not be null");
        @SuppressWarnings("unchecked")
        T value = (T) byBranchId.get(requireKnownBranch(branch.id()));
        return value;
    }

    public <T> Optional<T> find(ContainerBranch<?, T> branch) {
        Objects.requireNonNull(branch, "branch must not be null");
        if (!byBranchId.containsKey(branch.id())) {
            return Optional.empty();
        }
        return Optional.ofNullable(get(branch));
    }

    public Map<String, Object> asMap() {
        return byBranchId;
    }

    /**
     * Returns branch outputs in branch declaration order.
     *
     * <p>
     * The returned list is generic to support generated/container DSL code whose
     * container output type is known from the surrounding builder method. Prefer
     * {@link #get(ContainerBranch)} in handwritten code because it keeps each
     * branch access tied to its typed branch handle instead of relying on order.
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> orderedOutputs() {
        return (List<T>) orderedOutputs;
    }

    private String requireKnownBranch(String branchId) {
        if (branchId == null || branchId.isBlank()) {
            throw new IllegalArgumentException("branch id is required");
        }
        if (!byBranchId.containsKey(branchId)) {
            throw new IllegalArgumentException("Unknown container branch result '" + branchId + "'");
        }
        return branchId;
    }

    private static <T> T cast(String branchId, Object value, Class<T> type) {
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            throw new ClassCastException("Container branch '" + branchId + "' produced "
                    + value.getClass().getName() + " but " + type.getName() + " was requested");
        }
        return type.cast(value);
    }
}
