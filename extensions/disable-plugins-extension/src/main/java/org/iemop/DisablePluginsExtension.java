package org.iemop;

import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Named
@Singleton
public class DisablePluginsExtension extends AbstractEventSpy {
    private void removePlugins(MavenProject project) {
        Set<Plugin> removePlugins = new HashSet<>();
        for (Plugin plugin : project.getBuild().getPlugins()) {
            if (plugin.getGroupId().equals("com.mycila") && plugin.getArtifactId().equals("license-maven-plugin")) {
                removePlugins.add(plugin);
            }
        }

        for (Plugin plugin : removePlugins) {
            System.out.println("Remove plugin " + plugin.getGroupId() + ":" + plugin.getArtifactId());
            project.getBuild().removePlugin(plugin);
        }
    }

    @Override
    public void onEvent(Object event) {
        if (event instanceof ExecutionEvent) {
            ExecutionEvent e = (ExecutionEvent) event;
            if (e.getType() == ExecutionEvent.Type.SessionStarted) {
                List<MavenProject> sortedProjects = e.getSession().getProjectDependencyGraph().getSortedProjects();
                for (MavenProject project : sortedProjects) {
                    removePlugins(project);
                }
            }
        }
    }
}
