package org.iemop.instrumentation.strategy;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.iemop.instrumentation.InstrumentationResult;
import org.iemop.instrumentation.InstrumentationTask;
import org.iemop.util.Util;

import java.io.File;
import java.io.IOException;
import java.util.List;

public abstract class InstrumentationStrategy {
    MavenProject project;
    Log log;
    int threads;

    String classesDir;
    String testClassesDir;
    String classpath;
    String aspectPath;

    public InstrumentationStrategy(MavenProject project, Log log, int threads) {
        this.project = project;
        this.log = log;
        this.threads = threads;

        this.classesDir = project.getBuild().getOutputDirectory();
        this.testClassesDir = project.getBuild().getTestOutputDirectory();
        this.aspectPath = Util.getAspectPath();

        this.classpath = Util.getClasspath();

        log.info("Classpath: " + classpath);
        log.info("Classes: " + classesDir);
        log.info("Test Classes: " + testClassesDir);
    }

    public abstract void instrument() throws MojoExecutionException;
    abstract void processInstrumentationResults(List<InstrumentationResult> results) throws MojoExecutionException;
    abstract List<InstrumentationTask> getInstrumentationTask() throws Exception;
}
