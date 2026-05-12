package io.github.gear4jtest.jackson;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.chrono.HijrahDate;
import java.time.chrono.JapaneseDate;
import java.time.chrono.MinguoDate;
import java.time.chrono.ThaiBuddhistDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Currency;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gear4jtest.core.api.context.PayloadCloner;
import io.github.gear4jtest.core.exception.PayloadCloneException;

public final class JacksonPayloadCloner implements PayloadCloner {

    private static final Set<Class<?>> KNOWN_IMMUTABLE_TYPES = Set
            .of(String.class, Boolean.class, Byte.class, Short.class, Integer.class, Long.class, Float.class,
                Double.class, Character.class, BigDecimal.class, BigInteger.class, UUID.class, URI.class, URL.class,
                Pattern.class, Currency.class, OptionalInt.class, OptionalLong.class, OptionalDouble.class,
                Instant.class, Duration.class, Period.class, LocalDate.class, LocalTime.class, LocalDateTime.class,
                OffsetTime.class, OffsetDateTime.class, ZonedDateTime.class, Year.class, YearMonth.class,
                MonthDay.class, ZoneId.class, ZoneOffset.class, DayOfWeek.class, Month.class, HijrahDate.class,
                JapaneseDate.class, MinguoDate.class, ThaiBuddhistDate.class);

    private final ObjectMapper objectMapper;

    public JacksonPayloadCloner(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T clonePayload(T payload) {
        if (payload == null) {
            return null;
        }

        try {
            return (T) cloneValue(payload, new IdentityHashMap<>());
        } catch (PayloadCloneException e) {
            throw e;
        } catch (Exception e) {
            throw new PayloadCloneException("Jackson failed to clone payload of type " + payload.getClass().getName(),
                    e);
        }
    }

    private Object cloneValue(Object value, IdentityHashMap<Object, Object> visited) {
        if (value == null) {
            return null;
        }

        Class<?> type = value.getClass();

        if (isKnownImmutable(type)) {
            return value;
        }

        Object existing = visited.get(value);
        if (existing != null) {
            return existing;
        }

        if (value instanceof Optional<?> optional) {
            return cloneOptional(optional, visited);
        }

        if (type.isArray()) {
            return cloneArray(value, visited);
        }

        if (value instanceof List<?> list) {
            return cloneList(list, visited);
        }

        if (value instanceof Set<?> set) {
            return cloneSet(set, visited);
        }

        if (value instanceof Map<?, ?> map) {
            return cloneMap(map, visited);
        }

        if (value instanceof Collection<?> collection) {
            return cloneCollection(collection, visited);
        }

        return clonePojo(value);
    }

    private Optional<?> cloneOptional(Optional<?> source, IdentityHashMap<Object, Object> visited) {
        if (source.isEmpty()) {
            Optional<?> empty = Optional.empty();
            visited.put(source, empty);
            return empty;
        }

        Object clonedContent = cloneValue(source.get(), visited);
        Optional<?> cloned = Optional.ofNullable(clonedContent);
        visited.put(source, cloned);
        return cloned;
    }

    private Object cloneArray(Object sourceArray, IdentityHashMap<Object, Object> visited) {
        int length = Array.getLength(sourceArray);
        Class<?> componentType = sourceArray.getClass().getComponentType();
        Object clonedArray = Array.newInstance(componentType, length);
        visited.put(sourceArray, clonedArray);

        for (int i = 0; i < length; i++) {
            Object clonedElement = cloneValue(Array.get(sourceArray, i), visited);
            Array.set(clonedArray, i, clonedElement);
        }

        return clonedArray;
    }

    private List<Object> cloneList(List<?> source, IdentityHashMap<Object, Object> visited) {
        List<Object> cloned = new ArrayList<>(source.size());
        visited.put(source, cloned);

        for (Object element : source) {
            cloned.add(cloneValue(element, visited));
        }

        return cloned;
    }

    private Set<Object> cloneSet(Set<?> source, IdentityHashMap<Object, Object> visited) {
        Set<Object> cloned = new LinkedHashSet<>(Math.max(16, source.size()));
        visited.put(source, cloned);

        for (Object element : source) {
            cloned.add(cloneValue(element, visited));
        }

        return cloned;
    }

    private Collection<Object> cloneCollection(Collection<?> source, IdentityHashMap<Object, Object> visited) {

        Collection<Object> cloned = new ArrayList<>(source.size());
        visited.put(source, cloned);

        for (Object element : source) {
            cloned.add(cloneValue(element, visited));
        }

        return cloned;
    }

    private Map<Object, Object> cloneMap(Map<?, ?> source, IdentityHashMap<Object, Object> visited) {
        Map<Object, Object> cloned = new LinkedHashMap<>(Math.max(16, source.size()));
        visited.put(source, cloned);

        for (Map.Entry<?, ?> entry : source.entrySet()) {
            Object clonedKey = cloneValue(entry.getKey(), visited);
            Object clonedValue = cloneValue(entry.getValue(), visited);
            cloned.put(clonedKey, clonedValue);
        }

        return cloned;
    }

    private Object clonePojo(Object source) {
        try {
            /*
             * Important: visited-based cycle protection does not apply inside this Jackson
             * conversion step. Cyclic POJO graphs therefore require dedicated Jackson
             * configuration and are not guaranteed to be supported transparently by this
             * cloner.
             */
            JavaType javaType = objectMapper.getTypeFactory().constructType(source.getClass());
            return objectMapper.convertValue(source, javaType);
        } catch (IllegalArgumentException e) {
            throw new PayloadCloneException("Jackson failed to clone payload of type " + source.getClass().getName(),
                    e);
        }
    }

    private boolean isKnownImmutable(Class<?> type) {
        return type.isEnum() || Class.class.equals(type) || ZoneId.class.isAssignableFrom(type)
                || KNOWN_IMMUTABLE_TYPES.contains(type);
    }
}
