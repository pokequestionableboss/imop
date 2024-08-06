package org.iemop.instrumentation.library.repair;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GenerateBaseAspectFromLog {

    private static String getPointcut(String method) {
        if (method.endsWith(".<clinit>")) {
            String name = method.replace(".<clinit>", "").replace("$", ".");
            return "!within(" + name + ")";
        }

        String pointcut = method.replace("$", ".");
        if (pointcut.endsWith(".<init>")) {
            pointcut = "!withincode(" + pointcut.replace(".<init>", ".new(..)") + ")";
        } else {
            pointcut = "!withincode(* " + pointcut + "(..))";
        }

        return pointcut;
    }

    private static String getPointcutForClass(String className) {
        return "!within(" + className.replace("$", ".") + ")";
    }

    public static String generate(List<String> errorLog) {
        Set<String> methods = new HashSet<>();
        Set<String> excludedClasses = new HashSet<>();
        Set<String> pointcuts = new HashSet<>();

        Pattern pattern1 = Pattern.compile("problem generating method (.*) : Code size too big");
        Pattern pattern2 = Pattern.compile("when weaving type (.*)");
        Pattern pattern3 = Pattern.compile("Unexpected problem whilst preparing bytecode for (.*)");
        for (String line : errorLog) {
            Matcher match = pattern1.matcher(line);
            if (match.find()) {
                methods.add(match.group(1).replaceAll("\\$\\d+", ""));
                continue;
            }

            match = pattern2.matcher(line);
            if (match.find()) {
                excludedClasses.add(match.group(1).replaceAll("\\$\\d+", ""));
                continue;
            }

            match = pattern3.matcher(line);
            if (match.find()) {
                methods.add(match.group(1).split("\\(")[0].replaceAll("\\$\\d+", ""));
            }
        }

        if (methods.isEmpty() && excludedClasses.isEmpty()) {
            System.out.println("Cannot find any method to skip");
            return null;
        }

        for (String method : methods) {
            pointcuts.add(getPointcut(method));
        }

        for (String excludedClass : excludedClasses) {
            pointcuts.add(getPointcutForClass(excludedClass));
        }

        return "package mop;\n" +
                "public aspect BaseAspect {\n" +
                "    pointcut notwithin() :\n" +
                "    !within(sun..*) &&\n" +
                "    !within(java..*) &&\n" +
                "    !within(javax..*) &&\n" +
                "    !within(javafx..*) &&\n" +
                "    !within(com.sun..*) &&\n" +
                "    !within(org.dacapo.harness..*) &&\n" +
                "    !within(net.sf.cglib..*) &&\n" +
                "    !within(mop..*) &&\n" +
                "    !within(org.h2..*) &&\n" +
                "    !within(org.sqlite..*) &&\n" +
                "    !within(org.aspectj..*) &&\n" +
                "    !within(edu.cornell..*) &&\n" +
                "    !within(javamoprt..*) &&\n" +
                "    !within(rvmonitorrt..*) &&\n" +
                "    !within(org.junit..*) &&\n" +
                "    !within(junit..*) &&\n" +
                "    !within(java.lang.Object) &&\n" +
                "    !within(com.runtimeverification..*) &&\n" +
                "    !within(org.apache.maven.surefire..*) &&\n" +
                "    !within(org.mockito..*) &&\n" +
                "    !within(org.powermock..*) &&\n" +
                "    !within(org.easymock..*) &&\n" +
                "    !within(com.mockrunner..*) &&\n" +
                "    !within(org.jmock..*) &&\n" +
                "    !within(org.apache.maven..*) &&\n" +
                "    !within(org.testng..*) &&\n" +
                "    (" + String.join(" && ", pointcuts) + ");\n" +
                "}";
    }
}
