package org.iemop.util.smethods;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

// Source: https://github.com/sweetStreet/smethods/blob/main/src/main/java/org/smethods/MethodCallCollectorCV.java
public class MethodCallCollectorCV extends ClassVisitor {

    // Name of the class being visited.
    private String mClassName;

    Map<Method, Set<Method>> methodName2InvokedMethodNames;
    Map<String, Set<String>> hierarchy_parents;
    Map<String, Set<String>> hierarchy_children;
    Map<String, Set<String>> class2ContainedMethodNames;
    Set<String> classesInConstantPool;

    Set<String> addedStatic = new HashSet<>();

    public MethodCallCollectorCV(Map<Method, Set<Method>> methodName2MethodNames,
                                 Map<String, Set<String>> hierarchy_parents,
                                 Map<String, Set<String>> hierarchy_children,
                                 Map<String, Set<String>> class2ContainedMethodNames,
                                 Set<String> classesInConstantPool) {
        super(Opcodes.ASM9);
        this.methodName2InvokedMethodNames = methodName2MethodNames;
        this.hierarchy_parents = hierarchy_parents;
        this.hierarchy_children = hierarchy_children;
        this.class2ContainedMethodNames = class2ContainedMethodNames;
        this.classesInConstantPool = classesInConstantPool;
    }

    public MethodCallCollectorCV(Map<Method, Set<Method>> methodName2MethodNames,
                                 Map<String, Set<String>> hierarchy_parents,
                                 Map<String, Set<String>> hierarchy_children,
                                 Map<String, Set<String>> class2ContainedMethodNames) {
        super(Opcodes.ASM9);
        this.methodName2InvokedMethodNames = methodName2MethodNames;
        this.hierarchy_parents = hierarchy_parents;
        this.hierarchy_children = hierarchy_children;
        this.class2ContainedMethodNames = class2ContainedMethodNames;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        mClassName = name;
    }

    @Override
    public MethodVisitor visitMethod(int access, final String outerName, final String outerDesc, String signature,
                                     String[] exceptions) {
        // append arguments to key, remove what after ) of desc
        String outerMethodSig = outerName + outerDesc.substring(0, outerDesc.indexOf(")") + 1);
        Method key = new Method(mClassName, outerMethodSig);
        Set<Method> mInvokedMethods = methodName2InvokedMethodNames.computeIfAbsent(key, k -> new TreeSet<>());
        return new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                if (!owner.startsWith("java/") && !owner.startsWith("org/junit/")) {
                    String methodSig = name + desc.substring(0, desc.indexOf(")") + 1);

                    if (class2ContainedMethodNames.getOrDefault(owner, new HashSet<>()).contains(methodSig)) {
                        mInvokedMethods.add(new Method(owner, methodSig));
                    } else {
                        // find the first parent that implements the method
                        String firstParent = findFirstParent(owner, methodSig);
                        if (!firstParent.equals("")) {
                            mInvokedMethods.add(new Method(firstParent, methodSig));
                        }
                    }
                    if (!methodSig.startsWith("<init>") && !methodSig.startsWith("<clinit>")) {
                        for (String subClass : hierarchy_children.getOrDefault(owner, new HashSet<>())) {
                            if (class2ContainedMethodNames.getOrDefault(subClass, new HashSet<>())
                                    .contains(methodSig)) {
                                mInvokedMethods.add(new Method(subClass, methodSig));
                            }
                        }
                    }

                    addClinit(mClassName + "#" + outerMethodSig, owner, mInvokedMethods, true);
                }
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                if (!owner.startsWith("java/") && !owner.startsWith("org/junit/")) {
                    Method field = new Method(owner, name);
                    // outerDesc.equals("<init>")
                    // non static field would be invoked through constructor
                    if ((opcode == Opcodes.PUTSTATIC && outerName.equals("<clinit>"))
                            || (opcode == Opcodes.PUTFIELD && outerName.equals("<init>"))) {
                        if (owner.equals(mClassName)) {
                            Set<Method> methods = methodName2InvokedMethodNames.getOrDefault(field, new HashSet<>());
                            methods.add(key);
                            methodName2InvokedMethodNames.put(field, methods);
                        }
                    }
                    mInvokedMethods.add(field);
                    addClinit(mClassName + "#" + outerMethodSig, owner, mInvokedMethods, false);
                }
                // Opcodes.GETFIELD, Opcodes.PUTFIELD, Opcodes.GETSTATIC, Opcodes.PUTSTATIC
                super.visitFieldInsn(opcode, owner, name, desc);
            }

        };
    }

    public String findFirstParent(String currentClass, String methodSig) {
        for (String parent : hierarchy_parents.getOrDefault(currentClass, new HashSet<>())) {
            if (class2ContainedMethodNames.getOrDefault(parent, new HashSet<>()).contains(methodSig)) {
                return parent;
            } else {
                String firstParent = findFirstParent(parent, methodSig);
                if (!firstParent.equals("")) {
                    return firstParent;
                }
            }
        }
        return "";
    }

    public void addClinit(String method, String classname, Set<Method> mInvokedMethods, boolean includeChildren) {
        if (addedStatic.contains(method + "#" + classname)) {
            return;
        }
        addedStatic.add(method + "#" + classname);

        if (class2ContainedMethodNames.getOrDefault(classname, new HashSet<>()).contains("<clinit>()")) {
            mInvokedMethods.add(new Method(classname, "<clinit>()"));
        }

        for (String parent : hierarchy_parents.getOrDefault(classname, new HashSet<>())) {
            if (class2ContainedMethodNames.getOrDefault(parent, new HashSet<>()).contains("<clinit>()")) {
                mInvokedMethods.add(new Method(parent, "<clinit>()"));
            }
        }

        if (includeChildren) {
            for (String subClass : hierarchy_children.getOrDefault(classname, new HashSet<>())) {
                if (class2ContainedMethodNames.getOrDefault(subClass, new HashSet<>()).contains("<clinit>()")) {
                    mInvokedMethods.add(new Method(subClass, "<clinit>()"));
                }
            }
        }
    }
}
