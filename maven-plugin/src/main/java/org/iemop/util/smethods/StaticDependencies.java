package org.iemop.util.smethods;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class StaticDependencies implements Serializable {
    public Map<String, Set<String>> class2ContainedMethodNames = new HashMap<>();
    public Map<Method, Set<Method>> methodName2MethodNames = new HashMap<>();
    public Map<String, Set<String>> hierarchy_parents = new HashMap<>();
    public Map<String, Set<String>> hierarchy_children = new HashMap<>();

    @Override
    public String toString() {
        return methodName2MethodNames.toString();
    }
}
