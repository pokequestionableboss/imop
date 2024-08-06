package org.iemop.instrumentation.library;

import org.iemop.util.instrumentation.AspectJUtil;
import org.iemop.util.IOUtil;
import org.iemop.util.instrumentation.InstrumentationTool;
import org.iemop.util.instrumentation.InstrumentationToolResult;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.HashMap;
import java.util.stream.Stream;

public class InstrumentJar {
    private final String jarPath;
    private final String classpath;
    public InstrumentJar(String jarPath, String classpath) {
        this.jarPath = jarPath;
        this.classpath = classpath;
    }

    public InstrumentationToolResult instrument(String aspectPath, String dest) {
        return InstrumentationTool.getInstance().instrumentJar(this.classpath, aspectPath, this.jarPath, dest);
        /*
        try {
            String[] arguments = getAJCArguments(aspectPath, dest);
            Main compiler = new Main();
            MessageHandler mh = new MessageHandler();
            compiler.run(arguments, mh);
            IMessage[] errors = mh.getErrors();
            return errors.length == 0;
        } catch (Exception e) {
            return false;
        }
        */
    }

    public static void patchKnownIssues(String jarPath) {
        URI jarFile = URI.create("jar:file:" + jarPath);
        try (FileSystem fs = FileSystems.newFileSystem(jarFile, new HashMap<String, String>())) {
            // Delete multi-release jar
            // zip -d "${JAR}" "META-INF/versions/*"
            Path targetPath = fs.getPath("META-INF/versions");
            IOUtil.deleteDirectory(targetPath);

            // Delete signature
            // zip -d ${JAR} "META-INF/*.SF" "META-INF/*.DSA" "META-INF/*.RSA" "META-INF/*.EC"
            try (Stream<Path> paths = Files.walk(fs.getPath("META-INF"), 1)) {
//                PathMatcher matcher = fs.getPathMatcher("glob:META-INF/*.{SF,DSA,RSA,EC}");
                paths
//                    .filter(matcher::matches)  // Only works on macOS
                    .forEach(p -> {
                        try {
                            String f = p.toString();
                            if (f.endsWith(".SF") || f.endsWith(".DSA") || f.endsWith(".RSA") || f.endsWith(".EC")) {
                                System.out.println("DELETE signature file " + f + " in " + jarPath);
                                Files.delete(p);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
