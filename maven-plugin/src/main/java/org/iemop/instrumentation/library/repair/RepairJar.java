package org.iemop.instrumentation.library.repair;

import org.iemop.util.instrumentation.AspectJUtil;
import org.iemop.util.Util;
import org.iemop.util.instrumentation.InstrumentationTool;
import org.iemop.util.instrumentation.InstrumentationToolResult;

import java.io.File;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.file.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.iemop.util.Constants.DEFAULT_ASPECTS_NAME;

public class RepairJar {
    public static String repair(String jar, List<String> errorLog) {
        // Get BaseAspect.aj
        String baseAspect = GenerateBaseAspectFromLog.generate(errorLog);
        if (baseAspect == null) {
            System.out.println("No Base Aspect Generated");
            return "";
        }

        String dirName = Util.getArtifactDir() + File.separator + Paths.get(jar).getFileName().toString().replace(".", "-");
        Path aspectsForJar = Paths.get(dirName + File.separator + DEFAULT_ASPECTS_NAME);
        try {
            Files.createDirectories(Paths.get(dirName));
            Files.copy(Paths.get(Util.getAspectPath()), aspectsForJar);

            // Save BaseAspect.aj to tmp dir
            PrintWriter writer = new PrintWriter(dirName + File.separator + "BaseAspect.aj", "UTF-8");
            writer.print(baseAspect);
            writer.close();
        } catch (Exception e) {
            System.out.println("Exception in RepairJar");
            e.printStackTrace();
            return "";
        }

        // Compile BaseAspect.aj
        InstrumentationToolResult result = InstrumentationTool.getInstance().compile(dirName + File.separator + "BaseAspect.aj");
        if (!result.result) {
            System.out.println("Unable to compile BaseAspect");
            System.out.println(result.log);
            return "";
        }

        // Replace BaseAspect.class in jar to the BaseAspect.class in tmp dir
        Map<String, String> env = new HashMap<>();
        env.put("create", "true");
        URI jarFile = URI.create("jar:file:" + aspectsForJar);
        try (FileSystem jarfs = FileSystems.newFileSystem(jarFile, env)) {
            Path newFile = Paths.get(dirName + File.separator + "BaseAspect.class");
            Path pathInJarFile = jarfs.getPath("mop" + File.separator + "BaseAspect.class");
            Files.copy(newFile, pathInJarFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            System.out.println("Exception while replacing BaseAspect");
            e.printStackTrace();
            return "";
        }
        return aspectsForJar.toString();
    }
}
