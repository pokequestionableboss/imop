package org.iemop.util;

import org.ekstazi.hash.Hasher;
import org.iemop.instrumentation.InstrumentationLocation;
import org.iemop.util.instrumentation.BISMUtil;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

import static org.iemop.util.Constants.*;

public class Util {

    private static String artifactDirectory;
    private static boolean firstRun;
    public static String classpath;
    public static String m2;

    private static List<InstrumentationLocation> fileMapping;

    public static void setArtifactDir(String projectDir) {
        Util.artifactDirectory = projectDir + File.separator + IEMOP_DIR;
    }
    public static String getArtifactDir() {
        return artifactDirectory;
    }
    public static String getResourceDir() {
        return getArtifactDir() + File.separator + "resources";
    }

    public static String getUninstrumentedDir() {
        return getArtifactDir() + File.separator + "uninstrumented";
    }

    public static String getInstrumentedDir() {
        return getArtifactDir() + File.separator + "instrumented";
    }

    public static String getPendingDir() {
        return getArtifactDir() + File.separator + "pending";
    }

    public static String getUninstrumentedProjectDir() {
        return getUninstrumentedDir() + File.separator + "project";
    }

    public static String getUninstrumentedLibraryDir() {
        return getUninstrumentedDir() + File.separator + "library";
    }

    public static String getInstrumentedProjectDir() {
        return getInstrumentedDir() + File.separator + "project";
    }

    public static String getInstrumentedLibraryDir() {
        return getInstrumentedDir() + File.separator + "library";
    }

    public static String getJarDir() {
        return getArtifactDir() + File.separator + "jar";
    }

    public static String getWorkspaceDir() {
        return getArtifactDir() + File.separator + "workspace";
    }

    private static Path getFirstRunFile() {
        return Paths.get(getArtifactDir() + File.separator + "firstRun");
    }

    public static String getDynamicCIAFile(boolean isClasspath) {
        return getArtifactDir() + File.separator +  (isClasspath ? "dynamic.txt" : "dynamic.log");
    }

    public static String getM2Repo() {
        return m2;
    }

    public static boolean isFirstRun() {
        return firstRun;
    }

    public static void setFirstRun(boolean createFirstRunFile) {
        try {
            if (Files.exists(getFirstRunFile())) {
                firstRun = false;
            } else {
                firstRun = true;
                if (createFirstRunFile)
                    Files.createFile(getFirstRunFile());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void copyResources(boolean stats, boolean bism) {
        try {
            URI resource = getClass().getResource("").toURI();
            FileSystem fs = FileSystems.newFileSystem(resource, new HashMap<String, String>());

            Path resourcesPath = Paths.get(Util.getResourceDir());
            IOUtil.createDirectories(resourcesPath);

            Path aspectPath = Paths.get(getAspectPath());
            if (!Files.exists(aspectPath) || stats) {
                Files.copy(fs.getPath(stats ? STATS_ASPECTS_NAME : DEFAULT_ASPECTS_NAME), aspectPath, StandardCopyOption.REPLACE_EXISTING);
            }

            Path rvmPath = Paths.get(getRVMPath());
            if (!Files.exists(rvmPath)) {
                Files.copy(fs.getPath(RVM_NAME), rvmPath, StandardCopyOption.REPLACE_EXISTING);
            }

            Path aspectjRTPath = Paths.get(getAspectjRTPath());
            if (!Files.exists(aspectjRTPath)) {
                Files.copy(fs.getPath(ASPECTJ_RT_NAME), aspectjRTPath, StandardCopyOption.REPLACE_EXISTING);
            }

            if (bism) {
                IOUtil.createDirectories(Paths.get(getBISMPath()));

                Path bismPath = Paths.get(getBISMPath() + File.separator + BISM_JAR_NAME);
                if (!Files.exists(bismPath)) {
                    Files.copy(fs.getPath(BISM_NAME + File.separator + BISM_JAR_NAME), bismPath, StandardCopyOption.REPLACE_EXISTING);
                }

                for (String spec : BISMUtil.getSpecs()) {
                    Path specPath = Paths.get(getBISMPath() + File.separator + spec);
                    if (!Files.exists(specPath)) {
                        Files.copy(fs.getPath(BISM_NAME + File.separator + "specs" + File.separator + spec), specPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getClasspath() {
        String rvmPath = Util.getResourceDir() + File.separator + RVM_NAME;
        String aspectjRTPath = Util.getResourceDir() + File.separator + ASPECTJ_RT_NAME;

        return classpath + File.pathSeparator + rvmPath + File.pathSeparator + aspectjRTPath;
    }

    public static String getAspectPath() {
        return Util.getResourceDir() + File.separator + DEFAULT_ASPECTS_NAME;
    }

    public static String getRVMPath() {
        return Util.getResourceDir() + File.separator + RVM_NAME;
    }

    public static String getAspectjRTPath() {
        return Util.getResourceDir() + File.separator + ASPECTJ_RT_NAME;
    }

    public static String getBISMPath() {
        return Util.getResourceDir() + File.separator + BISM_NAME;
    }

    public static void saveMapping(List<InstrumentationLocation> fileMapping) {
        Util.fileMapping = fileMapping;
        saveToDisk(fileMapping, getArtifactDir() + File.separator + "mapping.iemop");
    }

    public static List<InstrumentationLocation> readMapping() {
        if (Util.fileMapping == null) {
            Object result = readFromDisk(getArtifactDir() + File.separator + "mapping.iemop");
            if (result != null) {
                Util.fileMapping = (List<InstrumentationLocation>) result;
            }
        }

        if (Util.fileMapping == null) {
            Util.fileMapping = new ArrayList<>();
        }

        return Util.fileMapping;
    }

    public static void saveToDisk(Object object, String path) {
        try {
            System.out.println(">>> saving file to " + path);

            FileOutputStream fos = new FileOutputStream(path);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(object);
            oos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Object readFromDisk(String path) {
        if (!Files.exists(Paths.get(path))) {
            System.out.println(">>> reading file " + path + ", but not exists");
            return null;
        }

        System.out.println(">>> reading file " + path);

        try {
            FileInputStream fis = new FileInputStream(path);
            ObjectInputStream ois = new ObjectInputStream(fis);
            Object object = ois.readObject();
            ois.close();
            return object;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    public static void deleteMapping() {
        try {
            Files.deleteIfExists(Paths.get(getArtifactDir() + File.separator + "mapping.iemop"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<String> diff(String a, String b) {
        List<String> cmd = Arrays.asList("diff", "-qrs", a, b);

        ProcessBuilder builder = new ProcessBuilder(cmd);
        builder.redirectErrorStream(true);

        List<String> output = new ArrayList<>();

        try {
            Process process = builder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine())!= null) {
                output.add(line);
            }

            return output;
        } catch (Exception e) {
            e.printStackTrace();
            return output;
        }
    }
}
