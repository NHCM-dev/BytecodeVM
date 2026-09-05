package nhcm.bytecodevm.generator.virtualization;

import nhcm.bytecodevm.utils.builder.FieldRef;
import nhcm.bytecodevm.utils.builder.MethodRef;
import nhcm.bytecodevm.generator.GeneratedMemberNamer;

public class CodePoolLayout
{
    public final String owner;
    public final String programDescriptor;
    public final String codePoolDescriptor;
    private final GeneratedMemberNamer namer;

    public final FieldRef instance;
    public final FieldRef opcodeStreams;
    public final FieldRef operandStreams;
    public final FieldRef layoutStreams;
    public final FieldRef blockStreams;
    public final FieldRef constants;
    public final FieldRef exceptionHandlers;
    public final FieldRef opcodeMaps;
    public final FieldRef methodKeys;
    public final FieldRef featureFlags;
    public final FieldRef maxLocals;
    public final FieldRef maxStack;

    public final MethodRef init;
    public final MethodRef find;
    public final MethodRef mix;
    public final MethodRef arrayMix;
    public final MethodRef unpackInts;
    public final MethodRef unpackStringInts;

    public CodePoolLayout(String owner, String codePoolDescriptor, String programDescriptor)
    {
        this(owner, codePoolDescriptor, programDescriptor, GeneratedMemberNamer.DISABLED, null);
    }

    public CodePoolLayout(
            String owner,
            String codePoolDescriptor,
            String programDescriptor,
            GeneratedMemberNamer namer,
            String findName)
    {
        this.owner = owner;
        this.codePoolDescriptor = codePoolDescriptor;
        this.programDescriptor = programDescriptor;
        this.namer = namer;

        this.instance = field("INSTANCE", codePoolDescriptor);
        this.opcodeStreams = field("OPCODE_STREAMS", "[[I");
        this.operandStreams = field("OPERAND_STREAMS", "[[I");
        this.layoutStreams = field("LAYOUT_STREAMS", "[[I");
        this.blockStreams = field("BLOCK_STREAMS", "[[I");
        this.constants = field("CONSTANTS", "[[Ljava/lang/Object;");
        this.exceptionHandlers = field("EXCEPTION_HANDLERS", "[[I");
        this.opcodeMaps = field("OPCODE_MAPS", "[[I");
        this.methodKeys = field("METHOD_KEYS", "[I");
        this.featureFlags = field("FEATURE_FLAGS", "[I");
        this.maxLocals = field("MAX_LOCALS", "[I");
        this.maxStack = field("MAX_STACK", "[I");

        this.init = method("<init>", "()V");
        this.find = findName == null
                ? method("find", "(I)" + programDescriptor)
                : methodWithName(findName, "(I)" + programDescriptor);
        this.mix = method("mix", "(IIII)I");
        this.arrayMix = method("arrayMix", "(II)I");
        this.unpackInts = method("unpackInts", "([JII)[I");
        this.unpackStringInts = method("unpackStringInts", "([Ljava/lang/String;II)[I");
    }

    private FieldRef field(String name, String descriptor)
    {
        return new FieldRef(owner, namer.field(owner, name), descriptor);
    }

    private MethodRef method(String name, String descriptor)
    {
        return new MethodRef(owner, namer.method(owner, name, descriptor), descriptor);
    }

    private MethodRef methodWithName(String name, String descriptor)
    {
        return new MethodRef(owner, namer.reserveMethodName(owner, name, descriptor), descriptor);
    }
}
