package org.iemop.util.smethods;

import java.io.Serializable;

public class Method implements Comparable<Method>, Serializable {
    private final String className;
    private final String methodName;

    public Method(String className, String methodName) {
        this.className = className;
        this.methodName = methodName;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public int compareTo(Method other) {
        int classNameComparison = className.compareTo(other.getClassName());
        if (classNameComparison != 0) {
            return classNameComparison;
        }
        return methodName.compareTo(other.getMethodName());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        Method method = (Method) other;
        return className.equals(method.getClassName()) && methodName.equals(method.getMethodName());
    }

    @Override
    public int hashCode() {
        return 31 * className.hashCode() + methodName.hashCode();
    }

    @Override
    public String toString() {
        return className + "#" + methodName;
    }
}
