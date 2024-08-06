package org.iemop.util;

import org.iemop.util.smethods.DependencyResults;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class IOUtil {
    /**
     * Delete directory targetPath inside a file system
     * Works for normal directory or directory in jar
     */
    public static void deleteDirectory(Path targetPath) throws IOException {
        if (Files.exists(targetPath)) {
            Files.walkFileTree(targetPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                        throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    /**
     * This method is used to delete a file or directory.
     * If the file is a directory, it recursively deletes all files and subdirectories within it.
     * After all files and subdirectories within the directory are deleted, the directory itself is deleted.
     * If the file is not a directory, it is directly deleted.
     */
    public static void deleteFile(File file) {
        if (file.isDirectory()) {
            for (File inner : file.listFiles()) {
                deleteFile(inner);
            }
        }
        file.delete();
    }

    /**
     * If file is in src but not in dest, copy it to dest
     */
    public static void syncDirectoryCopyNew(Path src, Path dest) throws IOException {
        if (!Files.exists(src))
            return;

        Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path pathInDest = dest.resolve(src.relativize(file));
                // Copy everytime, don't check for !Files.exists(pathInDest)
                // otherwise, if resources files are modified, it will NOT copy, which can cause test failure...
                // another solution is to check content are equal or not, not exists or equal then copy
                if (!file.toString().endsWith(".class")) {
                    System.out.println("Copying from " + file + " to " + pathInDest);

                    IOUtil.createDirectories(pathInDest.getParent());
                    Files.copy(file, pathInDest, StandardCopyOption.REPLACE_EXISTING);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Add base classes to pendingInstrumentFiles set
     * For example, if file is target/classes/hello/world/A$B.class
     * Then add target/classes/hello/world/A*.class to pendingInstrumentFiles
     * If the file is target/classes/hello/world/A.class
     * Then add target/classes/hello/world/A$*.class to pendingInstrumentFiles
     * Precondition: file must ends with .class
     */
    // TODO: need to optimize this...
    public static void findAllBaseClasses(Set<Path> pendingInstrumentFiles, Path file) {
        String filename = file.getFileName().toString();
        // If filename contains $, don't add $, add $ otherwise
        String prefix = filename.contains("$") ? filename.substring(0, filename.indexOf("$")) : (filename.substring(0, filename.length() - 6) + "$");
        // filename.substring(0, filename.length() - 6) will remove .class from filename

        try {
            try (Stream<Path> stream = Files.list(file.getParent())) {
                stream
                    .filter(f -> {
                        String name = f.getFileName().toString();
                        return name.startsWith(prefix) && name.endsWith(".class");
                    })
                    .forEach(pendingInstrumentFiles::add);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * If file is not in src but is in dest, delete it from dest
     */
    public static void syncDirectoryDeleteOld(Path src, Path dest) throws IOException {
        Files.walkFileTree(dest, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path pathInSrc = src.resolve(dest.relativize(file));
                if (!Files.exists(pathInSrc)) {
                    System.out.println("File " + file + " in dest but not in src " + src + ", delete it");
                    Files.delete(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * This method is used to synchronize two directories by deleting old files.
     * It walks through the first destination directory and checks if each file exists in the source directory.
     * If a file exists in the first destination directory but not in the source directory, it is considered as an old file.
     * The old file is then deleted from both the first and the second destination directories.
     */
    public static void syncDirectoryDeleteOld(Path src, Path dest1, Path dest2) throws IOException {
        Files.walkFileTree(dest1, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path pathInSrc = src.resolve(dest1.relativize(file));
                if (!Files.exists(pathInSrc)) {
                    System.out.println("File " + file + " in dest1 but not in src " + src + ", delete it");
                    Files.deleteIfExists(file);

                    Path pathInDest2 = dest2.resolve(dest1.relativize(file));
                    if (Files.exists(pathInDest2)) {
                        System.out.println("File " + pathInDest2 + " in dest2 but not in src " + src + ", delete it");
                        Files.deleteIfExists(pathInDest2);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void createDirectories(Path dir) {
        if (Files.exists(dir))
            return;

        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Set<Path> copyFilesToDestString(Set<String> relativePathFiles, Path srcBase, Path destBase) { // files are relative paths
        if (relativePathFiles.isEmpty())
            return new HashSet<>();

        Set<Path> destFiles = new HashSet<>();

        for (String file : relativePathFiles) {
            Path src = srcBase.resolve(file);
            Path dest = destBase.resolve(file);
            try {
                Files.createDirectories(dest.getParent());
                Files.copy(src, dest);
                destFiles.add(dest);
            } catch (Exception e) { e.printStackTrace(); return null; }
        }

        return destFiles;
    }

    public static boolean copyFilesToDest(Set<Path> absolutePathFiles, Path srcBase, Path destBase) { // files are absolute paths, all have base path `srcBase`
        if (absolutePathFiles.isEmpty())
            return true;

        for (Path file : absolutePathFiles) {
            Path dest = destBase.resolve(srcBase.relativize(file));
            try {
                Files.createDirectories(dest.getParent());
                Files.copy(file, dest);
            } catch (Exception e) { e.printStackTrace(); return false; }
        }

        return true;
    }

    /*
    public static boolean copyFilesToDest(Set<Path> files, Path srcBase, Path destBase, DependencyResults dependency) {
        if (files.isEmpty())
            return true;

        if (dependency == null)
            return copyFilesToDest(files, srcBase, destBase);

        // TODO: FIX
        for (String usedClass : dependency.getNewlyUsedFiles(jar)) {
            // If the project is using the file, i.e., in `included`, but the file didn't change, i.e., not in `files`, then don't reinstrument it
            // TODO: problem: what if file didn't change, but we didn't use it in the past (not instrumented), but we are using it now?
            Path src = srcBase.resolve(usedClass + ".class");
            if (files.contains(src)) {
                Path dest = destBase.resolve(usedClass + ".class");
                try {
                    Files.createDirectories(dest.getParent());
                    Files.copy(src, dest);
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            } else {
                System.out.println(usedClass + " not found in extracted files");
                return false;
            }
        }
//        for (Path file : files) {
//            Path src = tmpDirForCurrent.resolve(usedClass + ".class");
//
//            Path dest = destBase.resolve(srcBase.relativize(file));
//            try {
//                Files.createDirectories(dest.getParent());
//                Files.copy(file, dest);
//            } catch (Exception ignored) { return false; }
//        }

        return true;
    }
     */

    public static Set<Path> extractJar(Path jar, Path dest) {
        Set<Path> extracted = new HashSet<>();

        try {
            if (Files.exists(dest)) {
                Files.walkFileTree(dest, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        extracted.add(file);
                        return FileVisitResult.CONTINUE;
                    }
                });
                return extracted;
            }

            Files.createDirectories(dest);

            URI jarFile = URI.create("jar:file:" + jar);
            try (FileSystem fs = FileSystems.newFileSystem(jarFile, new HashMap<String, String>())) {
                Path jarRoot = fs.getPath("/");
                Files.walkFileTree(jarRoot, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Path pathInTmp = dest.resolve(jarRoot.relativize(file).toString());
                        Files.copy(file, pathInTmp, StandardCopyOption.REPLACE_EXISTING);
                        extracted.add(pathInTmp);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        Path pathInTmp = dest.resolve(jarRoot.relativize(dir).toString());
                        Files.createDirectories(pathInTmp);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return extracted;
    }

    /**
     * Add files in targetPath to classesPath or nonClassesPath
     */
    public static void addFilesInDirectory(Path targetPath, Set<Path> classesPath, Set<Path> nonClassesPath) throws IOException {
        Files.walkFileTree(targetPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".class"))
                    classesPath.add(file);
                else
                    nonClassesPath.add(file);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void copyFilesToJar(Path src, Path jar) {
        if (!Files.exists(src))
            return;

        try {
            try (FileSystem fs = FileSystems.newFileSystem(URI.create("jar:file:" + jar), new HashMap<String, String>())) {
                Path jarRoot = fs.getPath("/");

                Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Path pathInJar = jarRoot.resolve(src.relativize(file).toString());
                        Files.copy(file, pathInJar, StandardCopyOption.REPLACE_EXISTING);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void copyFilesToJar(Map<String, String> path2instrumented, Path jar) {
        try {
            try (FileSystem fs = FileSystems.newFileSystem(URI.create("jar:file:" + jar), new HashMap<String, String>())) {
                Path jarRoot = fs.getPath("/");

                for (Map.Entry<String, String> entry : path2instrumented.entrySet()) {
                    if (entry.getValue() == null)
                        continue;

                    Path pathInJar = jarRoot.resolve(entry.getKey());
                    Files.copy(Paths.get(entry.getValue()), pathInJar, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void copyFilesToJar(Set<String> includedRelativePathFiles, Path src, Path jar) {
        if (!Files.exists(src))
            return;

        try {
            try (FileSystem fs = FileSystems.newFileSystem(URI.create("jar:file:" + jar), new HashMap<String, String>())) {
                Path jarRoot = fs.getPath("/");

                Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String relativePath = src.relativize(file).toString();
                        if (!includedRelativePathFiles.contains(relativePath))
                            return FileVisitResult.CONTINUE;

                        Path pathInJar = jarRoot.resolve(relativePath);
                        Files.copy(file, pathInJar, StandardCopyOption.REPLACE_EXISTING);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * This method is used to copy files from one JAR to another.
     * It first opens the old JAR and the new JAR as file systems.
     * Then, for each file in the provided set of files, it calculates the relative path of the file in the uninstrumented directory.
     * It resolves this relative path in the old JAR and the new JAR, and copies the file from the old JAR to the new JAR.
     */
    public static void copyFilesAcrossJar(Set<Path> files, Path uninstrumentedDir, Path instrumentedJar, Path newJar) {
        // convert files to instrumentedJar's path, then copy files across jar
        try {
            try (FileSystem oldFS = FileSystems.newFileSystem(URI.create("jar:file:" + instrumentedJar), new HashMap<String, String>());
                 FileSystem newFS = FileSystems.newFileSystem(URI.create("jar:file:" + newJar), new HashMap<String, String>())) {
                Path oldRoot = oldFS.getPath("/");
                Path newRoot = newFS.getPath("/");

                for (Path uninstrumentedFile : files) {
                    String rel = uninstrumentedDir.relativize(uninstrumentedFile).toString();
                    Path oldJarPath = oldRoot.resolve(rel);
                    Path newJarPath = newRoot.resolve(rel);

                    Files.copy(oldJarPath, newJarPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
