package org.iemop.instrumentation.library;

import org.iemop.instrumentation.InstrumentationResult;
import org.iemop.instrumentation.library.repair.DeleteClassesFromLog;
import org.iemop.instrumentation.strategy.library.InstrumentCIAJar;
import org.iemop.instrumentation.strategy.library.InstrumentChangedJar;
import org.iemop.instrumentation.strategy.library.InstrumentLibraryStrategy;
import org.iemop.instrumentation.strategy.library.InstrumentMultipleJar;
import org.iemop.util.*;
import org.iemop.util.hasher.HasherUtil;
import org.iemop.util.instrumentation.AspectJUtil;
import org.iemop.util.instrumentation.InstrumentationToolResult;
import org.iemop.util.smethods.DependencyResults;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

public class InstrumentJarWorker implements Callable<InstrumentationResult> {

    private final ExecutorService pool;
    private final Jar jar;
    private final String jarPath;
    private final String outputPath;
    private final String classpath;
    private final String aspectPath;
    private final DependencyResults dependency;
    private final boolean forceInstrumentJar;

    public InstrumentJarWorker(ExecutorService pool, Jar jar, String jarPath, String outputPath, String classpath, String aspectPath, DependencyResults dependency, boolean forceInstrumentJar) {
        this.pool = pool;
        this.jar = jar;
        this.jarPath = jarPath;
        this.outputPath = outputPath == null ? jarPath + ".tmp" : outputPath;
        this.classpath = classpath;
        this.aspectPath = aspectPath;
        this.dependency = dependency;
        this.forceInstrumentJar = forceInstrumentJar;
    }

    @Override
    public InstrumentationResult call() throws InterruptedException {
        long start = System.currentTimeMillis();

        InstrumentLibraryStrategy strategy = null;
        if ((Config.methodCIA || Config.classCIA || Config.dynamicCIA) && this.dependency != null && this.forceInstrumentJar) {
            // THIS IS FOR OLD JAR, NO CHANGED FILES, we instrumented part of this jar before
            strategy = new InstrumentCIAJar(pool, jar, jarPath, outputPath, classpath, aspectPath, dependency, true);
        } else {
            InstrumentJar.patchKnownIssues(jarPath);

            if (Util.isFirstRun() && Config.storeHashes) {
                // is first run, we want to extract jar and compute hashes
                String tmpDirForCurrent = Util.getJarDir() + File.separator + jar.groupId + "-" + jar.artifactId + "-" + jar.version;

                Set<Path> files = IOUtil.extractJar(Paths.get(jarPath), Paths.get(tmpDirForCurrent));
                HasherUtil.saveHashes(files, tmpDirForCurrent + "-hashes.map");
            }

            if (Config.incrementalJar && !Util.isFirstRun()) { // should not use InstrumentChangedJar if it is first run
                strategy = new InstrumentChangedJar(pool, jar, jarPath, outputPath, classpath, aspectPath, dependency);
            } else if (Config.methodCIA || Config.classCIA || Config.dynamicCIA) {
                // InstrumentCIAJar will instrument ALL the newly used files, even if we already instrumented the files before
                strategy = new InstrumentCIAJar(pool, jar, jarPath, outputPath, classpath, aspectPath, dependency, false);
            } else if (Config.jarThreads) {
                strategy = new InstrumentMultipleJar(pool, jar, jarPath, outputPath, classpath, aspectPath);
            }
        }

        if (strategy != null) {
            if (strategy.instrument()) {
                TimerUtil.log("Time to instrument jar (" + strategy + ") " + jarPath + " - " + (System.currentTimeMillis() - start) + " ms");
                return new InstrumentationResult(jar, jarPath, outputPath, true);
            }
        }

        InstrumentJar instrumentedJar = new InstrumentJar(jarPath, classpath);
        InstrumentationToolResult result = instrumentedJar.instrument(aspectPath, outputPath);

        if (!result.result) {
            System.out.println("Failed to instrument " + jarPath + ", repairing");
            // Repair
            try {
                Files.copy(Paths.get(jarPath), Paths.get(jarPath + ".orig.jar"), StandardCopyOption.REPLACE_EXISTING); // add this to classpath so ajc can find it
                if (DeleteClassesFromLog.delete(result.log, jarPath, true)) {
                    System.out.println("Deleted classes from jar");
                    result = new InstrumentJar(jarPath, jarPath + ".orig.jar" + File.pathSeparator + classpath).instrument(aspectPath, outputPath);
                    System.out.println("Re-instrumentation result: " + result.result);
                    System.out.println("Re-instrument log:");
                    System.out.println(result.log);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        TimerUtil.log("Time to instrument jar (regular) " + jarPath + " - " + (System.currentTimeMillis() - start) + " ms");
        return new InstrumentationResult(jar, jarPath, outputPath, result.result);
    }
}
