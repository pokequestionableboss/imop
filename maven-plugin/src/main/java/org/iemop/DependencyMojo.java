package org.iemop;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.iemop.instrumentation.library.Jar;
import org.iemop.util.Config;
import org.iemop.util.LibraryUtil;
import org.iemop.util.Util;
import org.iemop.util.smethods.DependencyResults;
import org.iemop.util.smethods.Method;
import org.iemop.util.smethods.MethodLevelDepsBuilder;
import org.iemop.util.smethods.StaticDependencies;

import java.nio.file.Paths;
import java.util.*;

@Mojo(name = "dependency", requiresDirectInvocation = true, requiresDependencyResolution = ResolutionScope.TEST)
@Execute(phase = LifecyclePhase.TEST_COMPILE)
public class DependencyMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "type", defaultValue = "method")
    private String type;

    @Parameter(property = "showGraph", defaultValue = "false")
    private boolean showGraph;

    @Parameter(property = "showAllDependency", defaultValue = "false")
    private boolean showAllDependency;

    @Parameter(property = "showNewDependency", defaultValue = "false")
    private boolean showNewDependency;

    @Parameter(property = "searchClass", defaultValue = "")
    private String searchClass;

    @Parameter(property = "threads", defaultValue = "1")
    private int threads;

    @Override
    public void execute() throws MojoExecutionException {
        getLog().info("Building dependency graph");

        if (!type.equals("method") && !type.equals("class") && !type.equals("dynamic")) {
            throw new MojoExecutionException("Option `type` must be method, class, or dynamic");
        }

        if (!showGraph && !showAllDependency && !showNewDependency && searchClass == null) {
            throw new MojoExecutionException("Need to set `showGraph`, `showAllDependency`, `showNewDependency` or `searchClass`");
        }

        Util.setArtifactDir(project.getBasedir().getAbsolutePath());
        Util.setFirstRun(false);
        Config.threads = threads;

        List<Jar> jars = LibraryUtil.getAllJar(project);

        if (type.equals("method")) {
            MethodLevelDepsBuilder builder = new MethodLevelDepsBuilder();
            DependencyResults dependency = Util.isFirstRun() ?
                    builder.build(Paths.get(project.getBuild().getOutputDirectory()), Paths.get(project.getBuild().getTestOutputDirectory()), jars, false) :
                    builder.rebuild(Paths.get(project.getBuild().getOutputDirectory()), Paths.get(project.getBuild().getTestOutputDirectory()), jars, false);


            if (showGraph) {
                getLog().info("Method name to method names graph:");
                for (Map.Entry<String, StaticDependencies> entry : builder.dependencies.entrySet()) {
                    System.out.println(entry.getKey() + " has the following graph:");
                    System.out.println(entry.getValue().methodName2MethodNames);
                }
            }

            if (showAllDependency) {
                getLog().info("All dependency:");
                for (Map.Entry<Jar, Set<String>> entry : dependency.currentJarToFiles.entrySet()) {
                    System.out.println(entry.getKey() + " has the following used files:");
                    System.out.println(entry.getValue());
                }
            }

            if (showNewDependency) {
                getLog().info("New dependency:");
                for (Map.Entry<Jar, Set<String>> entry : dependency.currentJarToNewFiles.entrySet()) {
                    System.out.println(entry.getKey() + " has the following new used files:");
                    System.out.println(entry.getValue());
                }
            }

            if (searchClass != null) {
                getLog().info("Searching path to " + searchClass);
                new PathSearching(builder, searchClass).printPath();
            }
        }
    }

    private class PathSearching {
        private Map<String, StaticDependencies> dependencies;
        private Set<String> testClassesFile;
        private String searchClass;
        private Map<Method, Set<String>> methodToEntity = new HashMap<>();

        PathSearching(MethodLevelDepsBuilder builder, String searchClass) {
            this.dependencies = builder.dependencies;
            this.testClassesFile = builder.testClassesFile;
            this.searchClass = searchClass;

            for (Map.Entry<String, StaticDependencies> entity : dependencies.entrySet()) {
                for (Method method : entity.getValue().methodName2MethodNames.keySet()) {
                    methodToEntity.computeIfAbsent(method, k -> new HashSet<>()).add(entity.getKey());
                }
            }
        }

        private void printPath() {
            Set<Method> visited = new HashSet<>();

            boolean found = false;
            for (Method method : dependencies.get("project").methodName2MethodNames.keySet()) {
                if (!visited.contains(method) && testClassesFile.contains(method.getClassName())) { // testClassesFile.contains(method.getClassName())
                    if (search(method, visited, new ArrayList<>())) {
                        found = true;
                        break;
                    }
                }
            }

            if (!found) {
                System.out.println("Cannot find a path from project to " + searchClass);
            }
        }

        private boolean search(Method methodName, Set<Method> visitedMethods, List<Method> pathToClass) {
            if (methodName.getClassName().equals(searchClass)) {
                // Found class
                for (Method method : pathToClass) {
                    System.out.print(method + " -> ");
                }

                System.out.println(methodName);
                return true;
            }

            boolean found = false;
            if (methodToEntity.containsKey(methodName)) {
                for (String entity : methodToEntity.get(methodName)) {
                    for (Method method : dependencies.get(entity).methodName2MethodNames.get(methodName)) {
                        if (!visitedMethods.contains(method)) {
                            visitedMethods.add(method);

                            List<Method> pathToClassNew = new ArrayList<>(pathToClass);
                            pathToClassNew.add(methodName);
                            if (search(method, visitedMethods, pathToClassNew)) {
                                found = true;
                                break;
                            }
                        }
                    }
                }
            }
            return found;
        }
    }
}
