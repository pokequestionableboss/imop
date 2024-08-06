package org.iemop.util;

public class Config {
    public static boolean incrementalJar = false;
    public static boolean jarThreads = false;
    public static boolean threadsUseFiles = false;
    public static boolean cleanByteCode = false;
    public static boolean storeHashes = false;
    public static boolean methodCIA = false;
    public static boolean classCIA = false;
    public static boolean dynamicCIA = false;
    public static String dynamicCIALog = "";
    public static boolean lazy = false;
    public static boolean timer = false;
    public static int threads = 1;
    public static boolean bism = false;
    public static boolean noMonitoring = false;
    public static boolean skipCleanProject = false;
    public static boolean hashesSingleThread = true;

    public static boolean skipMOP = false;

    public static void setVariant(String variant) {
        switch (variant) {
            case "incrementalJar":
                incrementalJar = true;
                break;
            case "jarThreads":
                jarThreads = true;
                break;
            case "threadsUseFiles":
                jarThreads = true;
                threadsUseFiles = true;
                break;
            case "cleanByteCode":
                incrementalJar = true;
                cleanByteCode = true;
                break;
            case "storeHashes":
                incrementalJar = true;
                cleanByteCode = true;
                storeHashes = true;
                break;
            case "incrementalJarThreads":
                incrementalJar = true;
                jarThreads = true;
                break;
            case "cleanByteCodeThreads":
                incrementalJar = true;
                jarThreads = true;
                cleanByteCode = true;
                break;
            case "storeHashesThreads":
                incrementalJar = true;
                jarThreads = true;
                cleanByteCode = true;
                storeHashes = true;
                break;
            case "methodCIA":
                methodCIA = true;
                break;
            case "classCIA":
                classCIA = true;
                break;
            case "dynamicCIA":
                dynamicCIA = true;
                break;
            case "dynamicCIALazy":
                dynamicCIA = true;
                lazy = true;
                break;
            case "methodCIAThreads":
                methodCIA = true;
                jarThreads = true;
                break;
            case "classCIAThreads":
                classCIA = true;
                jarThreads = true;
                break;
            case "dynamicCIAThreads":
                dynamicCIA = true;
                jarThreads = true;
                break;
            case "dynamicCIALazyThreads":
                dynamicCIA = true;
                jarThreads = true;
                lazy = true;
                break;
            case "online":
                break;
            default:
                throw new RuntimeException("Unknown variant: " + variant);
        }
    }
}
