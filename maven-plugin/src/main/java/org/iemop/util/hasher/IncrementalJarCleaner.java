package org.iemop.util.hasher;

import org.ekstazi.hash.Hasher;
import org.iemop.util.Config;
import org.iemop.util.TimerUtil;
import org.iemop.util.Util;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class IncrementalJarCleaner {

    private class IncrementalJarCleanerResult {
        public boolean same;
        public String hash;

        public IncrementalJarCleanerResult(boolean same, String hash) {
            this.same = same;
            this.hash = hash;
        }
    }

    private static IncrementalJarCleaner instance = null;
    private ExecutorService pool;

    public static IncrementalJarCleaner getInstance() {
        if (instance == null)
            instance = new IncrementalJarCleaner();
        return instance;
    }

    private IncrementalJarCleaner() {
        pool = Executors.newFixedThreadPool(Config.threads);
    }

    public Set<Path> cleanByteCode(Set<Path> updatedFilesSet, String tmpDir, String tmpDirForCurrent) {
        if (Config.hashesSingleThread)
            return cleanByteCodeSingleThread(updatedFilesSet, tmpDir, tmpDirForCurrent);

        // Compare bytecode again after cleaning bytecode
        long start = System.currentTimeMillis();
        HashMap<String, String> newJarHashes = new HashMap<>();
        Path originalRoot = Paths.get(tmpDir);
        Path currentRoot = Paths.get(tmpDirForCurrent);
        Hasher hasher = new Hasher(Hasher.Algorithm.CRC32, 1000, true);
        Set<Path> removed = new HashSet<>();
        List<Path> updatedFiles = new ArrayList<>(updatedFilesSet);

        if (Config.storeHashes) {
            // Read original jar hashes from disk
            HashMap<String, String> tmp = (HashMap<String, String>) Util.readFromDisk(tmpDir + "-hashes.map");
            HashMap<String, String> originalJarHashes = (tmp == null ? new HashMap<>() : tmp);

            // don't compute and store hashes for `newFiles` because we don't want to pay the price at this time
            // we will compute the hashes when the files are changed in the future
            List<Future<IncrementalJarCleanerResult>> results = new ArrayList<>();
            for (Path file : updatedFiles) {
                results.add(pool.submit(() -> {
                    Path oldFile = originalRoot.resolve(currentRoot.relativize(file));
                    String newFileHash = hasher.hashURL("file:" + file);
                    String oldFileHash = originalJarHashes.containsKey(oldFile.toString()) ? originalJarHashes.get(oldFile.toString()) : hasher.hashURL("file:" + oldFile);
                    return new IncrementalJarCleanerResult(newFileHash.equals(oldFileHash), newFileHash);
                }));
            }
            try {
                int i = 0;
                for (Future<IncrementalJarCleanerResult> res : results) {
                    IncrementalJarCleanerResult result = res.get();
                    if (result.same) { // true => same bytecode after cleaning, we don't need to instrument file
                        removed.add(updatedFiles.get(i));
                    }
                    newJarHashes.put(updatedFiles.get(i).toString(), result.hash);
                    i += 1;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (!newJarHashes.isEmpty())
                // added at least one entry to newJarHashes, save it to disk
                Util.saveToDisk(newJarHashes, tmpDirForCurrent + "-hashes.map");
        } else {
            // don't compute and store hashes for `newFiles` because we don't want to pay the price at this time
            // we will compute the hashes when the files are changed in the future
            List<Future<Boolean>> results = new ArrayList<>();
            for (Path file : updatedFiles) {
                results.add(pool.submit(() -> {
                    Path oldFile = originalRoot.resolve(currentRoot.relativize(file));
                    String newFileHash = hasher.hashURL("file:" + file);
                    String oldFileHash = hasher.hashURL("file:" + oldFile);
                    return newFileHash.equals(oldFileHash);
                }));
            }
            try {
                int i = 0;
                for (Future<Boolean> result : results) {
                    if (result.get()) { // true => same bytecode after cleaning, we don't need to instrument file
                        removed.add(updatedFiles.get(i));
                    }
                    i += 1;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        TimerUtil.log("Cleaned bytecode for " + updatedFiles.size() + " files with " + Config.threads + " threads - " + tmpDir + " in " + (System.currentTimeMillis() - start) + " ms");
        return removed;
    }

    private static Set<Path> cleanByteCodeSingleThread(Set<Path> updatedFiles, String tmpDir, String tmpDirForCurrent) {
        // Compare bytecode again after cleaning bytecode
        long start = System.currentTimeMillis();
        boolean updatedOldHashes = false;
        HashMap<String, String> originalJarHashes = new HashMap<>();
        HashMap<String, String> newJarHashes = new HashMap<>();

        if (Config.storeHashes) {
            // Read original jar hashes from disk
            HashMap<String, String> tmp = (HashMap<String, String>) Util.readFromDisk(tmpDir + "-hashes.map");
            if (tmp != null)
                originalJarHashes = tmp;
        }

        Path originalRoot = Paths.get(tmpDir);
        Path currentRoot = Paths.get(tmpDirForCurrent);
        Hasher hasher = new Hasher(Hasher.Algorithm.CRC32, 1000, true);
        Set<Path> removed = new HashSet<>();

        // don't compute and store hashes for `newFiles` because we don't want to pay the price at this time
        // we will compute the hashes when the files are changed in the future
        for (Path file : updatedFiles) {
            Path oldFile = originalRoot.resolve(currentRoot.relativize(file));

            String newFileHash = hasher.hashURL("file:" + file);
            String oldFileHash;

            if (Config.storeHashes) {
                // Save hashes to originalJarHashes and newJarHashes
                if (originalJarHashes.containsKey(oldFile.toString())) {
                    oldFileHash = originalJarHashes.get(oldFile.toString()); // get old hash from map
                } else {
                    oldFileHash = hasher.hashURL("file:" + oldFile); // get hash and save it to map
                    originalJarHashes.put(oldFile.toString(), oldFileHash);
                    updatedOldHashes = true;
                }

                newJarHashes.put(file.toString(), newFileHash);
            } else {
                oldFileHash = hasher.hashURL("file:" + oldFile);
            }

            if (newFileHash.equals(oldFileHash)) {
                // same bytecode after cleaning, we don't need to instrument file
                removed.add(file);
            }
        }

        if (Config.storeHashes) {
            if (updatedOldHashes)
                // added at least one entry to originalJarHashes, save it to disk
                // probably don't even need this at all, only time it is helpful is when we have v1.0, v1.3, back to v1.2, then we can use v1.0 hashes
                Util.saveToDisk(originalJarHashes, tmpDir + "-hashes.map");

            if (!newJarHashes.isEmpty())
                // added at least one entry to newJarHashes, save it to disk
                Util.saveToDisk(newJarHashes, tmpDirForCurrent + "-hashes.map");
        }

        TimerUtil.log("Cleaned bytecode for " + tmpDir + " in " + (System.currentTimeMillis() - start) + " ms");
        return removed;
    }
}
