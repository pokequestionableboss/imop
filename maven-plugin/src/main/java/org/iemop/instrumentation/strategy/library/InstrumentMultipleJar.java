package org.iemop.instrumentation.strategy.library;

import org.iemop.instrumentation.InstrumentationResult;
import org.iemop.instrumentation.library.InstrumentJarPartWorker;
import org.iemop.instrumentation.library.Jar;
import org.iemop.util.Config;
import org.iemop.util.IOUtil;
import org.iemop.util.Util;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class InstrumentMultipleJar extends InstrumentLibraryStrategy {
    public InstrumentMultipleJar(ExecutorService pool, Jar jar, String jarPath, String outputPath, String classpath, String aspectPath) {
        super(pool, jar, jarPath, outputPath, classpath, aspectPath);
    }

    /**
     * This method is used to instrument a set of input files.
     * It takes as input a set of files, a root directory, and an output directory.
     * The method groups the input files by packages or by files, depending on the configuration.
     * Each group of files is then instrumented in a separate thread.
     * The method waits for all threads to complete and checks the results.
     * If any thread fails, the method returns false. Otherwise, it returns true.
     *
     * @param inputFiles The set of files to be instrumented.
     * @param tmpDirForCurrent The root directory where the uninstrumented files (inputFiles) are located.
     * @param tmpOutDir The output directory where the instrumented files should be written.
     * @return true if the instrumentation is successful for all files, false otherwise.
     */
    public boolean instrument(Set<Path> inputFiles, Path tmpDirForCurrent, String tmpOutDir) {
        // tmpDirForCurrent is the root of inputFiles
        String tmpInDir = Util.getWorkspaceDir() + File.separator + jar.groupId + "-" + jar.artifactId + "-" + jar.version + ".groups.in";

        List<Future<InstrumentationResult>> results;
        if (!Config.threadsUseFiles) {
            results = groupByPackages(inputFiles, tmpDirForCurrent, tmpInDir, tmpOutDir);
        } else {
            results = groupByFiles(inputFiles, tmpDirForCurrent, tmpInDir, tmpOutDir);
        }

        try {
            for (Future<InstrumentationResult> result : results) {
                InstrumentationResult res = result.get();
                if (!res.ok) {
                    System.out.println("Unable to instrument " + this.jarPath + " using multiple threads because package " + res.src + " failed");
                    return false;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * This method is used to instrument a directory.
     * It first walks through the directory and adds all files to a set.
     * Then, it calls the instrument method with the set of files, the directory, and an output directory.
     *
     * @param path The directory to be instrumented.
     * @return true if the instrumentation is successful for all files in the directory, false otherwise.
     */
    public boolean instrument(Path path) {
        Set<Path> inputFiles = new HashSet<>();
        String tmpOutDir = Util.getWorkspaceDir() + File.separator + jar.groupId + "-" + jar.artifactId + "-" + jar.version + ".out";

        try {
           Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
               @Override
               public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                   inputFiles.add(file);
                   return FileVisitResult.CONTINUE;
               }
           });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return instrument(inputFiles, path, tmpOutDir);
    }

    /**
     * This method is used to instrument a jar file.
     * It first extracts the jar file to a temporary directory.
     * Then, it calls the instrument method with the set of files, the temporary directory, and an output directory.
     * Finally, it copies the original jar file to the output directory and adds the instrumented files to the jar.
     *
     * @return true if the instrumentation is successful for all files in the jar, false otherwise.
     */
    public boolean instrument() {
        long start = System.currentTimeMillis();

        Path tmpDirForCurrent = Paths.get(Util.getJarDir() + File.separator + jar.groupId + "-" + jar.artifactId + "-" + jar.version); // new uninstrumented jar dir
        String tmpOutDir = Util.getWorkspaceDir() + File.separator + jar.groupId + "-" + jar.artifactId + "-" + jar.version + ".out";
        Set<Path> extractedFiles = IOUtil.extractJar(Paths.get(jarPath), tmpDirForCurrent);

        boolean success = instrument(extractedFiles, tmpDirForCurrent, tmpOutDir);
        if (success) {
            try {
                Files.copy(Paths.get(jarPath), Paths.get(outputPath));
                IOUtil.copyFilesToJar(Paths.get(tmpOutDir), Paths.get(outputPath));
            } catch (Exception e) {
                e.printStackTrace();
                success = false;
            }
        }

        System.out.println("Time to instrument jar " + jarPath + " - " + (System.currentTimeMillis() - start) + " ms");
        return success;
    }

    /**
     * This method groups the input files by packages.
     * It creates a map from package to set of files.
     * Then, it creates a thread for each package and calls the InstrumentJarPartWorker with the package and set of files.
     * The method returns a list of futures, one for each thread.
     *
     * @param inputFiles The set of files to be instrumented.
     * @param tmpDirForCurrent The root directory where the uninstrumented files (inputFiles) are located.
     * @param tmpInDir The temporary input directory where the files should be copied.
     * @param tmpOutDir The temporary output directory where the instrumented files should be written.
     * @return A list of futures, one for each thread.
     */
    public List<Future<InstrumentationResult>> groupByPackages(Set<Path> inputFiles, Path tmpDirForCurrent, String tmpInDir, String tmpOutDir) {
        // Group threads using package
        List<Future<InstrumentationResult>> results = new ArrayList<>();
        Map<Path, Set<Path>> packageToFile = new HashMap<>(); // map package to [file]
        for (Path file : inputFiles) {
            if (file.toString().endsWith(".class")) {
                Path packagePath = tmpDirForCurrent.relativize(file.getParent());
                packageToFile.computeIfAbsent(packagePath, k -> new HashSet<>()).add(file);
            }
        }

        System.out.println("Instrument " + jarPath + " by packages using " + packageToFile.size() + " groups");

        for (Map.Entry<Path, Set<Path>> entry : packageToFile.entrySet()) {
            Path packagePath = entry.getKey();
            Set<Path> files = entry.getValue();

            results.add(pool.submit(new InstrumentJarPartWorker(this.jarPath, tmpInDir, tmpOutDir, packagePath.toString(), files, tmpDirForCurrent, classpath, aspectPath)));
        }

        return results;
    }

    /**
     * This method groups the input files by files.
     * It creates a list of sets of files, each set containing X files.
     * Then, it creates a thread for each set of files and calls the InstrumentJarPartWorker with the set of files.
     * The method returns a list of futures, one for each thread.
     *
     * @param inputFiles The set of files to be instrumented.
     * @param tmpDirForCurrent The root directory where the uninstrumented files (inputFiles) are located.
     * @param tmpInDir The temporary input directory where the files should be copied.
     * @param tmpOutDir The temporary output directory where the instrumented files should be written.
     * @return A list of futures, one for each thread.
     */
    public List<Future<InstrumentationResult>> groupByFiles(Set<Path> inputFiles, Path tmpDirForCurrent, String tmpInDir, String tmpOutDir) {
        // Group threads using files
        List<Future<InstrumentationResult>> results = new ArrayList<>();

        int groupNumber = 1;
        Set<Path> groupFiles = new HashSet<>();
        for (Path file : inputFiles) {
            if (file.toString().endsWith(".class")) {
                groupFiles.add(file);
            }

            // X files per group
            if (groupFiles.size() == 75) {
                results.add(pool.submit(new InstrumentJarPartWorker(this.jarPath, tmpInDir, tmpOutDir, "group-" + groupNumber, groupFiles, tmpDirForCurrent, classpath, aspectPath)));
                groupFiles = new HashSet<>();
            }
        }

        if (!groupFiles.isEmpty()) {
            results.add(pool.submit(new InstrumentJarPartWorker(this.jarPath, tmpInDir, tmpOutDir, "group-" + groupNumber, groupFiles, tmpDirForCurrent, classpath, aspectPath)));
        }

        System.out.println(this.jarPath + " total " + results.size() + " groups");
        return results;
    }

    @Override
    public String toString() {
        return "instrument jar with multiple threads";
    }
}
