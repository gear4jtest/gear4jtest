package io.github.gear4jtest.core.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an API or SPI that may evolve before Gear4J reaches a stable release.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ ElementType.TYPE, ElementType.PACKAGE, ElementType.METHOD, ElementType.CONSTRUCTOR })
public @interface Experimental {
}
