package org.iemop.util.instrumentation;

import org.iemop.util.IOUtil;
import org.iemop.util.Util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class BISMUtil extends InstrumentationTool {
    public static String[] getSpecs() {
        return new String[]{
                "Appendable_ThreadSafe.spec",
                "CharSequence_NotInMap.spec",
                "CharSequence_NotInSet.spec",
                "Closeable_MultipleClose.spec",
                "Collection_UnsafeIterator.spec",
                "Collection_UnsynchronizedAddAll.spec",
                "Collections_SortBeforeBinarySearch.spec",
                "Console_CloseReader.spec",
                "InputStream_ManipulateAfterClose.spec",
                "InputStream_MarkAfterClose.spec",
                "Iterator_HasNext.spec",
                "Iterator_RemoveOnce.spec",
                "ListIterator_RemoveOnce.spec",
                "Map_CollectionViewAdd.spec",
                "Map_ItselfAsKey.spec",
                "Map_ItselfAsValue.spec",
                "Map_UnsafeIterator.spec",
                "OutputStream_ManipulateAfterClose.spec",
                "Reader_ManipulateAfterClose.spec",
                "Set_ItselfAsElement.spec",
                "StringBuilder_ThreadSafe.spec",
                "System_NullArrayCopy.spec",
                "Throwable_InitCauseOnce.spec",
                "TreeMap_Comparable.spec"
        };
    }

    @Override
    public InstrumentationToolResult run(String[] command) {
        List<String> javaCommand = new ArrayList<>();
        javaCommand.add("java");
        javaCommand.addAll(Arrays.asList(command));

        System.out.println("Running " + javaCommand);

        ProcessBuilder builder = new ProcessBuilder(javaCommand);
        builder.redirectErrorStream(true);
        builder.directory(new File(Util.getBISMPath()));

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

    @Override
    public String[] getArgument(String classpath, String aspectPath, String inPath, String outPath, boolean isJar) {
        String specs = String.join(",", getSpecs());

        return new String[]{
                "-jar",
                "bism.jar",
                "specification=" + specs + ":target=" + inPath
        };
    }

    @Override
    public InstrumentationToolResult compile(String argument) {
        throw new UnsupportedOperationException();
    }

    // Create and extract jar file
    private boolean runJarCommand(String dest, String... command) {
        try {
            Path destDir = Paths.get(dest);
            IOUtil.createDirectories(destDir);

            List<String> cmd = new ArrayList<>(command.length + 1);
            cmd.add("jar");
            Collections.addAll(cmd, command);

            ProcessBuilder builder = new ProcessBuilder(cmd);
            builder.redirectErrorStream(true);
            builder.directory(new File(destDir.toString()));

            Process process = builder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            while ((reader.readLine()) != null) {} // consume input stream to prevent blocking
            return process.waitFor() == 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean createJarFile(String jarName, String src, String dest) {
        // run jar -cf
        return runJarCommand(dest, "-cf", jarName, "-C", src, ".");
    }

    private boolean extractJarFile(String jar, String dest) {
        // run jar -xf
        return runJarCommand(dest, "-xf", jar);
    }

    @Override
    public InstrumentationToolResult instrumentProject(String classpath, String aspectPath, String inPath, String outPath) {
        // First, convert inPath to jar
        String tmpDir = System.getProperty("java.io.tmpdir");
        String jarName = UUID.randomUUID() + ".jar";
        boolean success = createJarFile(jarName, inPath, tmpDir);
        if (!success) {
            System.out.println("Unable to create jar file (jarName: " + jarName + ", inPath: " + inPath + ", and tmpDir: " + tmpDir + ")");
            return new InstrumentationToolResult(false, null);
        }

        // Use BISM to instrument jar
        String inJar = tmpDir + File.separator + jarName;
        String instrumentedJar = inJar.substring(0, inJar.length() - 4) + "-instrumented.jar";
        InstrumentationToolResult result = run(getArgument(classpath, aspectPath, inJar, outPath, true));
        try {
            // DISCARD BISM'S ERROR
            if (!result.result) {
                result.result = true;
                Files.copy(Paths.get(inJar), Paths.get(instrumentedJar), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Issue in " + inPath + ": " + result.log);
            }

            if (result.result) {
                // Extract jar to outPath
                success = extractJarFile(instrumentedJar, outPath);
                if (!success) {
                    System.out.println("Unable to extract jar file (instrumentedJar: " + instrumentedJar + ", outPath: " + outPath + ")");
                    result.result = false;
                }
            }

            // Clean up
            Files.deleteIfExists(Paths.get(instrumentedJar));
            Files.deleteIfExists(Paths.get(inJar));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    @Override
    public InstrumentationToolResult instrumentJar(String classpath, String aspectPath, String inPath, String outPath) {
        Path instrumentedJar = Paths.get(inPath.substring(0, inPath.length() - 4) + "-instrumented.jar");
        InstrumentationToolResult result = run(getArgument(classpath, aspectPath, inPath, outPath, true));
        try {
            // DISCARD BISM'S ERROR
            if (!result.result) {
                result.result = true;
                Files.copy(Paths.get(inPath), instrumentedJar, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Issue in " + inPath + ": " + result.log);
            }

            if (result.result) {
                Files.move(instrumentedJar, Paths.get(outPath));
            } else {
                Files.deleteIfExists(instrumentedJar);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    @Override
    public InstrumentationToolResult instrumentExtractedJar(String jarPath, String classpath, String aspectPath, String inPath, String outPath) {
        // similar to instrumentProject
        return instrumentProject(classpath, aspectPath, inPath, outPath);
    }
}
