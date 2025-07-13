package io.test.gear4test.xml.visitor;

import java.lang.reflect.Type;

public class VisitorContext {
    private Type lastOut;

    public VisitorContext() {
        this.lastOut = null;
    }

    public Type getLastOut() {
        return lastOut;
    }

    public void setLastOut(Type lastOut) {
        this.lastOut = lastOut;
    }
}
