package io.github.gear4jtest.core.sidecompute;

import io.github.gear4jtest.core.model.refactor.ExecutionContext;

/**
 * Accesseur par défaut, basé sur le SideComputeContext directement attaché
 * au ExecutionContext.
 */
public final class DefaultSideComputeAccessor implements SideComputeAccessor {

    private final ExecutionContext ctx;

    public DefaultSideComputeAccessor(ExecutionContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        String k = SideComputeKeys.valueKey(key);
        Object value = ctx.getContext().get(k);
        if (value == null) {
            throw new IllegalStateException(
                    "No resolved side compute value for key '" + key + "'. " +
                            "Did you forget to add a SideComputeWaitProcessor before this operation ?");
        }
        return type.cast(value);
    }

    @Override
    public boolean isPresent(String key) {
        return ctx.getContext().containsKey(SideComputeKeys.valueKey(key));
    }
}
