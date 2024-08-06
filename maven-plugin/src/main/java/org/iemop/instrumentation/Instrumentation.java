package org.iemop.instrumentation;

import org.apache.maven.plugin.logging.Log;
import org.iemop.instrumentation.library.InstrumentJarWorker;
import org.iemop.instrumentation.project.InstrumentProjectWorker;
import org.iemop.util.TimerUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Instrumentation {

    private final Log log;
    private final String classpath;
    private final String aspectPath;

    public Instrumentation(Log log, String classpath, String aspectPath) {
        this.log = log;
        this.classpath = classpath;
        this.aspectPath = aspectPath;
    }

    public List<InstrumentationResult> instrumentAll(List<InstrumentationTask> tasks, int threads) {
        long start = System.currentTimeMillis();
        if (tasks.isEmpty()) {
            log.info("Nothing new to instrument");
            return new ArrayList<>();
        }

        log.info("Instrumenting " + tasks.size() + " tasks using " + threads + " threads");

        List<Future<InstrumentationResult>> results = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        ExecutorService jarPool = Executors.newFixedThreadPool(threads);

        for (InstrumentationTask task : tasks) {
            if (task.isJar()) {
                log.info("Submitting InstrumentJarWorker(source: " + task.getSource() + ", dest: " + task.getDest() + ", aspectPath: " + aspectPath + ")");
                results.add(pool.submit(new InstrumentJarWorker(jarPool, task.getJar(), task.getSource(), task.getDest(), classpath, aspectPath, task.getDependency(), task.isForceInstrument())));
            } else {
                log.info("Submitting InstrumentProjectWorker(source: " + task.getSource() + ", dest: " + task.getDest() + ", aspectPath: " + aspectPath + ")");
                results.add(pool.submit(new InstrumentProjectWorker(task.getSource(), task.getDest(), classpath, aspectPath)));
            }
        }

        List<InstrumentationResult> res = new ArrayList<>();
        for(Future<InstrumentationResult> result : results) {
            try {
                res.add(result.get());
            } catch (Exception e) {
                System.out.println("Exception from task:");
                e.printStackTrace();
            }
        }

        log.info("Completed all tasks");
        TimerUtil.log("Time to complete all tasks - " + (System.currentTimeMillis() - start) + " ms");
        return res;
    }
}
