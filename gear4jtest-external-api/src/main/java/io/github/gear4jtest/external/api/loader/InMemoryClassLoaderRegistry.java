package io.github.gear4jtest.external.api.loader;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryClassLoaderRegistry implements ClassLoaderRegistry {
    private final Map<String, Holder> byId = new ConcurrentHashMap<>();
    private final Map<String, String> aliasToId = new ConcurrentHashMap<>();

    @Override
    public ClassLoader get(String id) {
        var h = byId.get(id);
        return h == null ? null : h.loader;
    }

    @Override
    public void register(String id, ClassLoader loader, GeneratedAssemblyLine bound) {
        byId.put(id, new Holder(loader, bound));
    }

    @Override
    public void evict(String id) {
        byId.remove(id);
        aliasToId.values().removeIf(v -> v.equals(id));
    }

    @Override
    public void setAlias(String alias, String id) {
        if (id == null) {
            aliasToId.remove(alias);
        } else {
            aliasToId.put(alias, id);
        }
    }

    @Override
    public void clearAlias(String alias) {
        aliasToId.remove(alias);
    }

    @Override
    public String resolveAlias(String alias) {
        return aliasToId.get(alias);
    }

    @Override
    public GeneratedAssemblyLine getBoundAssemblyLine(String id) {
        var h = byId.get(id);
        return h == null ? null : h.chain;
    }

    private static final class Holder {
        final ClassLoader loader;
        final GeneratedAssemblyLine chain;

        Holder(ClassLoader l, GeneratedAssemblyLine c) {
            this.loader = l;
            this.chain = c;
        }
    }
}
