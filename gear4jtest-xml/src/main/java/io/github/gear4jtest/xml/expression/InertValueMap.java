package io.github.gear4jtest.xml.expression;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class InertValueMap extends AbstractMap<String, Object> {
    private final Map<String, Object> values;

    InertValueMap(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    @Override
    public Object get(Object key) {
        return values.get(key);
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return values.entrySet();
    }
}
