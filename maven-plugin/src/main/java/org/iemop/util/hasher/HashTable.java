package org.iemop.util.hasher;

import org.ekstazi.hash.Hasher;
import org.iemop.instrumentation.library.Jar;
import org.iemop.util.Config;
import org.iemop.util.TimerUtil;
import org.iemop.util.Util;
import org.iemop.util.smethods.MethodCallCollectorCV;
import org.iemop.util.smethods.StaticDependencies;
import org.objectweb.asm.ClassReader;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class HashTable {
    private static HashTable instance = null;

    /*
        jar's groupId-artifactId -> {
            relative-path -> {
                checksum -> path-to-instrumented-bytecode
            }
        }
        Example:
        {
            com.foo-bar: {
                com/foo/bar/A.class -> {
                    checksum1: path/to/instrumented/uno.class,
                    checksum2: path/to/instrumented/dos.class
                },
                com/foo/bar/B.class -> {
                    checksum3: path/to/instrumented/tres.class
                }
            },
            com.foo-baz: {
                com/foo/baz/A.class {
                    checksum4: path/to/instrumented/cuatro.class
                },
                com/foo/baz/B.class {
                    checksum5: path/to/instrumented/cinco.class,
                    checksum6: path/to/instrumented/seis.class
                }
            }
        }
     */
    public Map<String, Map<String, Map<String, String>>> hashTable;
    private ExecutorService pool;

    public static HashTable getInstance() {
        if (instance == null)
            instance = new HashTable();
        return instance;
    }

    private HashTable() {
        Map<String, Map<String, Map<String, String>>> table = (Map<String, Map<String, Map<String, String>>>) Util.readFromDisk(Util.getArtifactDir() + File.separator + "hashes.iemop");
        if (table == null) {
            hashTable = new HashMap<>();
        } else {
            hashTable = table;
        }

        pool = Executors.newFixedThreadPool(Config.threads);
    }

    public synchronized void save() {
        Util.saveToDisk(hashTable, Util.getArtifactDir() + File.separator + "hashes.iemop");
    }

    /**
     * Given jar, merge path2checksum and path2instrumented into hashTable
     * recondition: if key in path2checksum then it must be in path2instrumented as well
     */
    public synchronized void addChecksum(Jar jar, Map<String, String> path2checksum, Map<String, String> path2instrumented) {
        String jarID = jar.groupId + "-" + jar.artifactId;
        Map<String, Map<String, String>> path2map = hashTable.computeIfAbsent(jarID, k -> new HashMap<>());
        for (String path : path2checksum.keySet()) {
            Map<String, String> checksum2instrumented = path2map.computeIfAbsent(path, k -> new HashMap<>());
            checksum2instrumented.put(path2checksum.get(path), path2instrumented.get(path));
        }
    }

    /**
     * Given a jar, return its checksum map
     */
    public synchronized Map<String, Map<String, String>> getChecksum(Jar jar) {
        String jarID = jar.groupId + "-" + jar.artifactId;
        if (hashTable.containsKey(jarID))
            // TODO: not sure if this is thread safe or not
            return new HashMap<>(hashTable.get(jarID));
        return new HashMap<>();
    }

    public void computeHashes(Jar jar, Path pathToUninstrumented, Path pathToInstrumented) {
        Map<String, String> path2checksum = new HashMap<>();
        Map<String, String> path2instrumented = new HashMap<>();
        Hasher hasher = new Hasher(Hasher.Algorithm.CRC32, 1000, true);

        try {
            Files.walkFileTree(pathToUninstrumented, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.toString().endsWith(".class")) {
                        Path relative = pathToUninstrumented.relativize(file);
                        Path instrumented = pathToInstrumented.resolve(relative);
                        String checksum = hasher.hashURL("file:" + file);

                        path2checksum.put(relative.toString(), checksum);
                        path2instrumented.put(relative.toString(), instrumented.toString());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) { e.printStackTrace(); }

        addChecksum(jar, path2checksum, path2instrumented);
    }

    public void computeHashes(Jar jar, Path pathToUninstrumented, Path pathToInstrumented, Set<String> relativePathFiles) {
        Map<String, String> path2checksum = new HashMap<>();
        Map<String, String> path2instrumented = new HashMap<>();
        Hasher hasher = new Hasher(Hasher.Algorithm.CRC32, 1000, true);

        for (String relativePathFile : relativePathFiles) {
            String absolutePath = pathToUninstrumented.resolve(relativePathFile).toString();
            Path instrumented = pathToInstrumented.resolve(relativePathFile);
            String checksum = hasher.hashURL("file:" + absolutePath);

            path2checksum.put(relativePathFile, checksum);
            path2instrumented.put(relativePathFile, instrumented.toString());
        }

        addChecksum(jar, path2checksum, path2instrumented);
    }

    public Map<String, String> computeImpacted(Jar jar, Path pathToUninstrumented, Set<String> fileToCheckSet) {
        if (Config.hashesSingleThread)
            return computeImpactedSingleThread(jar, pathToUninstrumented, fileToCheckSet);

        long start = System.currentTimeMillis();
        Map<String, String> result = new HashMap<>(); // map file to instrumented location, null if not instrumented
        Hasher hasher = new Hasher(Hasher.Algorithm.CRC32, 1000, true);
        Map<String, Map<String, String>> checksums = getChecksum(jar);

        List<String> fileToCheck = new ArrayList<>(fileToCheckSet);
        List<Future<String>> results = new ArrayList<>();

        for (String file : fileToCheck) {
            String classFile = file + ".class";

            results.add(pool.submit(() -> {
                if (!checksums.containsKey(classFile)) {
                    return null;
                }

                String filePath = pathToUninstrumented.resolve(Paths.get(classFile)).toString();
                String checksum = hasher.hashURL("file:" + filePath);
                return checksums.get(classFile).getOrDefault(checksum, null);
            }));
        }

        try {
            int i = 0;
            for (Future<String> path : results) {
                result.put(fileToCheck.get(i) + ".class", path.get());
                i += 1;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        TimerUtil.log("Cleaned bytecode for " + fileToCheck.size() + " files - " + jar + " in " + (System.currentTimeMillis() - start) + " ms");
        return result;
    }

    // Single thread implementation
    public Map<String, String> computeImpactedSingleThread(Jar jar, Path pathToUninstrumented, Set<String> fileToCheck) {
        long start = System.currentTimeMillis();
        Map<String, String> result = new HashMap<>(); // map file to instrumented location, null if not instrumented
        Hasher hasher = new Hasher(Hasher.Algorithm.CRC32, 1000, true);
        Map<String, Map<String, String>> checksums = getChecksum(jar);

        for (String file : fileToCheck) {
            String classFile = file + ".class";
            if (!checksums.containsKey(classFile)) {
                // never instrumented the path before
                result.put(classFile, null);
                continue;
            }

            // instrumented the path before, check if we instrumented the file before or not
            String filePath = pathToUninstrumented.resolve(Paths.get(classFile)).toString();
            String checksum = hasher.hashURL("file:" + filePath);
            result.put(classFile, checksums.get(classFile).getOrDefault(checksum, null));
        }

        TimerUtil.log("Cleaned bytecode for " + jar + " in " + (System.currentTimeMillis() - start) + " ms");
        return result;
    }
}
