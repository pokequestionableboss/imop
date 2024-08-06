package org.iemop.util.smethods;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

// Source: https://github.com/sweetStreet/smethods/blob/main/src/main/java/org/smethods/ClassToMethodsCollectorCV.java
public class ClassToMethodsCollectorCV extends ClassVisitor {

    // Name of the class being visited.
    public String mClassName;

    Map<String, Set<String>> class2ContainedMethodNames;
    Map<String, Set<String>> hierarchy_parents; // map from child to parents
    Map<String, Set<String>> hierarchy_children; // map from parent to children
    Set<Method> methodsInProject = null;

    public ClassToMethodsCollectorCV(Map<String, Set<String>> class2ContainedMethodNames,
                                     Map<String, Set<String>> hierarchy_parents,
                                     Map<String, Set<String>> hierarchy_children) {
        super(Opcodes.ASM9);
        this.class2ContainedMethodNames = class2ContainedMethodNames;
        this.hierarchy_parents = hierarchy_parents;
        this.hierarchy_children = hierarchy_children;
    }

    public ClassToMethodsCollectorCV(Map<String, Set<String>> class2ContainedMethodNames,
                                     Map<String, Set<String>> hierarchy_parents,
                                     Map<String, Set<String>> hierarchy_children,
                                     Set<Method> methodsInProject) {
        this(class2ContainedMethodNames, hierarchy_parents, hierarchy_children);
        this.methodsInProject = methodsInProject;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        mClassName = name;
        Set<String> parents = hierarchy_parents.getOrDefault(name, new HashSet<>());
        if (superName != null && !superName.startsWith("java/") && !superName.startsWith("org/junit/")) {
            parents.add(superName);
            Set<String> subClasses = hierarchy_children.getOrDefault(superName, new HashSet<>());
            subClasses.add(name);
            hierarchy_children.put(superName, subClasses);
        }
        for (String i : interfaces) {
            parents.add(i);
            Set<String> subClasses = hierarchy_children.getOrDefault(i, new HashSet<>());
            subClasses.add(name);
            hierarchy_children.put(i, subClasses);
        }
        hierarchy_parents.put(name, parents);
    }

    @Override
    public MethodVisitor visitMethod(int access, final String outerName, final String outerDesc, String signature,
                                     String[] exceptions) {
        // append arguments to key, remove what after ) of desc
        String m = outerName + outerDesc.substring(0, outerDesc.indexOf(")") + 1);
        Set<String> methods = class2ContainedMethodNames.getOrDefault(mClassName, new TreeSet<>());
        methods.add(m);
        class2ContainedMethodNames.put(mClassName, methods);

        if (methodsInProject != null) {
            methodsInProject.add(new Method(mClassName, m));
        }
        return null;
    }

}