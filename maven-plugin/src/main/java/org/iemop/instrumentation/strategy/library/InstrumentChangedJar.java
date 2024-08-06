package org.iemop.instrumentation.strategy.library;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.iemop.instrumentation.library.Jar;
import org.iemop.util.instrumentation.AspectJUtil;
import org.iemop.util.Config;
import org.iemop.util.IOUtil;
import org.iemop.util.Util;
import org.iemop.util.hasher.IncrementalJarCleaner;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;

import org.iemop.util.instrumentation.InstrumentationTool;
import org.iemop.util.instrumentation.InstrumentationToolResult;
import org.iemop.util.smethods.DependencyResults;

public class InstrumentChangedJar extends InstrumentLibraryStrategy {

    private DependencyResults dependency;
    public InstrumentChangedJar(ExecutorService pool, Jar jar, String jarPath, String outputPath, String classpath, String aspectPath, DependencyResults dependency) {
        super(pool, jar, jarPath, outputPath, classpath, aspectPath);
        this.dependency = dependency;
    }

    public boolean instrument() {
        long start = System.currentTimeMillis();

        List<Path> candidates = new ArrayList<>();
        if (Files.exists(Paths.get(Util.getUninstrumentedLibraryDir() + File.separator + jar.groupId + File.separator + jar.artifactId))) {
            try {
                try (Stream<Path> stream = Files.list(Paths.get(Util.getUninstrumentedLibraryDir() + File.separator + jar.groupId + File.separator + jar.artifactId))) {
                    stream
                            .forEach(path -> {
                                String jarName = path.getFileName().toString();
                                if (jarName.startsWith(jar.artifactId) && jarName.endsWith(".jar")) {
                                    candidates.add(path);
                                }
                            });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        String newVersion = jar.version;
        ComparableVersion newVersionCV = new ComparableVersion(jar.version);
        String closestVersion = null;
        ComparableVersion closestVersionCV = null;
        Path closestPath = null;

        for (Path candidate : candidates) {
            String jarName = candidate.getFileName().toString();
            String version = jarName.substring(jar.artifactId.length() + 1, jarName.length() - 4);  // convert abc-1.0.jar -> 1.0
            ComparableVersion versionCV = new ComparableVersion(version);
            if (newVersionCV.compareTo(versionCV) > 0 && (closestVersionCV == null || versionCV.compareTo(closestVersionCV) > 0)) {
                // first, current jar must be newer than candidate
                // then, candidate must be newer than previous candidate
                closestVersionCV = versionCV;
                closestVersion = version;
                closestPath = candidate;
            }
        }

        try {
            if (closestPath != null) {
                // Jar is updated
                String tmpDir = Util.getJarDir() + File.separator + jar.groupId + "-" + jar.artifactId + "-" + closestVersion; // old uninstrumented jar dir
                if (!Files.exists(Paths.get(tmpDir))) {
                    IOUtil.extractJar(closestPath, Paths.get(tmpDir));
                }

                String tmpDirForCurrent = Util.getJarDir() + File.separator + jar.groupId + "-" + jar.artifactId + "-" + newVersion; // new uninstrumented jar dir
                if (!Files.exists(Paths.get(tmpDirForCurrent))) {
                    IOUtil.extractJar(Paths.get(jarPath), Paths.get(tmpDirForCurrent));
                }

                Path instrumentedJar = Paths.get(Util.getInstrumentedLibraryDir() + File.separator + jar.groupId + File.separator + jar.artifactId + File.separator + closestPath.getFileName()); // old instrumented jar dir
                String tmpOutDir = Util.getWorkspaceDir() + File.separator + jar.groupId + "-" + jar.artifactId + "-" + newVersion + ".out";

                Path tmpInDir = Paths.get(Util.getWorkspaceDir() + File.separator + jar.groupId + "-" + jar.artifactId + "-" + newVersion + ".in");

                Set<Path> deletedFiles = new HashSet<>();
                Set<Path> newFiles = new HashSet<>();
                Set<Path> identicalFiles = new HashSet<>();
                Set<Path> updatedFiles = new HashSet<>();
                Set<Path> nonClasses = new HashSet<>();
                for (String line : Util.diff(tmpDir, tmpDirForCurrent)) {
                    if (line.startsWith("Only in " + tmpDirForCurrent)) {
                        String[] tmp = line.split(" ");
                        String filename = tmp[2].substring(0, tmp[2].length() - 1) + File.separator + tmp[3]; // Only in /path/to/new: file.class -> /path/to/new/file.class
                        Path filepath = Paths.get(filename);

                        if (filename.endsWith(".class")) {
                            newFiles.add(filepath);
                        } else if (Files.isDirectory(filepath)) {
                            IOUtil.addFilesInDirectory(filepath, newFiles, nonClasses);
                        } else {
                            nonClasses.add(filepath);
                        }
                    } else if (line.startsWith("Only in " + tmpDir)) {
                        String[] tmp = line.split(" ");
                        deletedFiles.add(Paths.get(tmp[2].substring(0, tmp[2].length() - 1) + File.separator + tmp[3]));
                    } else if (line.endsWith("identical")) {
                        String filename = line.split(" ")[3];
                        if (filename.endsWith(".class"))
                            identicalFiles.add(Paths.get(filename));
                        else
                            nonClasses.add(Paths.get(filename));
                    } else if (line.endsWith("differ")) {
                        String filename = line.split(" ")[3];
                        if (filename.endsWith(".class"))
                            updatedFiles.add(Paths.get(filename));
                        else
                            nonClasses.add(Paths.get(filename));
                    }
                }

                if (Config.cleanByteCode) {
                    Set<Path> removed = IncrementalJarCleaner.getInstance().cleanByteCode(updatedFiles, tmpDir, tmpDirForCurrent);
                    System.out.println(jar.artifactId + " after bytecode cleaning, " + removed.size() + " files are identical");
                    updatedFiles.removeAll(removed); // remove files from updatedFiles, because they are now "identical" (same bytecode)
                    identicalFiles.addAll(removed); // add files to identicalFiles
                }

                if (identicalFiles.isEmpty()) {
                    System.out.println(jar.artifactId + " [skip due to no identical] (" + closestVersion + " -> " + newVersion + ")" + " - old: " + identicalFiles.size() + ", deleted: " + deletedFiles.size() + ", updated: " + updatedFiles.size() + ", new: " + newFiles.size() + ", non-class: " + nonClasses.size() + " - " + (System.currentTimeMillis() - start) + " ms");
                    return false;
                }

                IOUtil.copyFilesToDest(newFiles, Paths.get(tmpDirForCurrent), tmpInDir);
                IOUtil.copyFilesToDest(updatedFiles, Paths.get(tmpDirForCurrent), tmpInDir);

                long instrStart = System.currentTimeMillis();
                boolean reInstrument = !newFiles.isEmpty() || !updatedFiles.isEmpty();
                if (reInstrument) {
                    if (Config.jarThreads) {
                        // Use instrument multiple jar strategy to group files
                        InstrumentMultipleJar strategy = new InstrumentMultipleJar(pool, jar, jarPath, outputPath, classpath, aspectPath);
                        if (!strategy.instrument(tmpInDir)) {
                            System.out.println(jar.artifactId + " [unable to instrument] (" + closestVersion + " -> " + newVersion + ")" + " - old: " + identicalFiles.size() + ", deleted: " + deletedFiles.size() + ", updated: " + updatedFiles.size() + ", new: " + newFiles.size() + ", non-class: " + nonClasses.size() + " - " + (System.currentTimeMillis() - start) + " ms");
                            return false;
                        }

                    } else {
                        // UPDATE NEW JAR: copy newly instr to jar, then copy identicalFiles to jar
                        InstrumentationToolResult result = InstrumentationTool.getInstance().instrumentExtractedJar(
                                jar,
                                jarPath + File.pathSeparator + this.classpath,
                                aspectPath,
                                tmpInDir.toString(),
                                tmpOutDir
                        );

                        if (!result.result) {
                            System.out.println(jar.artifactId + " [unable to instrument] (" + closestVersion + " -> " + newVersion + ")" + " - old: " + identicalFiles.size() + ", deleted: " + deletedFiles.size() + ", updated: " + updatedFiles.size() + ", new: " + newFiles.size() + ", non-class: " + nonClasses.size() + " - " + (System.currentTimeMillis() - start) + " ms");
                            return false;
                        }
                    }
                }
                long instrEnd = System.currentTimeMillis();

                Files.copy(Paths.get(jarPath), Paths.get(outputPath));

                // Copy newly instrumented files to jar
                if (reInstrument)
                    IOUtil.copyFilesToJar(Paths.get(tmpOutDir), Paths.get(outputPath));

                // Copy previously instrumented files to jar
                IOUtil.copyFilesAcrossJar(identicalFiles, Paths.get(tmpDirForCurrent), instrumentedJar, Paths.get(outputPath));

                System.out.println(jar.artifactId + " (" + closestVersion + " -> " + newVersion + ")" + " - old: " + identicalFiles.size() + ", deleted: " + deletedFiles.size() +  ", updated: " + updatedFiles.size() + ", new: " + newFiles.size() + ", non-class: " + nonClasses.size() + " - " + (System.currentTimeMillis() - start) + " ms (" + (instrEnd - instrStart) + ") ms instr.");
                return true;
            } else {
                System.out.println(jarPath + " is a completely new jar, skipping incremental instrumentation");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    @Override
    public String toString() {
        return "instrument changed jar";
    }
}
