package org.iemop.instrumentation;

import org.iemop.instrumentation.library.Jar;
import org.iemop.util.smethods.DependencyResults;

import java.util.HashSet;
import java.util.Set;

public class InstrumentationTask {
    public enum TaskType {
        JAR,
        PROJECT
    }

    private final TaskType type;
    private Jar jar = null;
    private final String src;
    private String dest = null;
    private DependencyResults dependency = null;
    private boolean forceInstrumentJar = false;

    public InstrumentationTask(TaskType type, String src) {
        this.type = type;
        this.src = src;
    }

    public InstrumentationTask(Jar jar, String src) {
        this.type = TaskType.JAR;
        this.jar = jar;
        this.src = src;
    }

    public InstrumentationTask(TaskType type, String src, String dest) {
        this(type, src);
        this.dest = dest;
    }

    public InstrumentationTask(Jar jar, String src, DependencyResults dependency, boolean forceInstrumentJar) {
        this(jar, src);
        this.dependency = dependency;
        this.forceInstrumentJar = forceInstrumentJar;
    }

    public boolean isJar() {
        return this.type == TaskType.JAR;
    }

    public String getSource() {
        return this.src;
    }

    public String getDest() {
        return this.dest;
    }

    public DependencyResults getDependency() {
        return this.dependency;
    }

    public boolean isForceInstrument() {
        return forceInstrumentJar;
    }

    public Jar getJar() {
        return this.jar;
    }
}
