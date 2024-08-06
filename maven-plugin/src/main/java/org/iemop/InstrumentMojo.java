package org.iemop;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.iemop.instrumentation.InstrumentationRestore;
import org.iemop.instrumentation.strategy.BasicIncrementalInstrumentation;
import org.iemop.instrumentation.strategy.InstrumentationStrategy;
import org.iemop.instrumentation.strategy.NonIncrementalInstrumentation;
import org.iemop.util.Config;

@Mojo(name = "instrument", requiresDirectInvocation = true, requiresDependencyResolution = ResolutionScope.TEST)
public class InstrumentMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    public void execute() throws MojoExecutionException {
        getLog().info("Instrument");

        try {
//            InstrumentationStrategy strategy = new NonIncrementalInstrumentation(project, getLog(), threads);
            InstrumentationStrategy strategy = new BasicIncrementalInstrumentation(project, getLog(), Config.threads);
            strategy.instrument();
        } catch (MojoExecutionException e) {
            if (!Config.skipMOP)
                InstrumentationRestore.restore(getLog(), project.getBuild().getOutputDirectory(), project.getBuild().getTestOutputDirectory());
            throw e;
        }
    }
}
