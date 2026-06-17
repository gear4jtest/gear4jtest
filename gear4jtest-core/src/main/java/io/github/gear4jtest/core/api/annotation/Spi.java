package io.github.gear4jtest.core.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Gear4J extension contract implemented by application or integration
 * code.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ ElementType.TYPE, ElementType.PACKAGE, ElementType.METHOD, ElementType.CONSTRUCTOR })
public @interface Spi {
}
