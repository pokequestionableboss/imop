package org.iemop.util.smethods;

import org.iemop.instrumentation.InstrumentationLocation;
import org.iemop.instrumentation.InstrumentationResult;
import org.iemop.instrumentation.library.Jar;
import org.iemop.util.Config;
import org.iemop.util.TimerUtil;
import org.iemop.util.Util;
import org.objectweb.asm.ClassReader;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class MethodLevelDepsBuilder {

    public Map<String, StaticDependencies> dependencies = new HashMap<>();
    private Map<Method, Set<String>> methodToEntity = new HashMap<>();

    private List<ClassReader> classReaders = new ArrayList<>();
    private Map<Jar, List<ClassReader>> jarClassReaders = new HashMap<>();
    public Set<String> testClassesFile = new HashSet<>();
    private Map<String, Set<Jar>> jarPathToJars = new HashMap<>();
    private Map<String, Set<String>> projectToClasses = new HashMap<>(); // not used?
    public Set<Method> methodsInProject = new HashSet<>();

    private ExecutorService pool;

    public MethodLevelDepsBuilder() {
        pool = Executors.newFixedThreadPool(Config.threads);
    }

    public DependencyResults build(Path codeClasses, Path testClasses, List<Jar> jars, boolean saveToDisk) {
        long start = System.currentTimeMillis();
        System.out.println(">>> getting project classes");
        getProject(codeClasses, true);
        getProject(testClasses, false);
        TimerUtil.log("Got project classes in " + (System.currentTimeMillis() - start) + " ms");

        start = System.currentTimeMillis();
        System.out.println(">>> getting libraries classes");
        getLibrary(jars, new HashSet<>());
        TimerUtil.log("Got libraries classes in " + (System.currentTimeMillis() - start) + " ms");
        System.out.println("testClassesFile: " + testClassesFile.size());

        start = System.currentTimeMillis();
        System.out.println(">>> building dependencies graph");
        buildDependenciesGraph();
        TimerUtil.log("Built dependencies graph in " + (System.currentTimeMillis() - start) + " ms");

        start = System.currentTimeMillis();
        System.out.println(">>> getting required instr files");
        Map<Jar, Set<String>> jarToClasses = getFilesToInstrument(jars);
        TimerUtil.log("Got jarToClasses in " + (System.currentTimeMillis() - start) + " ms");
        System.out.println("jarToClasses: " + jarToClasses.size());

        for (Map.Entry<Jar, Set<String>> entry : jarToClasses.entrySet()) {
            Jar jar = entry.getKey();
            Set<String> classes = entry.getValue();
            System.out.println("Jar " + jar + " contains " + classes.size() + " classes");
        }

        if (saveToDisk) {
            start = System.currentTimeMillis();
            Util.saveToDisk(jarToClasses, Util.getArtifactDir() + File.separator + "deps.iemop");
            System.out.println("Saving using " + Config.threads + " threads");
            saveGraphs(jars);
            TimerUtil.log("Saved all methodCIA metadata in " + (System.currentTimeMillis() - start) + " ms");
        }
        return new DependencyResults(jarToClasses, null);
    }

    public DependencyResults rebuild(Path codeClasses, Path testClasses, List<Jar> jars, boolean saveToDisk) {
        /*
         *  For now, we will rebuild the dependency graph from scratch.
         */
        long startRebuild = System.currentTimeMillis();
        System.out.println(">>> getting project classes");
        getProject(codeClasses, true);
        getProject(testClasses, false);
        TimerUtil.log("Got project classes in " + (System.currentTimeMillis() - startRebuild) + " ms");

        long start = System.currentTimeMillis();
        /*
        Map<String, StaticDependencies> oldDependencies = (Map<String, StaticDependencies>) Util.readFromDisk(Util.getArtifactDir() + File.separator + "graphs.iemop");
        if (oldDependencies == null) {
            System.err.println("Dependency graphs are invalid");
        } else {
            for (Jar jar : jars) {
                String entityID = jar.toString();
                if (oldDependencies.containsKey(entityID))
                    dependencies.put(entityID, oldDependencies.get(entityID));
            }
        }
         */
        System.out.println("Reading using " + Config.threads + " threads");
        readGraphs(jars);
        TimerUtil.log("Got " + dependencies.size() + " dependency graphs from disk in " + (System.currentTimeMillis() - start) + " ms");

        start = System.currentTimeMillis();
        System.out.println(">>> getting libraries classes");
        getLibrary(jars, dependencies.keySet());
        TimerUtil.log("Got libraries classes in " + (System.currentTimeMillis() - start) + " ms");
        System.out.println("testClassesFile: " + testClassesFile.size());

        start = System.currentTimeMillis();
        System.out.println(">>> building dependencies graph");
        buildDependenciesGraph();
        TimerUtil.log("Built dependencies graph in " + (System.currentTimeMillis() - start) + " ms");

        start = System.currentTimeMillis();
        System.out.println(">>> getting required instr files");
        Map<Jar, Set<String>> jarToClassesNew = getFilesToInstrument(jars);
        TimerUtil.log("Got jarToClasses in " + (System.currentTimeMillis() - start) + " ms");
        System.out.println("jarToClasses: " + jarToClassesNew.size());

        for (Map.Entry<Jar, Set<String>> entry : jarToClassesNew.entrySet()) {
            Jar jar = entry.getKey();
            Set<String> classes = entry.getValue();
            System.out.println("Jar " + jar + " contains " + classes.size() + " classes");
        }

        start = System.currentTimeMillis();
        Map<Jar, Set<String>> jarToClassesOld = (Map<Jar, Set<String>>) Util.readFromDisk(Util.getArtifactDir() + File.separator + "deps.iemop");
        TimerUtil.log("Read previous dependency map in " + (System.currentTimeMillis() - start) + " ms");

        DependencyResults result = new DependencyResults(jarToClassesNew, jarToClassesOld);
        if (saveToDisk) {
            // TODO: only save when test passed
            start = System.currentTimeMillis();
            Util.saveToDisk(jarToClassesNew, Util.getArtifactDir() + File.separator + "deps.iemop");
            System.out.println("Saving using " + Config.threads + " threads");
            saveGraphs(jars);
            TimerUtil.log("Saved all methodCIA metadata in " + (System.currentTimeMillis() - start) + " ms");
        }

        TimerUtil.log("Builder e2e time " + (System.currentTimeMillis() - startRebuild) + " ms");

        return result;
    }

    private void saveGraphs(List<Jar> jars) {
        long start = System.currentTimeMillis();
        try {
            Files.createDirectories(Paths.get(Util.getArtifactDir() + File.separator + "graphs"));
            List<Future> results = new ArrayList<>();
            for (Jar jar : jars) {
                String entityID = jar.getID();
                if (dependencies.containsKey(entityID) && !Files.exists(Paths.get(Util.getArtifactDir() + File.separator + "graphs" + File.separator + entityID + ".iemop"))) {
                    results.add(pool.submit(() -> {
                        Util.saveToDisk(dependencies.get(entityID), Util.getArtifactDir() + File.separator + "graphs" + File.separator + entityID + ".iemop");
                    }));
                }
            }

            for(Future future: results) { future.get(); }
        } catch (Exception e) {
            e.printStackTrace();
        }

        TimerUtil.log("Wrote " + jars.size() + " files to disk in " + (System.currentTimeMillis() - start) + " ms");
    }

    private void readGraphs(List<Jar> jars) {
        try {
            Files.createDirectories(Paths.get(Util.getArtifactDir() + File.separator + "graphs"));
            List<Future<StaticDependencies>> results = new ArrayList<>();
            List<String> jarID = new ArrayList<>();

            for (Jar jar : jars) {
                String entityID = jar.getID();
                if (Files.exists(Paths.get(Util.getArtifactDir() + File.separator + "graphs" + File.separator + entityID + ".iemop"))) {
                    results.add(pool.submit(() -> (StaticDependencies) Util.readFromDisk(Util.getArtifactDir() + File.separator + "graphs" + File.separator + entityID + ".iemop")));
                    jarID.add(entityID);
                }
            }

            int i = 0;
            for(Future<StaticDependencies> result : results) {
                StaticDependencies dep = result.get();
                if (dep == null) {
                    System.err.println("Unable to open " + jarID.get(i));
                } else {
                    dependencies.put(jarID.get(i), dep);
                }
                i += 1;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void buildDependenciesGraph() {
        long start = System.currentTimeMillis();
        buildClassToMethods();
        TimerUtil.log("Finished buildClassToMethods " + (System.currentTimeMillis() - start) + " ms");

        start = System.currentTimeMillis();
        buildMethodToMethods(); // <- this is the dependency graph
        TimerUtil.log("Finished buildMethodToMethods " + (System.currentTimeMillis() - start) + " ms");
    }

    private void buildClassToMethods() {
        StaticDependencies dependency = new StaticDependencies();
        for (ClassReader classReader : classReaders) {
            ClassToMethodsCollectorCV classToMethodsCollectorCV = new ClassToMethodsCollectorCV(
                    dependency.class2ContainedMethodNames, dependency.hierarchy_parents, dependency.hierarchy_children, null);
            classReader.accept(classToMethodsCollectorCV, ClassReader.SKIP_DEBUG);
            projectToClasses.computeIfAbsent("project", k -> new HashSet<>()).add(classToMethodsCollectorCV.mClassName);
        }
        dependencies.put("project", dependency);

        for (Jar jar : jarClassReaders.keySet()) {
            dependency = new StaticDependencies();
            ClassToMethodsCollectorCV classToMethodsCollectorCV = new ClassToMethodsCollectorCV(
                    dependency.class2ContainedMethodNames, dependency.hierarchy_parents, dependency.hierarchy_children);
            for (ClassReader classReader : jarClassReaders.get(jar))
                classReader.accept(classToMethodsCollectorCV, ClassReader.SKIP_DEBUG);

            projectToClasses.computeIfAbsent(jar.getID(), k -> new HashSet<>()).add(classToMethodsCollectorCV.mClassName);
            dependencies.put(jar.getID(), dependency);
        }
    }

    private void buildMethodToMethods() {
        // TODO: construct global class2ContainedMethodsName, hierarchy_parents, and hierarchy_children
        Map<String, Set<String>> hierarchy_parents = new HashMap<>();
        Map<String, Set<String>> hierarchy_children = new HashMap<>();
        Map<String, Set<String>> class2ContainedMethodNames = new HashMap<>();

        for (StaticDependencies dep : dependencies.values()) {
            for (Map.Entry<String, Set<String>> entry : dep.hierarchy_parents.entrySet()) {
                hierarchy_parents.computeIfAbsent(entry.getKey(), k -> new HashSet<>()).addAll(entry.getValue());
            }
            for (Map.Entry<String, Set<String>> entry : dep.hierarchy_children.entrySet()) {
                hierarchy_children.computeIfAbsent(entry.getKey(), k -> new HashSet<>()).addAll(entry.getValue());
            }
            for (Map.Entry<String, Set<String>> entry : dep.class2ContainedMethodNames.entrySet()) {
                class2ContainedMethodNames.computeIfAbsent(entry.getKey(), k -> new HashSet<>()).addAll(entry.getValue());
            }
        }

        StaticDependencies dependency = dependencies.get("project");
        for (ClassReader classReader : classReaders) {
            MethodCallCollectorCV methodCallCollectorCV = new MethodCallCollectorCV(dependency.methodName2MethodNames,
                    hierarchy_parents, hierarchy_children, class2ContainedMethodNames);
            classReader.accept(methodCallCollectorCV, ClassReader.SKIP_DEBUG);
        }

        List<Future<StaticDependencies>> results = new ArrayList<>();
        List<Jar> jars = new ArrayList<>(jarClassReaders.keySet());
        for (Jar jar : jars) {
            StaticDependencies deps = dependencies.get(jar.getID());
            results.add(pool.submit(() -> {
                MethodCallCollectorCV methodCallCollectorCV = new MethodCallCollectorCV(deps.methodName2MethodNames,
                        hierarchy_parents, hierarchy_children, class2ContainedMethodNames);

                for (ClassReader classReader : jarClassReaders.get(jar))
                    classReader.accept(methodCallCollectorCV, ClassReader.SKIP_DEBUG);

                return deps;
            }));
        }

        try {
            int i = 0;
            for(Future<StaticDependencies> result : results) {
                StaticDependencies dep = result.get();
                if (dep == null) {
                    System.err.println("Unable to get " + jars.get(i).toString());
                } else {
                    dependencies.put(jars.get(i).getID(), dep);
                }
                i += 1;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Map<Jar, Set<String>> getFilesToInstrument(List<Jar> jars) {
        // first we need to map method to a set of jar/project
        for (Map.Entry<String, StaticDependencies> entity : dependencies.entrySet()) {
            for (Method method : entity.getValue().methodName2MethodNames.keySet()) {
                methodToEntity.computeIfAbsent(method, k -> new HashSet<>()).add(entity.getKey());
            }
        }

        Set<Method> visited = new HashSet<>();
        Map<Jar, Set<String>> jarToClasses = new HashMap<>();
        for (Jar jar : jars) // so we know which jar is not used
            jarToClasses.put(jar, new HashSet<>());

        Stack<Method> stack = new Stack<>();
        for (Method method : dependencies.get("project").methodName2MethodNames.keySet()) {
            if (testClassesFile.contains(method.getClassName())) { // testClassesFile.contains(method.getClassName())
                stack.push(method);
            }
        }
        getDepsDFS(visited, stack);

        for (Method method : visited) {
            for (Jar jar : jarPathToJars.getOrDefault(method.getClassName(), new HashSet<>())) {
                jarToClasses.computeIfAbsent(jar, k -> new HashSet<>()).add(method.getClassName());
            }
        }
        return jarToClasses;
    }

    /*
    private void getDepsDFS(Method methodName, Set<Method> visitedMethods) {
        // need to check which dependency graph to visit
        if (!methodToEntity.containsKey(methodName))
            return;
        for (String entity : methodToEntity.get(methodName)) {
            for (Method method : dependencies.get(entity).methodName2MethodNames.get(methodName)) {
                if (!visitedMethods.contains(method)) {
                    visitedMethods.add(method);
                    getDepsDFS(method, visitedMethods);
                }
            }
        }
    }
     */

    private void getDepsDFS(Set<Method> visitedMethods, Stack<Method> stack) {
        while (!stack.isEmpty()) {
            Method currentMethod = stack.pop();

            if (!visitedMethods.contains(currentMethod)) {
                visitedMethods.add(currentMethod);

                if (methodToEntity.containsKey(currentMethod)) {
                    for (String entity : methodToEntity.get(currentMethod)) {
                        for (Method method : dependencies.get(entity).methodName2MethodNames.get(currentMethod)) {
                            if (!visitedMethods.contains(method)) {
                                stack.push(method);
                            }
                        }
                    }
                }
            }
        }
    }

    private void getProject(Path targetDir, boolean src) {
        try {
            Files.walkFileTree(targetDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.toString().endsWith(".class")) {
                        classReaders.add(new ClassReader(Files.newInputStream(file)));
                        String filename = targetDir.relativize(file).toString();
                        // remove .class from filename, and remove / from the beginning
                        if (!src)
                            testClassesFile.add(filename.substring(0, filename.length() - 6));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void getLibrary(List<Jar> jars, Set<String> excludedJars) {
        for (Jar jar : jars) {
            try {
                try (JarFile jarFile = new JarFile(jar.jarPath)) {
                    Enumeration<JarEntry> entries = jarFile.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String filename = entry.getName();
                        if (filename.endsWith(".class")) {
                            if (!excludedJars.contains(jar.getID())) {
                                jarClassReaders.computeIfAbsent(jar, k -> new ArrayList<>()).add(new ClassReader(jarFile.getInputStream(entry)));
                            }
                            jarPathToJars.computeIfAbsent(filename.substring(0, filename.length() - 6), k -> new HashSet<>()).add(jar);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
