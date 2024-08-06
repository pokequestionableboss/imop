package org.iemop.instrumentation;

import org.iemop.instrumentation.library.Jar;

import java.io.Serializable;

public class InstrumentationLocation implements Serializable {
    private Jar jar;
    private boolean newIns = true;
    private String instrumented;
    private String uninstrumented;

    public InstrumentationLocation(Jar jar, String instrumented, String uninstrumented) {
        this.jar = jar;
        this.instrumented = instrumented;
        this.uninstrumented = uninstrumented;
    }

    public InstrumentationLocation(Jar jar, boolean isNew, String instrumented, String uninstrumented) {
        this(jar, instrumented, uninstrumented);
        this.newIns = isNew;
    }

    public boolean isJar() {
        return this.jar != null;
    }

    public boolean isNew() { return this.newIns; }

    public Jar getJar() {
        return this.jar;
    }

    public String getInstrumented() {
        return this.instrumented;
    }

    public String getUninstrumented() {
        return this.uninstrumented;
    }

    @Override
    public String toString() {
        return "isJar: " + (this.jar != null) + (this.newIns ? " (new)" : "") +", instrumented: " + this.instrumented + ", and uninstrumented: " + this.uninstrumented;
    }
}
