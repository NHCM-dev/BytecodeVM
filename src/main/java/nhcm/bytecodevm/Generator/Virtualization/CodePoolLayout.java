package nhcm.bytecodevm.Generator.Virtualization;

import nhcm.bytecodevm.Utils.Builder.FieldRef;
import nhcm.bytecodevm.Utils.Builder.MethodRef;

public class CodePoolLayout
{
    public final String owner;
    public final String programDescriptor;
    public final String codePoolDescriptor;

    public final FieldRef instance;
    public final FieldRef codes;
    public final FieldRef constants;
    public final FieldRef exceptionHandlers;
    public final FieldRef maxLocals;
    public final FieldRef maxStack;

    public final MethodRef init;
    public final MethodRef find;

    public CodePoolLayout(String owner, String codePoolDescriptor, String programDescriptor)
    {
        this.owner = owner;
        this.codePoolDescriptor = codePoolDescriptor;
        this.programDescriptor = programDescriptor;

        this.instance = field("INSTANCE", codePoolDescriptor);
        this.codes = field("CODES", "[[I");
        this.constants = field("CONSTANTS", "[[Ljava/lang/Object;");
        this.exceptionHandlers = field("EXCEPTION_HANDLERS", "[[I");
        this.maxLocals = field("MAX_LOCALS", "[I");
        this.maxStack = field("MAX_STACK", "[I");

        this.init = method("<init>", "()V");
        this.find = method("find", "(I)" + programDescriptor);
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
