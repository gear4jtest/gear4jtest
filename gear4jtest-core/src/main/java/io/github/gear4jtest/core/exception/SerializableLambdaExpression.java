package io.github.gear4jtest.core.exception;

import io.github.gear4jtest.core.model.refactor.Whatever;

import java.io.Serializable;

public class SerializableLambdaExpression {
    public static Runnable getLambdaExpressionObject(Object object) {
        return (Runnable & Serializable) () -> System.out.println("please mate serialize this message with string  " + object);
    }

    public static class WhateverProperties {
        private String whateverString;

        public WhateverProperties(String whateverString) {
            this.whateverString = whateverString;
        }

        public boolean isEmpty() {
            return this.whateverString != null && !this.whateverString.isEmpty();
        }

        public String toString() {
            return this.whateverString;
        }
    }
}