package org.iemop.instrumentation.strategy.library;

import org.iemop.instrumentation.library.Jar;
import org.iemop.util.instrumentation.AspectJUtil;
import org.iemop.util.Config;
import org.iemop.util.IOUtil;
import org.iemop.util.Util;
import org.iemop.util.hasher.HashTable;
import org.iemop.util.instrumentation.InstrumentationTool;
import org.iemop.util.instrumentation.InstrumentationToolResult;
import org.iemop.util.smethods.DependencyResults;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

public class InstrumentCIAJar extends InstrumentLibraryStrategy {

    private final DependencyResults dependency;
    private final boolean reinstrumentJar;
    public InstrumentCIAJar(ExecutorService pool, Jar jar, String jarPath, String outputPath, String classpath, String aspectPath, DependencyResults dependency, boolean reinstrumentJar) {
        super(pool, jar, jarPath, outputPath, classpath, aspectPath);
        this.dependency = dependency;
        this.reinstrumentJar = reinstrumentJar;
    }

    public boolean instrumentFirstTime() throws Exception {
        // Instrument the entire jar
        // similar to InstrumentMultipleJar, but not using new threads
        long start = System.currentTimeMillis();

        Path tmpDirForCurrent = Paths.get(Util.getWorkspaceDir() + File.separator + jar.groupId + "-" + jar.artifactId + "-" + jar.version); // new uninstrumented jar dir
        Path tmpOutDir = Paths.get(Util.getJarDir() + File.separator + jar.groupId + "-" + jar.artifactId + "-" + jar.version + ".instrumented");
        Set<Path> filesToInstrument = IOUtil.extractJar(Paths.get(jarPath), tmpDirForCurrent);

        if (Config.jarThreads) {
            // Use instrument multiple jar strategy to group files
            System.out.println("CIA instruments " + jar + " first time, using multiple threads");
            InstrumentMultipleJar strategy = new InstrumentMultipleJar(pool, jar, jarPath, outputPath, classpath, aspectPath);
            if (!strategy.instrument(filesToInstrument, tmpDirForCurrent, tmpOutDir.toString())) {
                System.out.println("Unable to instrument impact files in " + jar + " using multiple threads - " + (System.currentTimeMillis() - start) + " ms");
                return false;
            }
        } else {
            // TODO: switch InstrumentJarPartWorker and InstrumentChangedJar to this implementation
            InstrumentationToolResult result = InstrumentationTool.getInstance().instrumentExtractedJar(
                    jar,
                    jarPath + File.pathSeparator + this.classpath,
                    aspectPath,
                    tmpDirForCurrent.toString(),
                    tmpOutDir.toString()
            );

            if (!result.result) {
                System.out.println("Unable to instrument jar " + jar + " - " + (System.currentTimeMillis() - start) + " ms");
                return false;
            }
        }

        // Copy instrumented code back to jar
        HashTable.getInstance().computeHashes(jar, tmpDirForCurrent, tmpOutDir);
        Files.copy(Paths.get(jarPath), Paths.get(outputPath));
        IOUtil.copyFilesToJar(tmpOutDir, Paths.get(outputPath));

        Files.createFile(Paths.get(jarPath + "-instrumented"));
        System.out.println("Instrumented jar " + jar + " (all files) - " + (System.currentTimeMillis() - start) + " ms");
        return true;
    }

