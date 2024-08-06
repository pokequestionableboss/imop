package org.iemop;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.iemop.util.Config;
import org.iemop.util.IOUtil;
import org.iemop.util.TimerUtil;
import org.iemop.util.Util;
import org.iemop.util.dclasses.DynamicTestRunner;
import org.iemop.util.hasher.HashTable;
import org.iemop.util.hasher.IncrementalJarCleaner;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Mojo(name = "setup", requiresDirectInvocation = true, requiresDependencyResolution = ResolutionScope.TEST)
public class SetupMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter( defaultValue = "${settings}", readonly = true )
    private Settings settings;

    @Parameter(property = "timer", defaultValue = "false")
    private boolean timer;

    @Parameter(property = "variant", defaultValue = "none")
    private String variant;

    @Parameter(property = "incrementalJar", defaultValue = "false")
    private boolean incrementalJar;

    @Parameter(property = "jarThreads", defaultValue = "false")
    private boolean jarThreads;

    @Parameter(property = "threadsUseFiles", defaultValue = "false")
    private boolean threadsUseFiles;

    @Parameter(property = "cleanByteCode", defaultValue = "false")
    private boolean cleanByteCode;

    @Parameter(property = "storeHashes", defaultValue = "false")
    private boolean storeHashes;

    @Parameter(property = "methodCIA", defaultValue = "false")
    private boolean methodCIA;

    @Parameter(property = "classCIA", defaultValue = "false")
    private boolean classCIA;

    @Parameter(property = "dynamicCIA", defaultValue = "")
    private String dynamicCIA;

    @Parameter(property = "lazy", defaultValue = "false")
    private boolean lazy;

    @Parameter(property = "stats", defaultValue = "false")
    private boolean stats;

    @Parameter(property = "bism", defaultValue = "false")
    private boolean bism;

    @Parameter(property = "noMonitoring", defaultValue = "false")
    private boolean noMonitoring;

    @Parameter(property = "skipCleanProject", defaultValue = "false")
    private boolean skipCleanProject;

    @Parameter(property = "hashesSingleThread", defaultValue = "true")
    private boolean hashesSingleThread;

    @Parameter(property = "threads", defaultValue = "1")
    private int threads;

    public void execute() throws MojoExecutionException {
        long start = System.currentTimeMillis();
        getLog().info("Setting up");

        Util.setArtifactDir(project.getBasedir().getAbsolutePath());
        Util.setFirstRun(true);
        new Util().copyResources(stats, bism);
        IOUtil.createDirectories(Paths.get(Util.getUninstrumentedProjectDir()));
        IOUtil.createDirectories(Paths.get(Util.getUninstrumentedLibraryDir()));
        IOUtil.createDirectories(Paths.get(Util.getInstrumentedProjectDir()));
        IOUtil.createDirectories(Paths.get(Util.getInstrumentedLibraryDir()));
        IOUtil.createDirectories(Paths.get(Util.getJarDir()));
        IOUtil.createDirectories(Paths.get(Util.getWorkspaceDir()));

        try {
            Util.classpath = String.join(File.pathSeparator, project.getTestClasspathElements());
            Util.m2 = settings.getLocalRepository();
        } catch (DependencyResolutionRequiredException e) {
            Util.classpath = "";
        }

        if (variant.equals("none")) {
            Config.incrementalJar = incrementalJar;
            Config.jarThreads = jarThreads;
            Config.threadsUseFiles = threadsUseFiles;
            Config.cleanByteCode = cleanByteCode;
            Config.storeHashes = storeHashes;
            Config.methodCIA = methodCIA;
            Config.classCIA = classCIA;

            if (dynamicCIA != null) {
                Config.dynamicCIA = true;
                Config.dynamicCIALog = dynamicCIA;
                Config.lazy = lazy;
            }
        } else {
            Config.setVariant(variant);

            if (dynamicCIA != null) {
                Config.dynamicCIALog = dynamicCIA;
            }
        }

        // DynamicCIA setting
        if (Config.dynamicCIA) {
            // If the variant is dynamicCIALazy, then dynamicCIALog points to the current run's path
            // If the variant is dynamicCIA, then dynamicCIALog points to initial test run's path
            if (dynamicCIA == null) {
                throw new MojoExecutionException("dynamicCIA is not set, but variant is set to dynamicCIA");
            }

            // if it is lazy, then we only run this if we have new/updated jar, or previous run is unsafe
            if (Config.lazy) {
                if (DynamicTestRunner.requireTestRun(Util.classpath)) {
                    getLog().info("New library or previous run is unsafe, running test without MOP");
                    if (!DynamicTestRunner.run(Util.getM2Repo(), Util.getDynamicCIAFile(false))) {
                        throw new MojoExecutionException("Unable to run test for dynamicCIA");
                    }
                }
            } else {
                Path log = Paths.get(Config.dynamicCIALog);
                if (!Files.exists(log)) {
                    getLog().info("Cannot find dynamicCIA test log, running test without MOP");
                    if (!DynamicTestRunner.run(Util.getM2Repo(), log.toString())) {
                        throw new MojoExecutionException("Unable to run test for dynamicCIA");
                    }
                }
            }
        }

        Config.timer = timer;
        Config.threads = threads;
        Config.skipCleanProject = skipCleanProject;
        Config.hashesSingleThread = hashesSingleThread;
        Config.bism = bism;
        Config.noMonitoring = noMonitoring;

        if (Config.incrementalJar) {
            IncrementalJarCleaner.getInstance(); // create instance
        }

        if (Config.methodCIA || Config.classCIA || Config.dynamicCIA) {
            long hashTableStart = System.currentTimeMillis();
            HashTable.getInstance(); // create instance
            TimerUtil.log("CIA - Time to load hashes: " + (System.currentTimeMillis() - hashTableStart) + " ms");
        }

        TimerUtil.log("Time to setup plugin: " + (System.currentTimeMillis() - start) + " ms");
    }
}
