package org.iemop.instrumentation.strategy.library;

import org.iemop.instrumentation.library.Jar;

import java.util.concurrent.ExecutorService;

public abstract class InstrumentLibraryStrategy {
    protected final ExecutorService pool;
    protected final Jar jar;
    protected final String jarPath;
    protected final String outputPath;
    protected final String classpath;
    protected final String aspectPath;

    public InstrumentLibraryStrategy(ExecutorService pool, Jar jar, String jarPath, String outputPath, String classpath, String aspectPath) {
        this.pool = pool;
        this.jar = jar;
        this.jarPath = jarPath;
        this.outputPath = outputPath;
        this.classpath = classpath;
        this.aspectPath = aspectPath;
    }

    public abstract boolean instrument();
    public abstract String toString();
}
