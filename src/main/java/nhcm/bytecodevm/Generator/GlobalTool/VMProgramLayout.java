package nhcm.bytecodevm.Generator.GlobalTool;

import nhcm.bytecodevm.Utils.Builder.FieldRef;
import nhcm.bytecodevm.Utils.Builder.MethodRef;

public class VMProgramLayout
{
    public final String owner;

    public final FieldRef codeField;
    public final FieldRef constantsField;
    public final FieldRef exceptionHandlersField;
    public final FieldRef maxLocalsField;
    public final FieldRef maxStackField;

    public final MethodRef init;
    public final MethodRef code;
    public final MethodRef constants;
    public final MethodRef exceptionHandlers;
    public final MethodRef maxLocals;
    public final MethodRef maxStack;

    public VMProgramLayout(String owner)
    {
        this.owner = owner;

        this.codeField = field("code", "[I");
        this.constantsField = field("constants", "[Ljava/lang/Object;");
        this.exceptionHandlersField = field("exceptionHandlers", "[I");
        this.maxLocalsField = field("maxLocals", "I");
        this.maxStackField = field("maxStack", "I");

        this.init = method("<init>", "([I[Ljava/lang/Object;[III)V");
        this.code = method("code", "()[I");
        this.constants = method("constants", "()[Ljava/lang/Object;");
        this.exceptionHandlers = method("exceptionHandlers", "()[I");
        this.maxLocals = method("maxLocals", "()I");
        this.maxStack = method("maxStack", "()I");
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
