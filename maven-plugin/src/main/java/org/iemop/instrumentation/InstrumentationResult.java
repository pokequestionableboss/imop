package org.iemop.instrumentation;

import org.iemop.instrumentation.library.Jar;

import java.io.Serializable;

public class InstrumentationResult implements Serializable {
    public InstrumentationTask.TaskType type;
    public Jar jar = null;
    public String src;
    public String dest;
    public boolean ok;
    public boolean fromPrevious = false; // true if result is not new, i.e., previously instrumented
    public boolean partially = false; // if type is project, partially is true if previous instrumented some files, fromPrevious must be true if partially is true

    public InstrumentationResult(Jar jar, String src, String dest, boolean ok) {
        this.type = jar == null ? InstrumentationTask.TaskType.PROJECT : InstrumentationTask.TaskType.JAR;
        this.jar = jar;
        this.src = src;
        this.dest = dest;
        this.ok = ok;
    }

    public InstrumentationResult(Jar jar, String src, String dest, boolean ok, boolean fromPrevious) {
        this(jar, src, dest, ok);
        this.fromPrevious = fromPrevious;
    }

    public InstrumentationResult(Jar jar, String src, String dest, boolean ok, boolean fromPrevious, boolean partially) {
        this(jar, src, dest, ok);
        if (partially && type == InstrumentationTask.TaskType.PROJECT) {
            this.fromPrevious = true;
            this.partially = true;
        } else {
            this.fromPrevious = fromPrevious;
        }
    }

    @Override
    public String toString() {
        if (type == InstrumentationTask.TaskType.JAR) {
            return "Input: " + src + ", destination: " + dest + ", ok: " + ok + ", and previously instrumented: " + fromPrevious;
        } else {
            return "Input: " + src + ", destination: " + dest + ", ok: " + ok + ", previously instrumented: " + fromPrevious + ", and partially instrumented: " + partially;
        }
    }
}
