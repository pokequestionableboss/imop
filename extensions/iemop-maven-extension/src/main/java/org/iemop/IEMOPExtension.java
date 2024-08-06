package org.iemop;

import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.File;
import java.net.URI;
import java.nio.file.*;
import java.util.HashMap;
import java.util.List;

@Named
@Singleton
public class IEMOPExtension extends AbstractEventSpy {

    public static final String IEMOP = ".iemop";

    public void copyResources(MavenProject project, boolean showStats) {
        System.out.println("Copying resources");
        String resDir = project.getBasedir().getAbsolutePath() + File.separator + IEMOP + File.separator + "resources";

        try {
            URI resource = getClass().getResource("").toURI();
            FileSystem fs = FileSystems.newFileSystem(resource, new HashMap<String, String>());

            Files.createDirectories(Paths.get(resDir));

            Path aspectPath = Paths.get(resDir + File.separator + "myaspects.jar");
            if (!Files.exists(aspectPath)) {
                Files.copy(fs.getPath(showStats ? "myaspects-stats.jar" : "myaspects.jar"), aspectPath, StandardCopyOption.REPLACE_EXISTING);
            }

            Path rvmPath = Paths.get(resDir + File.separator + "rv-monitor-rt.jar");
            if (!Files.exists(rvmPath)) {
                Files.copy(fs.getPath("rv-monitor-rt.jar"), rvmPath, StandardCopyOption.REPLACE_EXISTING);
            }

            Path aspectjRTPath = Paths.get(resDir + File.separator + "aspectjrt.jar");
            if (!Files.exists(aspectjRTPath)) {
                Files.copy(fs.getPath("aspectjrt.jar"), aspectjRTPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void insertDependencyToAllPlugin(MavenProject project) {
        System.out.println("Injecting aspectjtools to all plugins");
        Dependency dependency = new Dependency();
        dependency.setGroupId("org.aspectj");
        dependency.setArtifactId("aspectjtools");
        dependency.setVersion("1.9.7");

        for (Plugin plugin : project.getBuildPlugins()) {
            boolean found = false;

            for (Dependency dep : plugin.getDependencies()) {
                if (dep.getArtifactId().equals("aspectjtools")) {
                    found = true;
                }
            }

            if (!found) {
                plugin.getDependencies().add(dependency);
            }
        }
    }

    private void addDependencies(MavenProject project) {
        System.out.println("Injecting dependencies");
        String resDir = project.getBasedir().getAbsolutePath() + File.separator + IEMOP + File.separator + "resources";

        /*
        Add this to dependencies node
            <dependencies>
                <dependency>
                  <groupId>org.aspectj</groupId>
                  <artifactId>aspectjrt</artifactId>
                  <version>1.9.7</version>
                </dependency>

                <dependency>
                  <groupId>javamop-aspect</groupId>
                  <artifactId>javamop-aspect</artifactId>
                  <version>1.0</version>
                </dependency>

                <dependency>
                  <groupId>rv-monitor-rt</groupId>
                  <artifactId>rv-monitor-rt</artifactId>
                  <version>1.0</version>
                </dependency>
            </dependencies>
         */
        List<Dependency> dependencies = project.getDependencies();

        Dependency aspectjRT = new Dependency();
        aspectjRT.setGroupId("org.aspectj");
        aspectjRT.setArtifactId("aspectjrt");
        aspectjRT.setVersion("1.9.7");
        aspectjRT.setScope("system");
        aspectjRT.setSystemPath(resDir + File.separator + "aspectjrt.jar");

        Dependency javamopAspect = new Dependency();
        javamopAspect.setGroupId("javamop-aspect");
        javamopAspect.setArtifactId("javamop-aspect");
        javamopAspect.setVersion("1.0");
        javamopAspect.setScope("system");
        javamopAspect.setSystemPath(resDir + File.separator + "myaspects.jar");

        Dependency rvMonitorRV = new Dependency();
        rvMonitorRV.setGroupId("rv-monitor-rt");
        rvMonitorRV.setArtifactId("rv-monitor-rt");
        rvMonitorRV.setVersion("1.0");
        rvMonitorRV.setScope("system");
        rvMonitorRV.setSystemPath(resDir + File.separator + "rv-monitor-rt.jar");

        Dependency plugin = new Dependency();
        plugin.setGroupId("org.iemop");
        plugin.setArtifactId("iemop-maven-plugin");
        plugin.setVersion("1.0");

        dependencies.add(aspectjRT);
        dependencies.add(javamopAspect);
        dependencies.add(rvMonitorRV);
        dependencies.add(plugin);
    }

    public void updateSurefire(MavenProject project) {
        for (Plugin plugin : project.getBuildPlugins()) {
            if (plugin.getGroupId().equals("org.apache.maven.plugins") &&
                    plugin.getArtifactId().equals("maven-surefire-plugin")) {
                String version = System.getenv("SUREFIRE_VERSION") != null ? System.getenv("SUREFIRE_VERSION") : "3.1.2";
                System.out.println("Set surefire version to " + version);
                plugin.setVersion(version);
            }
        }
    }

    @Override
    public void onEvent(Object event) {
        if (System.getenv("ADD_IEMOP_EXTENSION") != null && System.getenv("ADD_IEMOP_EXTENSION").equals("0"))
            return;
        
        boolean showStats = System.getenv("SHOW_STATS") != null && System.getenv("SHOW_STATS").equals("1");

        boolean forceSurefire = System.getenv("SUREFIRE_VERSION") != null;

        if (event instanceof ExecutionEvent) {
            ExecutionEvent e = (ExecutionEvent) event;
            if (e.getType() == ExecutionEvent.Type.SessionStarted) {
                List<MavenProject> sortedProjects = e.getSession().getProjectDependencyGraph().getSortedProjects();
                for (MavenProject project : sortedProjects) {
                    copyResources(project, showStats);

                    insertDependencyToAllPlugin(project);
                    addDependencies(project);

                    if (showStats || forceSurefire)
                        updateSurefire(project);
                }
            }
        }
    }
}
