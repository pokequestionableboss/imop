package org.iemop.instrumentation.strategy;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.iemop.instrumentation.Instrumentation;
import org.iemop.instrumentation.InstrumentationLocation;
import org.iemop.instrumentation.InstrumentationResult;
import org.iemop.instrumentation.InstrumentationTask;
import org.iemop.instrumentation.library.Jar;
import org.iemop.util.LibraryUtil;
import org.iemop.util.Util;
import org.iemop.util.IOUtil;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class NonIncrementalInstrumentation extends InstrumentationStrategy {
    public NonIncrementalInstrumentation(MavenProject project, Log log, int threads) {
        super(project, log, threads);
    }

    @Override
    public void instrument() throws MojoExecutionException {
        log.info("Instrumenting project and library");

        List<InstrumentationTask> tasks = getInstrumentationTask();
        Instrumentation instrumentation = new Instrumentation(log, classpath, aspectPath);
        List<InstrumentationResult> results = instrumentation.instrumentAll(tasks, threads);
        processInstrumentationResults(results);
    }

    @Override
    public void processInstrumentationResults(List<InstrumentationResult> results) throws MojoExecutionException {
        try {
            List<InstrumentationLocation> locationsMapping = new ArrayList<>();

            for (InstrumentationResult result : results) {
                if (!result.ok) {
                    throw new MojoExecutionException("Unable to instrument " + result.src);
                } else {
                    Path source = Paths.get(result.src); // Original (uninstrumented) location
                    Path destination = Paths.get(result.dest); // New (instrumented) location
                    String sourceFileName = source.getFileName().toString();
                    if (result.type == InstrumentationTask.TaskType.JAR) {
                        String uninstrumentedLocation = result.jar == null ? (Util.getUninstrumentedLibraryDir() + File.separator + sourceFileName) : (Util.getUninstrumentedLibraryDir() + File.separator + result.jar.groupId + File.separator + result.jar.artifactId + File.separator + sourceFileName);
                        if (result.jar != null)
                            Files.createDirectories(Paths.get(uninstrumentedLocation).getParent());

                        // Move original jar to uninstrumented library directory
                        Files.move(source, Paths.get(uninstrumentedLocation));
                        // Move instrumented jar to original jar location
                        Files.move(destination, source);

                        locationsMapping.add(new InstrumentationLocation(result.jar, source.toString(), uninstrumentedLocation));
                    } else {
                        String uninstrumentedLocation = Util.getUninstrumentedProjectDir() + File.separator + sourceFileName;
                        Files.move(source, Paths.get(uninstrumentedLocation));
                        Files.move(destination, source);

                        locationsMapping.add(new InstrumentationLocation(null, source.toString(), uninstrumentedLocation));
                    }
                }
            }

            // Copy resources from original uninstrumented target to instrumented target
            IOUtil.syncDirectoryCopyNew(Paths.get(Util.getUninstrumentedProjectDir() + File.separator + "classes"), Paths.get(classesDir));
            IOUtil.syncDirectoryCopyNew(Paths.get(Util.getUninstrumentedProjectDir() + File.separator + "test-classes"), Paths.get(testClassesDir));

            // Delete files that are not in uninstrumented target but is in instrumented target
            IOUtil.syncDirectoryDeleteOld(Paths.get(Util.getUninstrumentedProjectDir() + File.separator + "classes"), Paths.get(classesDir));
            IOUtil.syncDirectoryDeleteOld(Paths.get(Util.getUninstrumentedProjectDir() + File.separator + "test-classes"), Paths.get(testClassesDir));

            // Save mapping in case plugin failed
            Util.saveMapping(locationsMapping);
        } catch (Exception e) {
            throw new MojoExecutionException(e);
        }

    }

    @Override
    public List<InstrumentationTask> getInstrumentationTask() {
        List<Jar> jars = LibraryUtil.getAllJar(project);
        List<InstrumentationTask> tasks = new ArrayList<>();

        tasks.add(new InstrumentationTask(InstrumentationTask.TaskType.PROJECT, classesDir));
        tasks.add(new InstrumentationTask(InstrumentationTask.TaskType.PROJECT, testClassesDir));

        for (Jar jar : jars) {
            tasks.add(new InstrumentationTask(InstrumentationTask.TaskType.JAR, jar.jarPath));
        }

        return tasks;
    }
}
