package nhcm.bytecodevm.Generator.Virtualization;

import nhcm.bytecodevm.Utils.Builder.FieldRef;
import nhcm.bytecodevm.Utils.Builder.MethodRef;

public class CodePoolLayout
{
    public final String owner;
    public final String programDescriptor;
    public final String codePoolDescriptor;

    public final FieldRef instance;
    public final FieldRef opcodeStreams;
    public final FieldRef operandStreams;
    public final FieldRef layoutStreams;
    public final FieldRef constants;
    public final FieldRef exceptionHandlers;
    public final FieldRef opcodeMaps;
    public final FieldRef methodKeys;
    public final FieldRef maxLocals;
    public final FieldRef maxStack;

    public final MethodRef init;
    public final MethodRef find;
    public final MethodRef mix;
    public final MethodRef arrayMix;
    public final MethodRef unpackInts;

    public CodePoolLayout(String owner, String codePoolDescriptor, String programDescriptor)
    {
        this.owner = owner;
        this.codePoolDescriptor = codePoolDescriptor;
        this.programDescriptor = programDescriptor;

        this.instance = field("INSTANCE", codePoolDescriptor);
        this.opcodeStreams = field("OPCODE_STREAMS", "[[I");
        this.operandStreams = field("OPERAND_STREAMS", "[[I");
        this.layoutStreams = field("LAYOUT_STREAMS", "[[I");
        this.constants = field("CONSTANTS", "[[Ljava/lang/Object;");
        this.exceptionHandlers = field("EXCEPTION_HANDLERS", "[[I");
        this.opcodeMaps = field("OPCODE_MAPS", "[[I");
        this.methodKeys = field("METHOD_KEYS", "[I");
        this.maxLocals = field("MAX_LOCALS", "[I");
        this.maxStack = field("MAX_STACK", "[I");

        this.init = method("<init>", "()V");
        this.find = method("find", "(I)" + programDescriptor);
        this.mix = method("mix", "(IIII)I");
        this.arrayMix = method("arrayMix", "(II)I");
        this.unpackInts = method("unpackInts", "([JII)[I");
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
