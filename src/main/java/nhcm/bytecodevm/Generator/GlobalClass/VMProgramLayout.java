package nhcm.bytecodevm.Generator.GlobalClass;

import nhcm.bytecodevm.Utils.Builder.FieldRef;
import nhcm.bytecodevm.Utils.Builder.MethodRef;

public class VMProgramLayout
{
    public final String owner;

    public final FieldRef opcodeStreamField;
    public final FieldRef operandStreamField;
    public final FieldRef layoutStreamField;
    public final FieldRef constantsField;
    public final FieldRef exceptionHandlersField;
    public final FieldRef opcodeMapField;
    public final FieldRef methodKeyField;
    public final FieldRef maxLocalsField;
    public final FieldRef maxStackField;

    public final MethodRef init;
    public final MethodRef opcodeStream;
    public final MethodRef operandStream;
    public final MethodRef layoutStream;
    public final MethodRef constants;
    public final MethodRef exceptionHandlers;
    public final MethodRef opcodeMap;
    public final MethodRef methodKey;
    public final MethodRef maxLocals;
    public final MethodRef maxStack;

    public VMProgramLayout(String owner)
    {
        this.owner = owner;

        this.opcodeStreamField = field("opcodeStream", "[I");
        this.operandStreamField = field("operandStream", "[I");
        this.layoutStreamField = field("layoutStream", "[I");
        this.constantsField = field("constants", "[Ljava/lang/Object;");
        this.exceptionHandlersField = field("exceptionHandlers", "[I");
        this.opcodeMapField = field("opcodeMap", "[I");
        this.methodKeyField = field("methodKey", "I");
        this.maxLocalsField = field("maxLocals", "I");
        this.maxStackField = field("maxStack", "I");

        this.init = method("<init>", "([I[I[I[Ljava/lang/Object;[I[IIII)V");
        this.opcodeStream = method("opcodeStream", "()[I");
        this.operandStream = method("operandStream", "()[I");
        this.layoutStream = method("layoutStream", "()[I");
        this.constants = method("constants", "()[Ljava/lang/Object;");
        this.exceptionHandlers = method("exceptionHandlers", "()[I");
        this.opcodeMap = method("opcodeMap", "()[I");
        this.methodKey = method("methodKey", "()I");
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
