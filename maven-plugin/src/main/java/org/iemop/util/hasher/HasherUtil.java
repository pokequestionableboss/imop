package org.iemop.util.hasher;

import org.ekstazi.hash.Hasher;
import org.iemop.instrumentation.library.Jar;
import org.iemop.util.Util;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Set;

public class HasherUtil {
    public static HashMap<String, String> saveHashes(Set<Path> files, String hashesFilePath) {
        try {
            Hasher hasher = new Hasher(Hasher.Algorithm.CRC32, 1000, true);

            HashMap<String, String> fileToHash = new HashMap<>();
            for (Path file : files) {
                String filePathString = file.toString();

                if (filePathString.endsWith(".class")) {
                    fileToHash.put(filePathString, hasher.hashURL("file:" + filePathString));
                }
            }
            Util.saveToDisk(fileToHash, hashesFilePath);
            return fileToHash;
        } catch (Exception e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    public static HashMap<String, String> saveHashes(Path pathInTarget, String hashesFilePath) {
        try {
            Hasher hasher = new Hasher(Hasher.Algorithm.CRC32, 1000, true);

            HashMap<String, String> fileToHash = new HashMap<>();
            Files.walkFileTree(pathInTarget, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String filePathString = file.toString();

                    if (filePathString.endsWith(".class")) {
                        fileToHash.put(filePathString, hasher.hashURL("file:" + filePathString));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            Util.saveToDisk(fileToHash, hashesFilePath);
            return fileToHash;
        } catch (Exception e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }
}
