package org.iemop.util.smethods;

import org.iemop.instrumentation.library.Jar;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DependencyResults {
    public Map<Jar, Set<String>> currentJarToFiles;
    public Map<Jar, Set<String>> oldJarToFiles;
    public Map<Jar, Set<String>> currentJarToNewFiles = new HashMap<>();

    public DependencyResults(Map<Jar, Set<String>> currentJarToFiles, Map<Jar, Set<String>> oldJarToFiles) {
        this.currentJarToFiles = currentJarToFiles;
        this.oldJarToFiles = oldJarToFiles;

        if (oldJarToFiles == null) {
            this.currentJarToNewFiles = currentJarToFiles;
            return;
        }

        for (Jar jar : currentJarToFiles.keySet()) {
            if (!oldJarToFiles.containsKey(jar)) {
                // we were not using this jar before
                currentJarToNewFiles.put(jar, currentJarToFiles.get(jar));
                continue;
            }

            // compute files in currentJarToFiles but not in oldJarToFiles
            Set<String> files = new HashSet<>();
            Set<String> oldFiles = oldJarToFiles.get(jar);
            for (String newFile : currentJarToFiles.get(jar)) {
                if (!oldFiles.contains(newFile)) {
                    // file is in new but not in old
                    files.add(newFile);
                }
            }
            currentJarToNewFiles.put(jar, files);
        }
    }

    public boolean hasOldResult() {
        return oldJarToFiles != null;
    }

    public boolean isUsingJar(Jar jar) {
        return this.currentJarToFiles.containsKey(jar) && !this.currentJarToFiles.get(jar).isEmpty();
    }

    public Set<String> getNewlyUsedFiles(Jar jar) {
        return this.currentJarToNewFiles.getOrDefault(jar, new HashSet<>());
    }

    public boolean hasNewlyUsedFiles() {
        for (Set<String> set : this.currentJarToNewFiles.values()) {
            if (!set.isEmpty()) {
                for (String file : set) {
                    // ignore classes like: $$EnhancerByMockitoWithCGLIB$$1b72259e, $MockitoMock$1899133663
                    if (!file.contains("EnhancerByMockito") && !file.contains("MockitoMock")) {
                        System.out.println("Newly used files: " + this.currentJarToNewFiles.values());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public Set<String> getUsedFiles(Jar jar) {
        return this.currentJarToFiles.getOrDefault(jar, new HashSet<>());
    }
}
