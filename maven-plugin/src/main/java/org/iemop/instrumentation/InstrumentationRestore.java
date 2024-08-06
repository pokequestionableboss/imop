package org.iemop.instrumentation;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.logging.Log;
import org.iemop.util.IOUtil;
import org.iemop.util.Util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class InstrumentationRestore {
    public static void restore(Log log, String classesDir, String testClassesDir) {
        log.info("Restoring...");
        // Move instrumented jar to artifact directory, then move uninstrumented jar back to m2 repo
        // Move instrumented project code to artifact directory, then move uninstrumented jar back to original dir
        List<InstrumentationLocation> locationsMapping = Util.readMapping();
        for (InstrumentationLocation mapping : locationsMapping) {
            log.info("Restoring " + mapping);

            try {
                Path instrumented = Paths.get(mapping.getInstrumented());
                Path uninstrumented = Paths.get(mapping.getUninstrumented());

                if (mapping.isNew()) {
                    // Newly instrumented, move instrumented project/library to artifact dir
                    if (mapping.isJar()) {
                        Path instrumentedLibInArtifact = Paths.get(Util.getInstrumentedLibraryDir() + File.separator + mapping.getJar().groupId + File.separator + mapping.getJar().artifactId + File.separator + instrumented.getFileName());
                        Files.createDirectories(instrumentedLibInArtifact.getParent());

                        log.info("It is a newly instrumented jar, moving " + instrumented + " to " + instrumentedLibInArtifact);

                        Files.move(instrumented, instrumentedLibInArtifact, StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        Path instrumentedProjInArtifact = Paths.get(Util.getInstrumentedProjectDir() + File.separator + instrumented.getFileName());

                        log.info("It is a project, delete " + instrumentedProjInArtifact + " if exists, then move " + instrumented + " to " + instrumentedProjInArtifact);
                        if (Files.exists(instrumentedProjInArtifact))
                            IOUtil.deleteFile(instrumentedProjInArtifact.toFile());

                        Files.move(instrumented, instrumentedProjInArtifact, StandardCopyOption.REPLACE_EXISTING);
                    }
                } else if (!mapping.isJar()) {
                    // Not newly instrumented PROJECT, delete target/classes or target/test-classes so we can do the copy below
                    log.info("Not newly instrumented project, so delete original location " + instrumented);
                    IOUtil.deleteFile(instrumented.toFile());
                }

                // Move uninstrumented project/library back to original location
                log.info("Copy uninstrumented " + uninstrumented + " to original location " + instrumented);
                if (mapping.isJar()) {
                    Files.copy(uninstrumented, instrumented, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    FileUtils.copyDirectory(uninstrumented.toFile(), instrumented.toFile());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Util.deleteMapping();

        // Delete pending if exists
        if (Files.exists(Paths.get(Util.getPendingDir()))) {
            log.info("Delete pending directory " + Util.getPendingDir());
            IOUtil.deleteFile(Paths.get(Util.getPendingDir()).toFile());
        }

        Path classesPath = Paths.get(classesDir + "-instrumented");
        if (Files.exists(classesPath)) {
            log.info("Delete pending directory " + classesDir + "-instrumented");
            IOUtil.deleteFile(classesPath.toFile());
        }

        Path testClassesPath = Paths.get(testClassesDir + "-instrumented");
        if (Files.exists(testClassesPath)) {
            log.info("Delete pending directory " + classesDir + "-instrumented");
            IOUtil.deleteFile(testClassesPath.toFile());
        }

//        Path tmpPath = Paths.get(Util.getWorkspaceDir();
//        if (Files.exists(tmpPath)) {
//            log.info("Delete tmp directory " + tmpPath);
//            IOUtil.deleteFile(tmpPath.toFile());
//        }
    }
}
