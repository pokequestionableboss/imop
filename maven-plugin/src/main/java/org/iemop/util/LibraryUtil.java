package org.iemop.util;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.iemop.instrumentation.library.Jar;

import java.util.ArrayList;
import java.util.List;

public class LibraryUtil {
    // Use POM changer
    // Or use extension, but make sure projects don't depend on them
    // Use mvn dependency to download jar

    private static List<Jar> jars = null;
    public static String[] excludeKeywords = {
            "org.eclipse.sisu.inject",
            "org.eclipse.sisu.plexus",
            "aspectjtools",
            "org/iemop",
            ".iemop",
            "org/apache/maven",
            "javax/inject",
            "google/inject",
            "javax/annotation",
            "codehaus/plexus",
            "junit/junit",
            "hamcrest/hamcrest-core",
            "projectlombok/lombok",
            "/lib/jvm/",
            "org.ekstazi.core",
            "asm-9.7",
            "commons-io-2.16.1"  // plugin dependency, don't instrument
    };

    public static List<Jar> getAllJar(MavenProject project) {
        if (jars != null)
            return jars;

        jars = new ArrayList<>();
        for (Artifact artifact : project.getArtifacts()) {
            String path = artifact.getFile().getAbsolutePath();
            if (shouldAddJar(path)) {
                if (artifact.hasClassifier()) {
                    jars.add(new Jar(path, artifact.getGroupId(), artifact.getArtifactId() + "-" + artifact.getClassifier(), artifact.getVersion()));
                } else {
                    jars.add(new Jar(path, artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion()));
                }
            }
        }

        return jars;
    }

    public static List<Jar> getAllJarWithoutFilters(MavenProject project) {
        List<Jar> jar = new ArrayList<>();
        for (Artifact artifact : project.getArtifacts()) {
            String path = artifact.getFile().getAbsolutePath();
            jar.add(new Jar(path, artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion()));
        }

        return jar;
    }

    public static boolean shouldAddJar(String path) {
        for (String keyword : excludeKeywords) {
            if (path.contains(keyword))
                return false;
        }

        return path.contains(".jar");
    }
}
