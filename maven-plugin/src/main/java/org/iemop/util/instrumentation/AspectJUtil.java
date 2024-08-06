package org.iemop.util.instrumentation;

import org.iemop.instrumentation.library.Jar;
import org.iemop.instrumentation.library.repair.DeleteClassesFromLog;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AspectJUtil extends InstrumentationTool {
    public InstrumentationToolResult run(String[] command) {
        List<String> ajcCommand = new ArrayList<>();
        ajcCommand.add("ajc");
        ajcCommand.addAll(Arrays.asList(command));

        ProcessBuilder builder = new ProcessBuilder(ajcCommand);
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
            return new InstrumentationToolResult(result, output);
        } catch (Exception e) {
            e.printStackTrace();
            return new InstrumentationToolResult(false, null);
        }
    }

    /**
     * Compile a single .aj file using AspectJ
     */
    public InstrumentationToolResult compile(String file) {
        return this.run(new String[]{file});
    }

    /**
     * Generate argument for ajc, inputs are classpath, path to myaspects.jar, input path, output path, and isJar flag
     * if isJar is true, then input and output paths are files
     * if isJar is false, then input and output paths are directories
     */
    public String[] getArgument(String classpath, String aspectPath, String inPath, String outPath, boolean isJar) {
        return new String[]{
                "-Xlint:ignore",
                "-1.8",
                "-encoding", "UTF-8",
                "-classpath", classpath,
                "-aspectpath", aspectPath,
                "-inpath", inPath,
                isJar ? "-outjar" : "-d", outPath
        };
    }

    /**
     * Given classpath, path to myaspects.jar, input directory inPath, and destination directory outPath
     * instrument the project
     */
    public InstrumentationToolResult instrumentProject(String classpath, String aspectPath, String inPath, String outPath) {
        return this.run(getArgument(classpath, aspectPath, inPath, outPath, false));
    }

    /**
     * Given classpath, path to myaspects.jar, input jar file inPath, and output jar file outPath
     * instrument the project
     */
    public InstrumentationToolResult instrumentJar(String classpath, String aspectPath, String inPath, String outPath) {
        return this.run(getArgument(classpath, aspectPath, inPath, outPath, true));
    }

    /**
     * Given jar file jarPath, classpath, path to myaspects.jar, input directory inPath, and output directory outPath
     * instrument the project
     */
    public InstrumentationToolResult instrumentExtractedJar(String jarPath, String classpath, String aspectPath, String inPath, String outPath) {
        String[] args = getArgument(classpath, aspectPath, inPath, outPath, false);
        InstrumentationToolResult result = this.run(args);

        if (!result.result) {
            System.out.println("Failed to instrument " + jarPath + ", repairing");
            // Repair
            if (DeleteClassesFromLog.delete(result.log, inPath, false)) {
                System.out.println("Deleted classes from dir " + jarPath);
                result = this.run(args);
                System.out.println("Re-instrumentation result: " + result.result);
                System.out.println("Re-instrument log:");
                System.out.println(result.log);
            }
        }

        return result;
    }
}
