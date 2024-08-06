package org.iemop.util.instrumentation;

import org.iemop.instrumentation.library.Jar;
import org.iemop.util.Config;

public abstract class InstrumentationTool {
    private static InstrumentationTool instance = null;
    public static InstrumentationTool getInstance() {
        if (instance == null)
            instance = Config.bism ? new BISMUtil() : new AspectJUtil();
        return instance;
    }

    public abstract InstrumentationToolResult run(String[] command);
    public abstract String[] getArgument(String classpath, String aspectPath, String inPath, String outPath, boolean isJar);
    public abstract InstrumentationToolResult compile(String argument);
    public abstract InstrumentationToolResult instrumentProject(String classpath, String aspectPath, String inPath, String outPath);
    public abstract InstrumentationToolResult instrumentJar(String classpath, String aspectPath, String inPath, String outPath);

    public InstrumentationToolResult instrumentExtractedJar(Jar jar, String classpath, String aspectPath, String inPath, String outPath) {
        return instrumentExtractedJar(jar.jarPath, classpath, aspectPath, inPath, outPath);
    }

    public abstract InstrumentationToolResult instrumentExtractedJar(String jarPath, String classpath, String aspectPath, String inPath, String outPath);
}
