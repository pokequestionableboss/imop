package org.iemop;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.iemop.instrumentation.InstrumentationLocation;
import org.iemop.instrumentation.InstrumentationRestore;
import org.iemop.instrumentation.library.Jar;
import org.iemop.util.*;
import org.iemop.util.dclasses.DynamicDepsBuilder;
import org.iemop.util.dclasses.DynamicTestRunner;
import org.iemop.util.smethods.DependencyResults;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mojo(name = "run", requiresDirectInvocation = true, requiresDependencyResolution = ResolutionScope.TEST)
@Execute(phase = LifecyclePhase.TEST, lifecycle = "run")
public class RunMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    public void execute() throws MojoExecutionException {
        if (Config.skipMOP) {
            getLog().info("Finish running ieMOP - SKIPPED");
            return;
        }

        long start = System.currentTimeMillis();

        // Check for safety if it is dynamicCIALazy, and we didn't run the first test
        if (Config.dynamicCIA && Config.lazy) {
            getLog().info("Checking dynamicCIA safety");
            // Find classes that are loaded in the latest test run (Config.dynamicCIALog), but not in dynamic.log
            // If a class is in both Config.dynamicCIALog and dynamic.log, then we do not need to do anything
            // because InstrumentCIAJar strategy will keep the instrumentation up to date

            DependencyResults result = new DynamicDepsBuilder().build(Config.dynamicCIALog, LibraryUtil.getAllJar(project), false, false);
            if (result.hasNewlyUsedFiles()) {
                if (DynamicTestRunner.skipCIA) {
                    // Skip CIA, latest test run shows we have newly used files, run is unsafe
                    getLog().error("dynamicCIA run is unsafe: some classes are loaded in this current test run, but not in the previous one");
                    getLog().error("Next run should run test twice");
                    try {
                        Files.deleteIfExists(Paths.get(Util.getDynamicCIAFile(true)));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    // Didn't skip CIA (run test twice), but we have newly used files after using test with MOP
                    // handle classes that will get loaded if run with MOP only
                    // Solution: first run should update dynamicdeps (so we know what classes are loaded if run with MOP), but it is still safe
                    // After the first run, we will always mark `unsafe classes` as used, even when they are not used anymore

                    long saveStart = System.currentTimeMillis();
                    // Add result.currentJarToNewFiles to forceInclude
                    Map<Jar, Set<String>> forceInclude = DynamicDepsBuilder.getForceInclude();
                    for (Map.Entry<Jar, Set<String>> entry : result.currentJarToNewFiles.entrySet()) {
                        if (forceInclude.containsKey(entry.getKey()))
                            forceInclude.get(entry.getKey()).addAll(entry.getValue());
                        else
                            forceInclude.put(entry.getKey(), entry.getValue());
                    }

                    Util.saveToDisk(forceInclude, Util.getArtifactDir() + File.separator + "classes-loaded-only-with-mop.iemop");
                    Util.saveToDisk(result.currentJarToFiles, Util.getArtifactDir() + File.separator + "deps.iemop");
                    getLog().warn("dynamicCIA run is unsafe, but it should be. Adding new loaded classes in a white list");
                    TimerUtil.log("Saved special dynamicCIA metadata in " + (System.currentTimeMillis() - saveStart) + " ms");
                }
            }
        }

        /*
         * BISM Experiment [INSTRUMENTATION ONLY, NO MONITORING], no need to clean up
         */
        if (Config.bism || Config.noMonitoring)
            return;

        getLog().info("Finish running ieMOP");
        InstrumentationRestore.restore(getLog(), project.getBuild().getOutputDirectory(), project.getBuild().getTestOutputDirectory());
        TimerUtil.log("Time restore repository: " + (System.currentTimeMillis() - start) + " ms");
    }
}
