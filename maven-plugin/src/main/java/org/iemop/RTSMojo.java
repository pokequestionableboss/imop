package org.iemop;

import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.iemop.instrumentation.InstrumentationRestore;
import org.iemop.util.Config;
import org.iemop.util.TimerUtil;
import org.iemop.util.Util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Mojo(name = "rts", requiresDirectInvocation = true, requiresDependencyResolution = ResolutionScope.TEST)
@Execute(phase = LifecyclePhase.PROCESS_TEST_CLASSES, lifecycle = "rts")
public class RTSMojo extends AbstractMojo {

    @Parameter(property = "tool", defaultValue = "starts")
    private String tool;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

    @Component
    private BuildPluginManager manager;

    public void execute() throws MojoExecutionException {
        long start = System.currentTimeMillis();
        List<Dependency> dependencyList = new ArrayList<>();
        Dependency ajc = dependency("org.aspectj", "aspectjtools", "1.9.7");
        dependencyList.add(ajc);

        Dependency mop = new Dependency();
        mop.setGroupId("javamop-aspect");
        mop.setArtifactId("javamop-aspect");
        mop.setVersion("1.0");
        mop.setScope("system");
        mop.setSystemPath(Util.getAspectPath());
        dependencyList.add(mop);

        Dependency rvm = new Dependency();
        rvm.setGroupId("rv-monitor-rt");
        rvm.setArtifactId("rv-monitor-rt");
        rvm.setVersion("1.0");
        rvm.setScope("system");
        rvm.setSystemPath(Util.getRVMPath());
        dependencyList.add(rvm);

        if (tool.equals("starts")) {
            getLog().info("Running starts:run");
            executeMojo(
                    plugin(groupId("edu.illinois"), artifactId("starts-maven-plugin"), version("1.4"), dependencyList),
                    goal("run"),
                    configuration(),
                    executionEnvironment(
                            project,
                            session,
                            manager
                    )
            );
        } else if (tool.equals("ekstazi")){
            getLog().info("Running ekstazi:select");
            executeMojo(
                    plugin(groupId("org.ekstazi"), artifactId("ekstazi-maven-plugin"), version("5.3.0"), dependencyList),
                    goal("select"),
                    configuration(),
                    executionEnvironment(
                            project,
                            session,
                            manager
                    )
            );
        } else {
            throw new MojoExecutionException("Unknown RTS tool " + tool + ", currently only supports starts & ekstazi");
        }

        TimerUtil.log("Finish selecting test with RTS in: " + (System.currentTimeMillis() - start) + " ms");
        long startTest = System.currentTimeMillis();
        getLog().info("Start running test");

        executeMojo(
                plugin(
                        groupId("org.apache.maven.plugins"),
                        artifactId("maven-surefire-plugin"),
                        version(System.getenv("SUREFIRE_VERSION") != null ? System.getenv("SUREFIRE_VERSION") : "3.1.2")
                ),
                goal("test"),
                configuration(),
                executionEnvironment(
                        project,
                        session,
                        manager
                )
        );

        TimerUtil.log("Finish running test in: " + (System.currentTimeMillis() - startTest) + " ms");
        TimerUtil.log("Finish running RTS in: " + (System.currentTimeMillis() - start) + " ms");

        if (Config.skipMOP) {
            getLog().info("Finish running ieMOP - SKIPPED");
            return;
        }

        long startFinish = System.currentTimeMillis();
        getLog().info("Finish running ieMOP");
        InstrumentationRestore.restore(getLog(), project.getBuild().getOutputDirectory(), project.getBuild().getTestOutputDirectory());
        TimerUtil.log("Time restore repository: " + (System.currentTimeMillis() - startFinish) + " ms");
    }
}
