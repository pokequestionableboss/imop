package org.iemop.instrumentation.library;

import org.iemop.instrumentation.InstrumentationResult;
import org.iemop.util.instrumentation.AspectJUtil;
import org.iemop.util.IOUtil;
import org.iemop.util.instrumentation.InstrumentationTool;
import org.iemop.util.instrumentation.InstrumentationToolResult;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.Callable;

public class InstrumentJarPartWorker implements Callable<InstrumentationResult> {

    private final String jarPath;
    private final String inPath;
    private final String outPath;
    private final String packagePath;
    private final Set<Path> files;
    private final Path filesRoot;
    private final String classpath;
    private final String aspectPath;

    public InstrumentJarPartWorker(String jarPath, String inPath, String outPath, String packagePath, Set<Path> files, Path filesRoot, String classpath, String aspectPath) {
        this.jarPath = jarPath;
        this.inPath = inPath;
        this.outPath = outPath;
        this.packagePath = packagePath;
        this.files = files;
        this.filesRoot = filesRoot;
        this.classpath = classpath;
        this.aspectPath = aspectPath;
    }

    @Override
    public InstrumentationResult call() throws InterruptedException {
        try {
            Path tmpInDir = Paths.get(inPath + File.separator + this.packagePath.replace(File.separator, "."));
//            System.out.println("Instrument part: " + jarPath + ", in: " + tmpInDir + ", outPath: " + outPath + ", packagePath: " + packagePath + ", files size: " + files.size() + ", filesRoot: " + filesRoot);

            Files.createDirectories(tmpInDir);

            IOUtil.copyFilesToDest(this.files, this.filesRoot, tmpInDir); // TODO: directly resolve root in `this.files`

            InstrumentationToolResult result = InstrumentationTool.getInstance().instrumentExtractedJar(
                    jarPath + " (" + packagePath + ")",
                    jarPath + File.pathSeparator + this.classpath,
                    aspectPath,
                    tmpInDir.toString(),
                    outPath
            );

            return new InstrumentationResult(null, this.packagePath, outPath, result.result);
        } catch (Exception e) {
            e.printStackTrace();
            return new InstrumentationResult(null, this.packagePath, outPath, false);
        }
    }
}