    public boolean instrumentJar() throws Exception {
        // If reinstrumentJar is false,
        // CASE C: jar changed, so we have to check all depends on, if the checksum changed, instrument
        // otherwise
        // CASE B: jar didn't change, so we only need to check newly depends on, if the checksum changed, instrument
        long start = System.currentTimeMillis();

        Set<String> allPendingCheck = reinstrumentJar ? dependency.getNewlyUsedFiles(jar) : dependency.getUsedFiles(jar);
        Path tmpDirForCurrent = Paths.get(Util.getWorkspaceDir() + File.separator + jar.groupId + "-" + jar.artifactId + "-" + jar.version); // new uninstrumented jar dir
        Path tmpInDir = Paths.get(Util.getWorkspaceDir() + File.separator + jar.groupId + "-" + jar.artifactId + "-" + jar.version + ".in-" + UUID.randomUUID());
        Path tmpOutDir = Paths.get(Util.getJarDir() + File.separator + jar.groupId + "-" + jar.artifactId + "-" + jar.version + ".instrumented");

        if (!reinstrumentJar) {
            System.out.println("Case C: jar " + jar + " changed, check all depends on, " + (System.currentTimeMillis() - start) + " ms so far");
            IOUtil.extractJar(Paths.get(jarPath), tmpDirForCurrent); // new jar, need to extract it
        } else {
            System.out.println("Case B: jar " + jar + " didn't change, check newly depends on" + (System.currentTimeMillis() - start) + " ms so far");
        }

        // Verify pendingCheck is in tmpDirForCurrent, handle files like A$MockitoMock$741033178$auxiliary$cTX5vz0u.class that are not in jar
        long startA = System.currentTimeMillis();
        Set<String> pendingCheck = new HashSet<>();
        for (String check : allPendingCheck) {
            if (Files.exists(tmpDirForCurrent.resolve(check + ".class"))) {
                pendingCheck.add(check);
            }
        }

        Map<String, String> fileStatus = HashTable.getInstance().computeImpacted(jar, tmpDirForCurrent, pendingCheck);
        Set<String> pendingInstrument = new HashSet<>();
        for (Map.Entry<String, String> entry : fileStatus.entrySet()) {
            String file = entry.getKey();
            String instrumentedDest = entry.getValue();

            if (instrumentedDest == null) {
                // file is not instrumented, copy to tmpInDir for instrumentation
                pendingInstrument.add(file);
            }
        }
        System.out.println(jar + " Get instrumentation time status, " + (System.currentTimeMillis() - startA) + " ms so far");

        if (!pendingInstrument.isEmpty()) {
            long startB = System.currentTimeMillis();
            Set<Path> filesToInstrument = IOUtil.copyFilesToDestString(pendingInstrument, tmpDirForCurrent, tmpInDir);

            if (filesToInstrument == null || filesToInstrument.isEmpty()) {
                System.out.println("Unable to instrument impact files in " + jar + ", no files to instrument - " + (System.currentTimeMillis() - start) + " ms");
                return false;
            }

            if (Config.jarThreads) {
                // Use instrument multiple jar strategy to group files
                InstrumentMultipleJar strategy = new InstrumentMultipleJar(pool, jar, jarPath, outputPath, classpath, aspectPath);
                if (!strategy.instrument(filesToInstrument, tmpInDir, tmpOutDir.toString())) {
                    System.out.println("Unable to instrument impact files in " + jar + " using multiple threads - " + (System.currentTimeMillis() - start) + " ms");
                    return false;
                }

                System.out.println(jar + " instrumented, " + (System.currentTimeMillis() - startB) + " ms so far");
            } else {
                InstrumentationToolResult result = InstrumentationTool.getInstance().instrumentExtractedJar(
                        jar,
                        jarPath + File.pathSeparator + this.classpath,
                        aspectPath,
                        tmpInDir.toString(),
                        tmpOutDir.toString()
                );
                System.out.println(jar + " instrumented, " + (System.currentTimeMillis() - startB) + " ms so far");

                if (!result.result) {
                    System.out.println("Unable to instrument impact files in " + jar + " - " + (System.currentTimeMillis() - start) + " ms");
                    return false;
                }
            }
        }

        long startC = System.currentTimeMillis();
        if (reinstrumentJar) {
            // copy previously instrumented version
            Path instrumentedJarPath = Paths.get(Util.getInstrumentedLibraryDir() + File.separator + jar.groupId + File.separator + jar.artifactId + File.separator + Paths.get(jarPath).getFileName());
            Files.copy(instrumentedJarPath, Paths.get(outputPath));
        } else {
            Files.copy(Paths.get(jarPath), Paths.get(outputPath));
        }

        IOUtil.copyFilesToJar(pendingInstrument, tmpOutDir, Paths.get(outputPath));
        IOUtil.copyFilesToJar(fileStatus, Paths.get(outputPath));
        HashTable.getInstance().computeHashes(jar, tmpDirForCurrent, tmpOutDir, pendingInstrument);
        System.out.println(jar + " hashing, " + (System.currentTimeMillis() - startC) + " ms so far");

        System.out.println(jar + (reinstrumentJar ? " re" : " ") + "instrumented " + pendingInstrument.size() + " impacted files, total " + fileStatus.size() + " impacted files, and " + (allPendingCheck.size() - pendingCheck.size()) + " files are not in jar");
        return true;
    }

    @Override
    public boolean instrument() {
        try {
            if (Util.isFirstRun())
                return instrumentFirstTime();
            return instrumentJar();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    @Override
    public String toString() {
        return "instrument impacted classes";
    }
}
