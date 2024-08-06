package org.iemop.instrumentation.project;

import org.iemop.instrumentation.InstrumentationResult;
import org.iemop.instrumentation.InstrumentationTask;
import org.iemop.util.TimerUtil;

import java.io.File;
import java.util.concurrent.Callable;

public class InstrumentProjectWorker implements Callable<InstrumentationResult> {

    private final String src;
    private final String dest;
    private final String classpath;
    private final String aspectPath;

    public InstrumentProjectWorker(String src, String dest, String classpath, String aspectPath) {
        this.src = src;
        this.dest = dest == null ? src + "-instrumented" : dest;
        this.classpath = classpath;
        this.aspectPath = aspectPath;
    }

    @Override
    public InstrumentationResult call() throws InterruptedException {
        long start = System.currentTimeMillis();
        InstrumentProject project = new InstrumentProject(src, classpath);
        boolean result = project.instrument(aspectPath, dest);
        boolean partial = src.contains(File.separator + "pending" + File.separator);
        // set fromPrevious to partial because:
        // we will only instrument project if we want to instrument new classes or all classes
        // so if it is not partial, then it must be all classes, so not from previous
        // if it is partial, then from previous must be true
        TimerUtil.log("Time to instrument project " + src + " - " + (System.currentTimeMillis() - start) + " ms");
        return new InstrumentationResult(null, src, dest, result, partial, partial);
    }
}
