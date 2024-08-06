package org.iemop.util.dclasses;

import org.iemop.util.TimerUtil;
import org.iemop.util.Util;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DynamicTestRunner {

    public static boolean skipCIA = false;

    public static boolean run(String m2, String log) {
        long start = System.currentTimeMillis();
        System.out.println("Running test and saving log to " + log);

        List<String> SKIPS = Arrays.asList("-Dcheckstyle.skip", "-Drat.skip", "-Denforcer.skip", "-Danimal.sniffer.skip",
                "-Dmaven.javadoc.skip", "-Dfindbugs.skip", "-Dwarbucks.skip","-Dmodernizer.skip", "-Dimpsort.skip",
                "-Dpmd.skip", "-Dxjc.skip", "-Djacoco.skip", "-Dinvoker.skip", "-DskipDocs", "-DskipITs",
                "-Dmaven.plugin.skip", "-Dlombok.delombok.skip", "-Dlicense.skipUpdateLicense", "-Dremoteresources.skip");

        // mvn -Djava.io.tmpdir=${TMP_DIR} -Dmaven.repo.local=${REPO_DIR}-${run_variant} ${SKIP} -DargLine="-verbose:class" test
        List<String> commands = new ArrayList<>();
        commands.add("mvn");
        commands.add("-Djava.io.tmpdir=" + File.separator + "tmp" + File.separator + "iemop");
        commands.add("-Dmaven.repo.local=" + m2);
        commands.add("-DargLine=-verbose:class");
        commands.addAll(SKIPS);
        commands.add("test");

        ProcessBuilder builder = new ProcessBuilder(commands);
        builder.redirectErrorStream(true);

        List<String> output = new ArrayList<>();

        try {
            Process process = builder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine())!= null) {
                output.add(line);
            }

            boolean result = process.waitFor() == 0;

            try (PrintWriter writer = new PrintWriter(log, "UTF-8")) {
                for (String o : output)
                    writer.println(o);
            }

            TimerUtil.log("dynamicCIA finished running test with JVM flag in " + (System.currentTimeMillis() - start) + " ms");
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            TimerUtil.log("dynamicCIA failed to run test with JVM flag in " + (System.currentTimeMillis() - start) + " ms");
            return false;
        }
    }

    public static boolean requireTestRun(String classpath) {
        try {
            String file = Util.getDynamicCIAFile(true);
            String cpFile = Util.getDynamicCIAFile(false);

            if (Files.exists(Paths.get(file)) && Files.exists(Paths.get(cpFile))) {
                try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                    String savedClasspath = br.readLine();
                    System.out.println("Comparing classpath in file:");
                    System.out.println(savedClasspath);
                    System.out.println("To current classpath:");
                    System.out.println(classpath);
                    if (savedClasspath.equals(classpath)) {
                        System.out.println("Classpath didn't changed, don't need to rerun test");
                        skipCIA = true;
                        return false;
                    } else {
                        System.out.println("Classpath changed, need to rerun test");
                    }
                }
            }

            try (PrintWriter writer = new PrintWriter(file, "UTF-8")) {
                writer.println(classpath);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return true;
        }
    }
}
