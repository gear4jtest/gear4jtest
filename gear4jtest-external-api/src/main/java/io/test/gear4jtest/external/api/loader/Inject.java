package io.test.gear4jtest.external.api.loader;

@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@java.lang.annotation.Target(java.lang.annotation.ElementType.FIELD)
public @interface Inject {
    String value() default "";
    boolean required() default true;
}