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
    public final MethodRef methodType;
    public final MethodRef resolveConstant;
    public final MethodRef loadOwner;
    public final MethodRef loadOwnerWithLoader;
    public final MethodRef invoke;
    public final MethodRef construct;
    public final MethodRef getField;
    public final MethodRef setField;
    public final MethodRef findField;
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
        this.findField = method(
                "findField",
                "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/reflect/Field;");
        this.cloneArray = method("cloneArray", "(Ljava/lang/Object;)Ljava/lang/Object;");
        this.findExceptionHandler = method(
                "findExceptionHandler",
                "(Ljava/lang/Throwable;[II[Ljava/lang/Object;)I");
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
