package org.iemop.instrumentation.project;

import org.iemop.util.instrumentation.AspectJUtil;
import org.iemop.util.instrumentation.InstrumentationTool;

public class InstrumentProject {
    private final String src;
    private final String classpath;

    public InstrumentProject(String src, String classpath) {
        this.src = src;
        this.classpath = classpath;
    }

    public boolean instrument(String aspectPath, String dest) {
        return InstrumentationTool.getInstance().instrumentProject(this.classpath, aspectPath, this.src, dest).result;
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
}
