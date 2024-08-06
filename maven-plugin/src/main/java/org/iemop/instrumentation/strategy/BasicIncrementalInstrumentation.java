package org.iemop.instrumentation.strategy;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.ekstazi.hash.Hasher;
import org.iemop.instrumentation.*;
import org.iemop.instrumentation.library.Jar;
import org.iemop.util.*;
import org.iemop.util.dclasses.DynamicDepsBuilder;
import org.iemop.util.dclasses.DynamicTestRunner;
import org.iemop.util.hasher.HashTable;
import org.iemop.util.hasher.HasherUtil;
import org.iemop.util.sclasses.ClassLevelDepsBuilder;
import org.iemop.util.smethods.DependencyResults;
import org.iemop.util.smethods.MethodLevelDepsBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class BasicIncrementalInstrumentation extends InstrumentationStrategy {
    List<InstrumentationResult> previousResults; // previously instrumented results

    public BasicIncrementalInstrumentation(MavenProject project, Log log, int threads) {
        super(project, log, threads);
        this.previousResults = new ArrayList<>();
    }

    @Override
    public void instrument() throws MojoExecutionException {
        long start = System.currentTimeMillis();
        log.info("Instrumenting project and library");

        try {
            List<InstrumentationTask> tasks = getInstrumentationTask();
            Instrumentation instrumentation = new Instrumentation(log, classpath, aspectPath);
            List<InstrumentationResult> results = instrumentation.instrumentAll(tasks, threads);
            if (results.isEmpty()) {
                Config.skipMOP = true;
                TimerUtil.log("Don't need to run MOP, detected in " + (System.currentTimeMillis() - start) + " ms");
                throw new MojoExecutionException("Nothing new to instrument or monitor, existing...");
            }

            results.addAll(this.previousResults);
            processInstrumentationResults(results);
        } catch (Exception e) {
            throw new MojoExecutionException(e);
        }

        TimerUtil.log("Time to instrument and process all jar: " + (System.currentTimeMillis() - start) + " ms");

        /*
         * BISM Experiment [INSTRUMENTATION ONLY, NO MONITORING], will restore all files
         */
        if (Config.bism || Config.noMonitoring) {
            InstrumentationRestore.restore(log, project.getBuild().getOutputDirectory(), project.getBuild().getTestOutputDirectory());
        }
    }

    @Override
    void processInstrumentationResults(List<InstrumentationResult> results) throws MojoExecutionException {
        long start = System.currentTimeMillis();

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
                        log.info("Result (" + result + ") is a jar");

                        String uninstrumentedLocation = result.jar == null ? (Util.getUninstrumentedLibraryDir() + File.separator + sourceFileName) : (Util.getUninstrumentedLibraryDir() + File.separator + result.jar.groupId + File.separator + result.jar.artifactId + File.separator + sourceFileName);
                        Path uninstrumentedPath = Paths.get(uninstrumentedLocation);

                        if (result.jar != null)
                            Files.createDirectories(uninstrumentedPath.getParent());

                        if (!result.fromPrevious) {
                            log.info("Newly instrumented jar, move " + source + " to " + uninstrumentedPath + ", then move " + destination + " to " + source);
                            // Case 1: destination is newly instrumented jar
                            // move original jar (source) to uninstrumented library directory
                            // then move instrumented jar (destination) to the expected location (source)
                            Files.move(source, uninstrumentedPath, StandardCopyOption.REPLACE_EXISTING);
                            Files.move(destination, source);
                        } else {
                            log.info("Previously instrumented jar, copy " + destination + " to " + source);

                            // Case 2: destination is previously instrumented jar
                            // simply copy instrumented jar (destination) to the expected location (source)
                            // don't need to move source because source is already in uninstrumented library directory
                            Files.copy(destination, source, StandardCopyOption.REPLACE_EXISTING);
                        }

                        locationsMapping.add(new InstrumentationLocation(result.jar, !result.fromPrevious, source.toString(), uninstrumentedLocation));
                    } else {
                        log.info("Result (" + result + ") is a project");

                        String uninstrumentedLocation = Util.getUninstrumentedProjectDir() + File.separator + sourceFileName;
                        Path uninstrumentedPath = Paths.get(uninstrumentedLocation);

                        if (!result.fromPrevious) {
                            log.info("Newly instrumented bytecode, move " + source + " to " + uninstrumentedPath + ", then move " + destination + " to " + source);

                            // Instrumented all classes
                            Files.move(source, uninstrumentedPath); // Move files in target to uninstrumented project directory
                            Files.move(destination, source);  // Move instrumented project to original project directory
                            locationsMapping.add(new InstrumentationLocation(null, true, source.toString(), uninstrumentedLocation));

                            // Copy resources from original uninstrumented target to instrumented target
                            IOUtil.syncDirectoryCopyNew(uninstrumentedPath, Paths.get(sourceFileName.equals("classes") ? classesDir : testClassesDir));
                        } else {
                            Path projectPath = Paths.get(sourceFileName.equals("classes") ? classesDir : testClassesDir);

                            if (result.partially) {
                                // Instrumented some classes
                                log.info("Previously instrumented some bytecode, delete " + uninstrumentedPath + ", move " + projectPath + " to " + uninstrumentedPath + ", then move " + destination + " to " + projectPath);

                                IOUtil.deleteFile(uninstrumentedPath.toFile()); // delete uninstrumented dir in artifact
                                Files.move(projectPath, uninstrumentedPath); // Move files in target to uninstrumented artifact
                                Files.move(destination, projectPath); // Move instrumented files to classes/test-classes
                                locationsMapping.add(new InstrumentationLocation(null, true, projectPath.toString(), uninstrumentedLocation));

                                // Delete files that are not in uninstrumented target but is in instrumented target
                                log.info("Checking if files are in " + projectPath + " but are not in " + uninstrumentedPath);

                                IOUtil.syncDirectoryDeleteOld(uninstrumentedPath, projectPath);
                                // Copy resources from original uninstrumented target to instrumented target
                                IOUtil.syncDirectoryCopyNew(uninstrumentedPath, projectPath);
                            } else {
                                // Don't need to instrument at all
                                log.info("Previously instrumented ALL bytecode, delete " + projectPath + ", then copy " + destination + " to " + projectPath);
                                log.info("Checking if files are in " + destination + " or " + uninstrumentedPath + " but are not in " + projectPath);
                                IOUtil.syncDirectoryDeleteOld(projectPath, destination, uninstrumentedPath);
                                // Copy resources from original uninstrumented target to instrumented target
                                IOUtil.syncDirectoryCopyNew(projectPath, destination);

                                IOUtil.deleteFile(projectPath.toFile());
                                FileUtils.copyDirectory(destination.toFile(), projectPath.toFile());
                                locationsMapping.add(new InstrumentationLocation(null, false, projectPath.toString(), uninstrumentedLocation));
                            }
                        }
                    }
                }
            }

            // Save mapping in case plugin failed
            Util.saveMapping(locationsMapping);

            if (Config.methodCIA || Config.classCIA || Config.dynamicCIA) {
                long hashTableStart = System.currentTimeMillis();
                HashTable.getInstance().save();
                TimerUtil.log("CIA - Time to save hashes: " + (System.currentTimeMillis() - hashTableStart) + " ms");
            }
        } catch (Exception e) {
            throw new MojoExecutionException(e);
        }

        TimerUtil.log("Time to process instrumentation results - " + (System.currentTimeMillis() - start) + " ms");
    }

    @Override
    protected List<InstrumentationTask> getInstrumentationTask() throws Exception {
        long start = System.currentTimeMillis();

        List<InstrumentationTask> tasks = new ArrayList<>();
        this.addTaskForProject(tasks, "classes");
        TimerUtil.log("Time to process classes - " + (System.currentTimeMillis() - start) + " ms");

        this.addTaskForProject(tasks, "test-classes");
        TimerUtil.log("Time to process test-classes - " + (System.currentTimeMillis() - start) + " ms");

        this.addTasksForLibrary(tasks);
        TimerUtil.log("Time to process library - " + (System.currentTimeMillis() - start) + " ms");

        long end = System.currentTimeMillis();
        TimerUtil.log("Time to get all instrumentation tasks - " + (end - start) + " ms");
        return tasks;
    }

    /**
     * classesName must be "classes" or "test-classes"
     */
    protected void addTaskForProject(List<InstrumentationTask> tasks, String classesName) throws Exception {
        Path classesInTarget = Paths.get(classesName.equals("classes") ? classesDir : testClassesDir);
        Path classesInArtifact = Paths.get(Util.getUninstrumentedProjectDir() + File.separator + classesName);
        Path instrumentedClassesInArtifact = Paths.get(Util.getInstrumentedProjectDir() + File.separator + classesName);

        if (!Files.exists(classesInTarget)) {
            // Cannot assume a project always has test...
            log.info("Skip " + classesName + " because it is missing");
            return;
        }

        if (Files.exists(classesInArtifact)) {
            Set<Path> pendingInstrumentFiles = ((Config.cleanByteCode || Config.methodCIA || Config.classCIA || Config.dynamicCIA) && !Config.skipCleanProject) ?
                    getPendingInstrumentFilesWithByteCodeCleaning(classesInTarget, classesInArtifact) :
                    getPendingInstrumentFiles(classesInTarget, classesInArtifact);

            log.info("pending instrument bytecode in " + classesName + ": " + pendingInstrumentFiles);

            if (pendingInstrumentFiles.isEmpty()) {
                this.previousResults.add(
                        new InstrumentationResult(
                                null,
                                classesInTarget.toString(),
                                instrumentedClassesInArtifact.toString(), true, true
                        )
                );
                return;
            }

            Path instrumentedTmpPath = Paths.get(classesInTarget + "-instrumented");
            Path pendingPath = Paths.get(Util.getPendingDir() + File.separator + classesName);

            IOUtil.deleteFile(instrumentedTmpPath.toFile());
            IOUtil.deleteFile(pendingPath.toFile());

            FileUtils.copyDirectory(instrumentedClassesInArtifact.toFile(), instrumentedTmpPath.toFile());
            IOUtil.createDirectories(pendingPath);

            if (!IOUtil.copyFilesToDest(pendingInstrumentFiles, classesInTarget, pendingPath)) {
                throw new MojoExecutionException("Unable to copy files from " + classesInTarget + " to " + pendingPath);
            }

            tasks.add(new InstrumentationTask(
                    InstrumentationTask.TaskType.PROJECT, pendingPath.toString(), instrumentedTmpPath.toString()
            ));
        } else {
            if (Config.storeHashes && !Config.skipCleanProject) {
                // First iteration, compute hashes for all files
                HasherUtil.saveHashes(classesInTarget, "project-hashes.map");
            }

            tasks.add(new InstrumentationTask(InstrumentationTask.TaskType.PROJECT, classesInTarget.toString()));
        }
    }

    protected void addTasksForLibrary(List<InstrumentationTask> tasks) {
        List<Jar> jars = LibraryUtil.getAllJar(project);

        if (Config.methodCIA) {
            log.info("Building method-level static dependencies tree for jars.");
            MethodLevelDepsBuilder builder = new MethodLevelDepsBuilder();
//            builder.build(Paths.get(project.getBuild().getOutputDirectory()), Paths.get(project.getBuild().getTestOutputDirectory()), LibraryUtil.getAllJarWithoutFilters(project));
            DependencyResults dependency = Util.isFirstRun() ?
                    builder.build(Paths.get(project.getBuild().getOutputDirectory()), Paths.get(project.getBuild().getTestOutputDirectory()), jars, true) :
                    builder.rebuild(Paths.get(project.getBuild().getOutputDirectory()), Paths.get(project.getBuild().getTestOutputDirectory()), jars, true);

            addTasksForLibrary(tasks, jars, dependency);
            return;
        } else if (Config.classCIA) {
            log.info("Building class-level static dependencies tree for jars.");
            ClassLevelDepsBuilder builder = new ClassLevelDepsBuilder();
            addTasksForLibrary(tasks, jars, builder.build(Paths.get(project.getBuild().getOutputDirectory()), Paths.get(project.getBuild().getTestOutputDirectory()), jars, true, Util.isFirstRun()));
            return;
        } else if (Config.dynamicCIA) {
            log.info("Building class-level dynamic dependencies list for jars.");
            DynamicDepsBuilder builder = new DynamicDepsBuilder();
            addTasksForLibrary(tasks, jars, builder.build(Config.lazy ? Util.getDynamicCIAFile(false) : Config.dynamicCIALog, jars, true, Util.isFirstRun()));
            return;
        }

        // Only instrument new JAR
        addTasksForLibrary(tasks, jars, null);
    }

    private void addTasksForLibrary(List<InstrumentationTask> tasks, List<Jar> jars, DependencyResults dependency) {
        for (Jar jar : jars) {
            if (!Util.isFirstRun() && dependency != null) {
                if (!dependency.isUsingJar(jar)) {
                    // CASE D: don't care
                    // during the initial run, we will instrument everything
                    // after the initial run, we will only instrument if we are using the files in the jar
                    log.info("Case D: Jar " + jar + " is excluded from instrumentation due to CIA.");
                    continue;
                } else if (Files.exists(Paths.get(jar.jarPath + "-instrumented"))){
                    // CASE A and CASE B, but already instrumented during the initial run, no need to check
                    log.info("Case A/B: Jar " + jar + " already fully instrumented");

                    Path jarPath = Paths.get(jar.jarPath);
                    Path instrumentedJarPath = Paths.get(Util.getInstrumentedLibraryDir() + File.separator + jar.groupId + File.separator + jar.artifactId + File.separator + jarPath.getFileName());
                    this.previousResults.add(new InstrumentationResult(jar, jar.jarPath, instrumentedJarPath.toString(), true, true));
                    continue;
                }
            }

            Path jarPath = Paths.get(jar.jarPath);
            Path uninstrumentedJarPath = Paths.get(Util.getUninstrumentedLibraryDir() + File.separator + jar.groupId + File.separator + jar.artifactId + File.separator + jarPath.getFileName());
            Path instrumentedJarPath = Paths.get(Util.getInstrumentedLibraryDir() + File.separator + jar.groupId + File.separator + jar.artifactId + File.separator + jarPath.getFileName());
            boolean forceInstrumentJar = false;

            if (Files.exists(instrumentedJarPath) && Files.exists(uninstrumentedJarPath)) {
                try {
                    // Check if the uninstrumented version of the instrumented jar and given jar are the same
                    if (FileUtils.contentEquals(uninstrumentedJarPath.toFile(), jarPath.toFile())) {  // TODO: find out why files length not equal but have ths same sha256
                        boolean skip = true;

                        if (dependency != null && dependency.isUsingJar(jar)) {
                            //noinspection StatementWithEmptyBody
                            if (!dependency.getNewlyUsedFiles(jar).isEmpty()) {
                                // CASE B: jar didn't change, have newly depends on, need to check checksum
                                skip = false;
                                log.info("CASE B: Jar " + jar + " is partially instrumented, need to re-instrument");
                                forceInstrumentJar = true;
                            } else {
                                // CASE A: jar didn't change, no newly depends on, can skip jar
                                log.info("CASE A: Jar " + jar + " didn't change, can skip jar");
                            }
                        }

                        if (skip) {
                            log.info("Jar " + jar + " already instrumented, can skip.");
                            this.previousResults.add(new InstrumentationResult(jar, jar.jarPath, instrumentedJarPath.toString(), true, true));
                            continue;
                        }
                    } else {
                        // TODO: test this...
                        log.info("Jar " + jarPath + " is different from " + uninstrumentedJarPath + ", same version but different content, need to re-instrument");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                log.info("Jar " + jar + " is new because it is not in " + instrumentedJarPath + " or " + uninstrumentedJarPath);
            }

            if (dependency != null) {
                // forceInstrumentJar is true if we partially instrumented this jar before
                tasks.add(new InstrumentationTask(jar, jar.jarPath, dependency, forceInstrumentJar));
            } else {
                tasks.add(new InstrumentationTask(jar, jar.jarPath));
            }
        }
    }

    protected Set<Path> getPendingInstrumentFiles(Path pathInTarget, Path pathInArtifact) {
        // Only instrument new project code
        // Compare classesDir with Util.getUninstrumentedProjectDir()/classes
        // or compare testClassesDir with Util.getUninstrumentedProjectDir()/test-classes
        Set<Path> pendingInstrumentFiles = new HashSet<>();
        try {
            Files.walkFileTree(pathInTarget, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path pathInDest = pathInArtifact.resolve(pathInTarget.relativize(file));
                    if (file.toString().endsWith(".class")) {
                        if (!Files.exists(pathInDest)) {
                            // .class file in target but not in previous classes
                            if (!pendingInstrumentFiles.contains(file)) {
                                IOUtil.findAllBaseClasses(pendingInstrumentFiles, file);
                                pendingInstrumentFiles.add(file);
                            }
                        } else {
                            if (!FileUtils.contentEquals(file.toFile(), pathInDest.toFile())) {
                                if (!pendingInstrumentFiles.contains(file)) {
                                    IOUtil.findAllBaseClasses(pendingInstrumentFiles, file);
                                    pendingInstrumentFiles.add(file);
                                }
                            }
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return pendingInstrumentFiles;
    }

    protected Set<Path> getPendingInstrumentFilesWithByteCodeCleaning(Path pathInTarget, Path pathInArtifact) {
        // Only instrument new project code
        // Compare classesDir with Util.getUninstrumentedProjectDir()/classes
        // or compare testClassesDir with Util.getUninstrumentedProjectDir()/test-classes
        Set<Path> pendingInstrumentFiles = new HashSet<>();
        Hasher hasher = new Hasher(Hasher.Algorithm.CRC32, 1000, true);

        // Read original project hashes from disk
        HashMap<String, String> projectHashes = Config.storeHashes ? (HashMap<String, String>) Util.readFromDisk("project-hashes.map") : new HashMap<>();
        HashMap<String, String> newProjectHashes = projectHashes == null ? new HashMap<>() : projectHashes;

        try {
            Files.walkFileTree(pathInTarget, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String filePathString = file.toString();
                    Path pathInDest = pathInArtifact.resolve(pathInTarget.relativize(file));

                    if (file.toString().endsWith(".class")) {
                        if (!Files.exists(pathInDest)) {
                            // .class file in target but not in previous classes
                            if (!pendingInstrumentFiles.contains(file)) {
                                IOUtil.findAllBaseClasses(pendingInstrumentFiles, file);
                                pendingInstrumentFiles.add(file);
                            }
                            // don't compute and store hashes because we don't want to pay the price at this time
                            // we will compute the hashes when the files are changed in the future
                        } else {
                            if (!FileUtils.contentEquals(file.toFile(), pathInDest.toFile())) {
                                // .class file in both target and previous classes, but content not equal
                                // check if cleaned bytecodes are equal or not
                                String newFileHash = hasher.hashURL("file:" + filePathString);
                                String oldFileHash;

                                if (Config.storeHashes && projectHashes != null && projectHashes.containsKey(filePathString)) {
                                    oldFileHash = projectHashes.get(filePathString);
                                } else {
                                    oldFileHash = hasher.hashURL("file:" + pathInDest);
                                }

                                if (oldFileHash.equals(newFileHash)) {
                                    // same file after we remove debug infos
                                    System.out.println("Skipping " + file + " because it is the same as " + pathInDest);
                                    return FileVisitResult.CONTINUE;
                                }

                                newProjectHashes.put(filePathString, newFileHash);

                                if (!pendingInstrumentFiles.contains(file)) {
                                    IOUtil.findAllBaseClasses(pendingInstrumentFiles, file);
                                    pendingInstrumentFiles.add(file);
                                }
                            }
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (Config.storeHashes)
            Util.saveToDisk(newProjectHashes, "project-hashes.map");

        return pendingInstrumentFiles;
    }
}
