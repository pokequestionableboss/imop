package org.iemop.util.dclasses;

import org.iemop.instrumentation.library.Jar;
import org.iemop.util.Config;
import org.iemop.util.TimerUtil;
import org.iemop.util.Util;
import org.iemop.util.smethods.DependencyResults;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DynamicDepsBuilder {
    public static Map<Jar, Set<String>> forceInclude = null;

    public static Map<Jar, Set<String>> getForceInclude() {
        if (forceInclude == null) {
            if (Files.exists(Paths.get(Util.getArtifactDir() + File.separator + "classes-loaded-only-with-mop.iemop"))) {
                forceInclude = (Map<Jar, Set<String>>) Util.readFromDisk(Util.getArtifactDir() + File.separator + "classes-loaded-only-with-mop.iemop");
            }

            if (forceInclude == null) {
                forceInclude = new HashMap<>();
            }
        }

        return forceInclude;
    }

    public DependencyResults build(String logFilename, List<Jar> jars, boolean saveToDisk, boolean firstRun) {
        long startBuilder = System.currentTimeMillis();
        System.out.println("Checking loaded classes using log " + logFilename);

        List<String> lines;
        try {
            lines = Files.readAllLines(Paths.get(logFilename));
        } catch(Exception e) {
            String loadedClassesDumpStream = Util.getDynamicCIAFile(false) + ".loaded";
            if (Files.exists(Paths.get(loadedClassesDumpStream)))
                return build(loadedClassesDumpStream, jars, saveToDisk, firstRun);

            e.printStackTrace();
            lines = new ArrayList<>();
        }

        Map<String, Jar> pathToJar = new HashMap<>();
        for (Jar jar : jars) {
            pathToJar.put(jar.jarPath, jar);
        }

        Map<Jar, Set<String>> jarToClasses = new HashMap<>();

        Pattern pattern = Pattern.compile("\\[Loaded (.*) from file:(.*)\\]");
        boolean matched = false;
        for (String line : lines) {
            Matcher match = pattern.matcher(line);
            if (match.find()) {
                matched = true;

                String className = match.group(1);
                String jarPath = match.group(2);
                if (pathToJar.containsKey(jarPath)) {
                    jarToClasses.computeIfAbsent(pathToJar.get(jarPath), k -> new HashSet<>()).add(className.replace(".", "/"));
                }
            } else if (!matched && (line.contains("directly writing to native stream in forked") || line.contains("Corrupted stdin stream in forked JVM"))) {
                for (String word : line.split(" ")) {
                    if (word.endsWith(".dumpstream")) {
                        if (Config.lazy) {
                            // copy word to artifact directory, otherwise mvn clean will clean the loaded classes list
                            try {
                                String loadedClassesDumpStream = Util.getDynamicCIAFile(false) + ".loaded";
                                if (!Files.exists(Paths.get(word)))
                                    // dumpstream doesn't exit (e.g., mvn cleaned), check .loaded directly
                                    return build(loadedClassesDumpStream, jars, saveToDisk, firstRun);

                                Files.copy(Paths.get(word), Paths.get(loadedClassesDumpStream), StandardCopyOption.REPLACE_EXISTING);
                                System.out.println("Backing up " + word + " to " + loadedClassesDumpStream + " for future run");
                            } catch (Exception e) { e.printStackTrace(); }
                        }
                        return build(word, jars, saveToDisk, firstRun);
                    }
                }
            }
        }
        
        try {
            for (Map.Entry<Jar, Set<String>> entry : getForceInclude().entrySet()) {
                if (jarToClasses.containsKey(entry.getKey())) {
                    jarToClasses.get(entry.getKey()).addAll(entry.getValue());
                } else{
                    if (pathToJar.containsKey(entry.getKey().jarPath)) {
                        jarToClasses.put(entry.getKey(), new HashSet<>(entry.getValue()));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
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
            TimerUtil.log("Saved all dynamicCIA metadata in " + (System.currentTimeMillis() - start) + " ms");
        }
        
        return res;
    }
}
