package io.github.gear4jtest.core.api.context;

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
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import io.github.gear4jtest.core.exception.PayloadCloneException;

/**
 * Default strict {@link PayloadCloner} implementation.
 *
 * <p>
 * This cloner only accepts payloads that are known to be immutable. Any other
 * payload type is rejected explicitly so that the engine never gives a false
 * impression of branch isolation.
 * </p>
 */
public final class ImmutableAwarePayloadCloner implements PayloadCloner {

    private static final Set<Class<?>> IMMUTABLE_TYPES = Set
            .of(String.class, Boolean.class, Byte.class, Short.class, Integer.class, Long.class, Float.class,
                Double.class, Character.class, BigDecimal.class, BigInteger.class, UUID.class, URI.class, URL.class,
                Pattern.class, Instant.class, Duration.class, Period.class, LocalDate.class, LocalTime.class,
                LocalDateTime.class, OffsetTime.class, OffsetDateTime.class, ZonedDateTime.class, Year.class,
                YearMonth.class, MonthDay.class, ZoneId.class, ZoneOffset.class, DayOfWeek.class, Month.class,
                HijrahDate.class, JapaneseDate.class, MinguoDate.class, ThaiBuddhistDate.class);

    @Override
    public <T> T clonePayload(T payload) {
        if (payload == null) {
            return null;
        }

        if (isKnownImmutable(payload.getClass())) {
            return payload;
        }

        throw new PayloadCloneException("No PayloadCloner is able to isolate payload of type "
                + payload.getClass().getName() + ". Configure a dedicated PayloadCloner on PipelineEngine.Builder "
                + "or use immutable payloads.");
    }

    private boolean isKnownImmutable(Class<?> type) {
        return type.isEnum() || ZoneId.class.isAssignableFrom(type) || IMMUTABLE_TYPES.contains(type);
    }
}
