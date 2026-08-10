package io.github.gear4jtest.spring.boot;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.beans.factory.annotation.Qualifier;

/**
 * Qualifies the {@link javax.sql.DataSource} reserved for Gear4J persistence.
 * <p>
 * This qualifier takes precedence over Spring's default single-candidate or
 * {@code @Primary} datasource selection.
 */
@Target({ ElementType.ANNOTATION_TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER,
        ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Qualifier public @interface Gear4jDataSource {
}
