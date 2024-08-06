package org.iemop.util.sclasses;

import org.iemop.instrumentation.library.Jar;
import org.iemop.util.TimerUtil;
import org.iemop.util.Util;
import org.iemop.util.smethods.DependencyResults;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ClassLevelDepsBuilder {
    Map<Klass, Set<Klass>> dependencies = new HashMap<>();

    public DependencyResults build(Path codeClasses, Path testClasses, List<Jar> jars, boolean saveToDisk, boolean firstRun) {
        System.out.println("Getting dependency from JDeps");

        // Build args for JDeps
        String[] args = getArgument(codeClasses, testClasses, jars);
        dependencies = JDeps.runJdeps(args);

        Map<Jar, Set<String>> jarToClasses = getFilesToInstrument(testClasses, jars);

        System.out.println("jarToClasses: " + jarToClasses.size());
        for (Map.Entry<Jar, Set<String>> entry : jarToClasses.entrySet()) {
            Jar jar = entry.getKey();
            Set<String> classes = entry.getValue();
            System.out.println("Jar " + jar + " contains " + classes.size() + " classes");
        }

        DependencyResults res;
        if (firstRun) {
            res = new DependencyResults(jarToClasses, null);
        } else {
            long start = System.currentTimeMillis();
            Map<Jar, Set<String>> jarToClassesOld = (Map<Jar, Set<String>>) Util.readFromDisk(Util.getArtifactDir() + File.separator + "deps.iemop");
            TimerUtil.log("Read previous dependency map in " + (System.currentTimeMillis() - start) + " ms");
            res = new DependencyResults(jarToClasses, jarToClassesOld);
        }

        if (saveToDisk) {
            long start = System.currentTimeMillis();
            Util.saveToDisk(jarToClasses, Util.getArtifactDir() + File.separator + "deps.iemop");
            TimerUtil.log("Saved all classCIA metadata in " + (System.currentTimeMillis() - start) + " ms");
        }

        return res;
    }

    private Map<Jar, Set<String>> getFilesToInstrument(Path testClasses, List<Jar> jars) {
        String testName = testClasses.getFileName().toString();

        Set<Klass> visitedClasses = new HashSet<>();
        for (Klass klass : dependencies.keySet()) {
            if (!visitedClasses.contains(klass) && klass.getLocationName().equals(testName)) {
                visitedClasses.add(klass);
                getDepsDFS(klass, visitedClasses);
            }
        }

        Map<String, Jar> pathToJar = new HashMap<>();
        Map<Jar, Set<String>> jarToClasses = new HashMap<>();
        for (Jar jar : jars) {
            pathToJar.put(Paths.get(jar.jarPath).getFileName().toString(), jar);
            jarToClasses.put(jar, new HashSet<>());
        }

        for (Klass klass : visitedClasses) {
            if (pathToJar.containsKey(klass.getLocationName())) {
                jarToClasses.computeIfAbsent(pathToJar.get(klass.getLocationName()), k -> new HashSet<>()).add(klass.getClassName().replace(".", "/"));
            }
        }

        return jarToClasses;
    }

    private void getDepsDFS(Klass className, Set<Klass> visitedClasses) {
        // need to check which dependency graph to visit
        if (!dependencies.containsKey(className))
            return;

        for (Klass klass : dependencies.get(className)) {
            if (!visitedClasses.contains(klass)) {
                visitedClasses.add(klass);
                getDepsDFS(klass, visitedClasses);
            }
        }
    }

    public String[] getArgument(Path codeClasses, Path testClasses, List<Jar> jars) {
        return new String[]{
                "-f", "^java(x|\\.).*|^sun\\..*", // don't show JDK internal classes
                "-cp", getClasspath(jars),
                "-R",
                "-verbose:class",
                codeClasses.toString(),
                testClasses.toString()
        };
    }

    private String getClasspath(List<Jar> jars) {
        StringBuilder cp = new StringBuilder();
        boolean first = true;
        for (Jar jar : jars) {
            if (first) {
                first = false;
            } else {
                cp.append(":");
            }
            cp.append(jar.jarPath);
        }
        return cp.toString();
    }
}
