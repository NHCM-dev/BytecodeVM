package nhcm.bytecodevm.Enums;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum VMOpcode
{
    NOP(Opcs.NOP),

    PUSH_NULL(Opcs.ACONST_NULL),
    PUSH_INT(
            Opcs.ICONST_M1,
            Opcs.ICONST_0,
            Opcs.ICONST_1,
            Opcs.ICONST_2,
            Opcs.ICONST_3,
            Opcs.ICONST_4,
            Opcs.ICONST_5,
            Opcs.BIPUSH,
            Opcs.SIPUSH),
    PUSH_LONG(Opcs.LCONST_0, Opcs.LCONST_1),
    PUSH_FLOAT(Opcs.FCONST_0, Opcs.FCONST_1, Opcs.FCONST_2),
    PUSH_DOUBLE(Opcs.DCONST_0, Opcs.DCONST_1),
    LOAD_CONSTANT(Opcs.LDC),

    LOAD_LOCAL(Opcs.ILOAD, Opcs.LLOAD, Opcs.FLOAD, Opcs.DLOAD, Opcs.ALOAD),
    STORE_LOCAL(Opcs.ISTORE, Opcs.LSTORE, Opcs.FSTORE, Opcs.DSTORE, Opcs.ASTORE),
    LOAD_ARRAY(
            Opcs.IALOAD,
            Opcs.LALOAD,
            Opcs.FALOAD,
            Opcs.DALOAD,
            Opcs.AALOAD,
            Opcs.BALOAD,
            Opcs.CALOAD,
            Opcs.SALOAD),
    STORE_ARRAY(
            Opcs.IASTORE,
            Opcs.LASTORE,
            Opcs.FASTORE,
            Opcs.DASTORE,
            Opcs.AASTORE,
            Opcs.BASTORE,
            Opcs.CASTORE,
            Opcs.SASTORE),

    POP(Opcs.POP, Opcs.POP2),
    DUPLICATE(
            Opcs.DUP,
            Opcs.DUP_X1,
            Opcs.DUP_X2,
            Opcs.DUP2,
            Opcs.DUP2_X1,
            Opcs.DUP2_X2),
    SWAP(Opcs.SWAP),

    ADD(Opcs.IADD, Opcs.LADD, Opcs.FADD, Opcs.DADD),
    SUBTRACT(Opcs.ISUB, Opcs.LSUB, Opcs.FSUB, Opcs.DSUB),
    MULTIPLY(Opcs.IMUL, Opcs.LMUL, Opcs.FMUL, Opcs.DMUL),
    DIVIDE(Opcs.IDIV, Opcs.LDIV, Opcs.FDIV, Opcs.DDIV),
    REMAINDER(Opcs.IREM, Opcs.LREM, Opcs.FREM, Opcs.DREM),
    NEGATE(Opcs.INEG, Opcs.LNEG, Opcs.FNEG, Opcs.DNEG),
    SHIFT_LEFT(Opcs.ISHL, Opcs.LSHL),
    SHIFT_RIGHT(Opcs.ISHR, Opcs.LSHR),
    UNSIGNED_SHIFT_RIGHT(Opcs.IUSHR, Opcs.LUSHR),
    BITWISE_AND(Opcs.IAND, Opcs.LAND),
    BITWISE_OR(Opcs.IOR, Opcs.LOR),
    BITWISE_XOR(Opcs.IXOR, Opcs.LXOR),
    INCREMENT(Opcs.IINC),

    CONVERT(
            Opcs.I2L,
            Opcs.I2F,
            Opcs.I2D,
            Opcs.L2I,
            Opcs.L2F,
            Opcs.L2D,
            Opcs.F2I,
            Opcs.F2L,
            Opcs.F2D,
            Opcs.D2I,
            Opcs.D2L,
            Opcs.D2F,
            Opcs.I2B,
            Opcs.I2C,
            Opcs.I2S),
    COMPARE(Opcs.LCMP, Opcs.FCMPL, Opcs.FCMPG, Opcs.DCMPL, Opcs.DCMPG),

    BRANCH(
            Opcs.IFEQ,
            Opcs.IFNE,
            Opcs.IFLT,
            Opcs.IFGE,
            Opcs.IFGT,
            Opcs.IFLE,
            Opcs.IF_ICMPEQ,
            Opcs.IF_ICMPNE,
            Opcs.IF_ICMPLT,
            Opcs.IF_ICMPGE,
            Opcs.IF_ICMPGT,
            Opcs.IF_ICMPLE,
            Opcs.IF_ACMPEQ,
            Opcs.IF_ACMPNE,
            Opcs.IFNULL,
            Opcs.IFNONNULL),
    GOTO(Opcs.GOTO),
    SUBROUTINE_CALL(Opcs.JSR),
    SUBROUTINE_RETURN(Opcs.RET),
    SWITCH(Opcs.TABLESWITCH, Opcs.LOOKUPSWITCH),

    RETURN(
            Opcs.IRETURN,
            Opcs.LRETURN,
            Opcs.FRETURN,
            Opcs.DRETURN,
            Opcs.ARETURN,
            Opcs.RETURN),

    READ_FIELD(Opcs.GETSTATIC, Opcs.GETFIELD),
    WRITE_FIELD(Opcs.PUTSTATIC, Opcs.PUTFIELD),
    INVOKE(
            Opcs.INVOKEVIRTUAL,
            Opcs.INVOKESPECIAL,
            Opcs.INVOKESTATIC,
            Opcs.INVOKEINTERFACE),
    INVOKE_DYNAMIC(Opcs.INVOKEDYNAMIC),

    NEW_OBJECT(Opcs.NEW),
    NEW_ARRAY(Opcs.NEWARRAY, Opcs.ANEWARRAY, Opcs.MULTIANEWARRAY),
    ARRAY_LENGTH(Opcs.ARRAYLENGTH),
    THROW(Opcs.ATHROW),
    CAST(Opcs.CHECKCAST),
    INSTANCE_OF(Opcs.INSTANCEOF),
    MONITOR(Opcs.MONITORENTER, Opcs.MONITOREXIT);

    private static final Map<Opcs, VMOpcode> BY_OPCODE;

    static
    {
        Map<Opcs, VMOpcode> byOpcode = new EnumMap<>(Opcs.class);

        for (VMOpcode operation : values())
        {
            for (Opcs opcode : operation.opcodes)
            {
                VMOpcode previous = byOpcode.put(opcode, operation);
                if (previous != null)
                {
                    throw new IllegalStateException(
                            opcode + " belongs to both " + previous + " and " + operation);
                }
            }
        }

        for (Opcs opcode : Opcs.values())
        {
            if (!byOpcode.containsKey(opcode))
            {
                throw new IllegalStateException("Opcode has no VM operation: " + opcode);
            }
        }

        BY_OPCODE = Collections.unmodifiableMap(byOpcode);
    }

    private final Set<Opcs> opcodes;

    VMOpcode(Opcs first, Opcs... remaining)
    {
        this.opcodes = EnumSet.of(first, remaining);
    }

    public Set<Opcs> getOpcodes()
    {
        return Collections.unmodifiableSet(opcodes);
    }

    public boolean contains(Opcs opcode)
    {
        return opcodes.contains(opcode);
    }

    public static VMOpcode fromOpcode(Opcs opcode)
    {
        return opcode == null ? null : BY_OPCODE.get(opcode);
    }

    public static VMOpcode fromAsmOpcode(int opcode)
    {
        return fromOpcode(Opcs.fromOpcode(opcode));
    }
}
