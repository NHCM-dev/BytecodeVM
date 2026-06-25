package nhcm.bytecodevm.Generator.Virtualization;

import nhcm.bytecodevm.Utils.Builder.FieldRef;
import nhcm.bytecodevm.Utils.Builder.MethodRef;

public class VMRuntimeLayout
{
    public final String owner;

    public final FieldRef codePools;
    public final FieldRef fieldHandles;
    public final FieldRef methodHandles;
    public final FieldRef methodTypes;
    public final FieldRef monitors;

    public final MethodRef constantString;
    public final MethodRef resolve;
    public final MethodRef interpret;
    public final MethodRef instructionIndex;
    public final MethodRef decodeOpcode;
    public final MethodRef decodeNextPc;
    public final MethodRef decodeOriginalPc;
    public final MethodRef decodeOperand;
    public final MethodRef methodType;
    public final MethodRef resolveConstant;
    public final MethodRef loadOwner;
    public final MethodRef loadOwnerWithLoader;
    public final MethodRef invoke;
    public final MethodRef construct;
    public final MethodRef getField;
    public final MethodRef setField;
    public final MethodRef fieldHandle;
    public final MethodRef adaptFieldHandle;
    public final MethodRef findField;
    public final MethodRef findMethod;
    public final MethodRef adaptMethodHandle;
    public final MethodRef adaptDirectMethodHandle;
    public final MethodRef adaptConstructorHandle;
    public final MethodRef coerceArgument;
    public final MethodRef cloneArray;
    public final MethodRef findExceptionHandler;
    public final MethodRef monitorFor;
    public final MethodRef monitorEnter;
    public final MethodRef monitorExit;
    public final MethodRef rethrow;

    public VMRuntimeLayout(String owner, String frameDescriptor)
    {
        this(owner, frameDescriptor, null);
    }

    public VMRuntimeLayout(String owner, String frameDescriptor, String programDescriptor)
    {
        this.owner = owner;

        this.codePools = field("CODE_POOLS", "Ljava/util/List;");
        this.fieldHandles = field("FIELD_HANDLES", "Ljava/util/Map;");
        this.methodHandles = field("METHOD_HANDLES", "Ljava/util/Map;");
        this.methodTypes = field("METHOD_TYPES", "Ljava/util/Map;");
        this.monitors = field("MONITORS", "Ljava/util/Map;");

        this.constantString = method("constantString", "([Ljava/lang/Object;I)Ljava/lang/String;");
        this.resolve = programDescriptor == null ? null : method("resolve", "(I)" + programDescriptor);
        this.interpret = programDescriptor == null ? null : method("interpret", "(" + programDescriptor + frameDescriptor + ")V");
        this.instructionIndex = programDescriptor == null ? null : method("instructionIndex", "(" + programDescriptor + "I)I");
        this.decodeOpcode = programDescriptor == null ? null : method("decodeOpcode", "(" + programDescriptor + "I)I");
        this.decodeNextPc = programDescriptor == null ? null : method("decodeNextPc", "(" + programDescriptor + "I)I");
        this.decodeOriginalPc = programDescriptor == null ? null : method("decodeOriginalPc", "(" + programDescriptor + "I)I");
        this.decodeOperand = programDescriptor == null ? null : method("decodeOperand", "(" + programDescriptor + "III)I");
        this.methodType = method("methodType", "(Ljava/lang/String;)Ljava/lang/invoke/MethodType;");
        this.resolveConstant = method("resolveConstant", "(Ljava/lang/Object;" + frameDescriptor + ")Ljava/lang/Object;");
        this.loadOwner = method("loadOwner", "(Ljava/lang/String;)Ljava/lang/Class;");
        this.loadOwnerWithLoader = method("loadOwner", "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/Class;");
        this.invoke = method(
                "invoke",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/invoke/MethodType;" +
                        "ZLjava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
        this.construct = method(
                "construct",
                "(Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/Object;");
        this.getField = method(
                "getField",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZLjava/lang/Object;)Ljava/lang/Object;");
        this.setField = method(
                "setField",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZLjava/lang/Object;Ljava/lang/Object;)V");
        this.fieldHandle = method(
                "fieldHandle",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZ)Ljava/lang/invoke/MethodHandle;");
        this.adaptFieldHandle = method(
                "adaptFieldHandle",
                "(Ljava/lang/reflect/Field;ZZ)Ljava/lang/invoke/MethodHandle;");
        this.findField = method(
                "findField",
                "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/reflect/Field;");
        this.findMethod = method(
                "findMethod",
                "(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;");
        this.adaptMethodHandle = method(
                "adaptMethodHandle",
                "(Ljava/lang/reflect/Method;ZI)Ljava/lang/invoke/MethodHandle;");
        this.adaptDirectMethodHandle = method(
                "adaptDirectMethodHandle",
                "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;Z)Ljava/lang/invoke/MethodHandle;");
        this.adaptConstructorHandle = method(
                "adaptConstructorHandle",
                "(Ljava/lang/reflect/Constructor;I)Ljava/lang/invoke/MethodHandle;");
        this.coerceArgument = method(
                "coerceArgument",
                "(Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;");
        this.cloneArray = method("cloneArray", "(Ljava/lang/Object;)Ljava/lang/Object;");
        this.findExceptionHandler = method(
                "findExceptionHandler",
                "(Ljava/lang/Throwable;[III[Ljava/lang/Object;)I");
        this.monitorFor = method(
                "monitorFor",
                "(Ljava/lang/Object;)Ljava/util/concurrent/locks/ReentrantLock;");
        this.monitorEnter = method("monitorEnter", "(Ljava/lang/Object;)V");
        this.monitorExit = method("monitorExit", "(Ljava/lang/Object;)V");
        this.rethrow = method("rethrow", "(Ljava/lang/Throwable;)Ljava/lang/RuntimeException;");
    }

    private FieldRef field(String name, String descriptor)
    {
        return new FieldRef(owner, name, descriptor);
    }

    private MethodRef method(String name, String descriptor)
    {
        return new MethodRef(owner, name, descriptor);
    }
}
