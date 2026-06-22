package nhcm.bytecodevm.Enums;

import org.objectweb.asm.Opcodes;

public enum Opcs
{
    NOP(Opcodes.NOP, false),
    ACONST_NULL(Opcodes.ACONST_NULL, false),
    ICONST_M1(Opcodes.ICONST_M1, false),
    ICONST_0(Opcodes.ICONST_0, false),
    ICONST_1(Opcodes.ICONST_1, false),
    ICONST_2(Opcodes.ICONST_2, false),
    ICONST_3(Opcodes.ICONST_3, false),
    ICONST_4(Opcodes.ICONST_4, false),
    ICONST_5(Opcodes.ICONST_5, false),
    LCONST_0(Opcodes.LCONST_0, false),
    LCONST_1(Opcodes.LCONST_1, false),
    FCONST_0(Opcodes.FCONST_0, false),
    FCONST_1(Opcodes.FCONST_1, false),
    FCONST_2(Opcodes.FCONST_2, false),
    DCONST_0(Opcodes.DCONST_0, false),
    DCONST_1(Opcodes.DCONST_1, false),
    BIPUSH(Opcodes.BIPUSH, true),
    SIPUSH(Opcodes.SIPUSH, true),
    LDC(Opcodes.LDC, OperandFormat.CONSTANT),
    ILOAD(Opcodes.ILOAD, true),
    LLOAD(Opcodes.LLOAD, true),
    FLOAD(Opcodes.FLOAD, true),
    DLOAD(Opcodes.DLOAD, true),
    ALOAD(Opcodes.ALOAD, true),
    IALOAD(Opcodes.IALOAD, false),
    LALOAD(Opcodes.LALOAD, false),
    FALOAD(Opcodes.FALOAD, false),
    DALOAD(Opcodes.DALOAD, false),
    AALOAD(Opcodes.AALOAD, false),
    BALOAD(Opcodes.BALOAD, false),
    CALOAD(Opcodes.CALOAD, false),
    SALOAD(Opcodes.SALOAD, false),
    ISTORE(Opcodes.ISTORE, true),
    LSTORE(Opcodes.LSTORE, true),
    FSTORE(Opcodes.FSTORE, true),
    DSTORE(Opcodes.DSTORE, true),
    ASTORE(Opcodes.ASTORE, true),
    IASTORE(Opcodes.IASTORE, false),
    LASTORE(Opcodes.LASTORE, false),
    FASTORE(Opcodes.FASTORE, false),
    DASTORE(Opcodes.DASTORE, false),
    AASTORE(Opcodes.AASTORE, false),
    BASTORE(Opcodes.BASTORE, false),
    CASTORE(Opcodes.CASTORE, false),
    SASTORE(Opcodes.SASTORE, false),
    POP(Opcodes.POP, false),
    POP2(Opcodes.POP2, false),
    DUP(Opcodes.DUP, false),
    DUP_X1(Opcodes.DUP_X1, false),
    DUP_X2(Opcodes.DUP_X2, false),
    DUP2(Opcodes.DUP2, false),
    DUP2_X1(Opcodes.DUP2_X1, false),
    DUP2_X2(Opcodes.DUP2_X2, false),
    SWAP(Opcodes.SWAP, false),
    IADD(Opcodes.IADD, false),
    LADD(Opcodes.LADD, false),
    FADD(Opcodes.FADD, false),
    DADD(Opcodes.DADD, false),
    ISUB(Opcodes.ISUB, false),
    LSUB(Opcodes.LSUB, false),
    FSUB(Opcodes.FSUB, false),
    DSUB(Opcodes.DSUB, false),
    IMUL(Opcodes.IMUL, false),
    LMUL(Opcodes.LMUL, false),
    FMUL(Opcodes.FMUL, false),
    DMUL(Opcodes.DMUL, false),
    IDIV(Opcodes.IDIV, false),
    LDIV(Opcodes.LDIV, false),
    FDIV(Opcodes.FDIV, false),
    DDIV(Opcodes.DDIV, false),
    IREM(Opcodes.IREM, false),
    LREM(Opcodes.LREM, false),
    FREM(Opcodes.FREM, false),
    DREM(Opcodes.DREM, false),
    INEG(Opcodes.INEG, false),
    LNEG(Opcodes.LNEG, false),
    FNEG(Opcodes.FNEG, false),
    DNEG(Opcodes.DNEG, false),
    ISHL(Opcodes.ISHL, false),
    LSHL(Opcodes.LSHL, false),
    ISHR(Opcodes.ISHR, false),
    LSHR(Opcodes.LSHR, false),
    IUSHR(Opcodes.IUSHR, false),
    LUSHR(Opcodes.LUSHR, false),
    IAND(Opcodes.IAND, false),
    LAND(Opcodes.LAND, false),
    IOR(Opcodes.IOR, false),
    LOR(Opcodes.LOR, false),
    IXOR(Opcodes.IXOR, false),
    LXOR(Opcodes.LXOR, false),
    IINC(Opcodes.IINC, OperandFormat.TWO_IMMEDIATES),
    I2L(Opcodes.I2L, false),
    I2F(Opcodes.I2F, false),
    I2D(Opcodes.I2D, false),
    L2I(Opcodes.L2I, false),
    L2F(Opcodes.L2F, false),
    L2D(Opcodes.L2D, false),
    F2I(Opcodes.F2I, false),
    F2L(Opcodes.F2L, false),
    F2D(Opcodes.F2D, false),
    D2I(Opcodes.D2I, false),
    D2L(Opcodes.D2L, false),
    D2F(Opcodes.D2F, false),
    I2B(Opcodes.I2B, false),
    I2C(Opcodes.I2C, false),
    I2S(Opcodes.I2S, false),
    LCMP(Opcodes.LCMP, false),
    FCMPL(Opcodes.FCMPL, false),
    FCMPG(Opcodes.FCMPG, false),
    DCMPL(Opcodes.DCMPL, false),
    DCMPG(Opcodes.DCMPG, false),
    IFEQ(Opcodes.IFEQ, true),
    IFNE(Opcodes.IFNE, true),
    IFLT(Opcodes.IFLT, true),
    IFGE(Opcodes.IFGE, true),
    IFGT(Opcodes.IFGT, true),
    IFLE(Opcodes.IFLE, true),
    IF_ICMPEQ(Opcodes.IF_ICMPEQ, true),
    IF_ICMPNE(Opcodes.IF_ICMPNE, true),
    IF_ICMPLT(Opcodes.IF_ICMPLT, true),
    IF_ICMPGE(Opcodes.IF_ICMPGE, true),
    IF_ICMPGT(Opcodes.IF_ICMPGT, true),
    IF_ICMPLE(Opcodes.IF_ICMPLE, true),
    IF_ACMPEQ(Opcodes.IF_ACMPEQ, true),
    IF_ACMPNE(Opcodes.IF_ACMPNE, true),
    GOTO(Opcodes.GOTO, true),
    TABLESWITCH(Opcodes.TABLESWITCH, OperandFormat.TABLE_SWITCH),
    LOOKUPSWITCH(Opcodes.LOOKUPSWITCH, OperandFormat.LOOKUP_SWITCH),
    IRETURN(Opcodes.IRETURN, false),
    LRETURN(Opcodes.LRETURN, false),
    FRETURN(Opcodes.FRETURN, false),
    DRETURN(Opcodes.DRETURN, false),
    ARETURN(Opcodes.ARETURN, false),
    RETURN(Opcodes.RETURN, false),
    GETSTATIC(Opcodes.GETSTATIC, OperandFormat.THREE_CONSTANTS),
    PUTSTATIC(Opcodes.PUTSTATIC, OperandFormat.THREE_CONSTANTS),
    GETFIELD(Opcodes.GETFIELD, OperandFormat.THREE_CONSTANTS),
    PUTFIELD(Opcodes.PUTFIELD, OperandFormat.THREE_CONSTANTS),
    INVOKEVIRTUAL(Opcodes.INVOKEVIRTUAL, OperandFormat.METHOD_REFERENCE),
    INVOKESPECIAL(Opcodes.INVOKESPECIAL, OperandFormat.METHOD_REFERENCE),
    INVOKESTATIC(Opcodes.INVOKESTATIC, OperandFormat.METHOD_REFERENCE),
    INVOKEINTERFACE(Opcodes.INVOKEINTERFACE, OperandFormat.METHOD_REFERENCE),
    INVOKEDYNAMIC(Opcodes.INVOKEDYNAMIC, OperandFormat.INVOKE_DYNAMIC),
    NEW(Opcodes.NEW, OperandFormat.CONSTANT),
    NEWARRAY(Opcodes.NEWARRAY, true),
    ANEWARRAY(Opcodes.ANEWARRAY, OperandFormat.CONSTANT),
    ARRAYLENGTH(Opcodes.ARRAYLENGTH, false),
    ATHROW(Opcodes.ATHROW, false),
    CHECKCAST(Opcodes.CHECKCAST, OperandFormat.CONSTANT),
    INSTANCEOF(Opcodes.INSTANCEOF, OperandFormat.CONSTANT),
    MONITORENTER(Opcodes.MONITORENTER, false),
    MONITOREXIT(Opcodes.MONITOREXIT, false),
    MULTIANEWARRAY(Opcodes.MULTIANEWARRAY, OperandFormat.MULTI_ARRAY),
    IFNULL(Opcodes.IFNULL, true),
    IFNONNULL(Opcodes.IFNONNULL, true);

    public final int opcode;
    public final boolean hasOperand;
    public final int operandCount;
    public final boolean referencesConstant;
    public final OperandFormat operandFormat;

    Opcs(int opcode, boolean hasOperand)
    {
        this(opcode, hasOperand ? OperandFormat.IMMEDIATE : OperandFormat.NONE);
    }

    Opcs(int opcode, OperandFormat operandFormat)
    {
        this.opcode = opcode;
        this.operandFormat = operandFormat;
        this.operandCount = operandFormat.operandCount;
        this.hasOperand = operandFormat != OperandFormat.NONE;
        this.referencesConstant = operandFormat.referencesConstant;
    }

    public int getOperandCount(int[] code, int operandStart)
    {
        return operandFormat.getOperandCount(code, operandStart);
    }

    public boolean isConstantOperand(int operandIndex)
    {
        return operandFormat.isConstantOperand(operandIndex);
    }

    public static Opcs fromOpcode(int opcode)
    {
        for (Opcs value : values())
        {
            if (value.opcode == opcode)
            {
                return value;
            }
        }
        return null;
    }

    public static Opcs fromOrdinal(int ordinal)
    {
        for (Opcs value : values())
        {
            if (value.ordinal() == ordinal)
            {
                return value;
            }
        }
        return null;
    }

    public enum OperandFormat
    {
        NONE(0, 0L, -1),
        IMMEDIATE(1, 0L, -1),
        CONSTANT(1, 1L, -1),
        TWO_IMMEDIATES(2, 0L, -1),
        THREE_CONSTANTS(3, 0b111L, -1),
        METHOD_REFERENCE(4, 0b111L, -1),
        MULTI_ARRAY(2, 0b1L, -1),
        INVOKE_DYNAMIC(-1, 0b111L, 4),
        TABLE_SWITCH(-1, 0L, -1),
        LOOKUP_SWITCH(-1, 0L, -1);

        public final int operandCount;
        public final boolean variableLength;
        public final boolean referencesConstant;

        private final long constantMask;
        private final int trailingConstantStart;

        OperandFormat(int operandCount, long constantMask, int trailingConstantStart)
        {
            this.operandCount = operandCount;
            this.variableLength = operandCount < 0;
            this.constantMask = constantMask;
            this.trailingConstantStart = trailingConstantStart;
            this.referencesConstant = constantMask != 0L || trailingConstantStart >= 0;
        }

        public int getOperandCount(int[] code, int operandStart)
        {
            switch (this)
            {
                case INVOKE_DYNAMIC:
                    requireAvailable(code, operandStart, 4);
                    return 4 + code[operandStart + 3];

                case TABLE_SWITCH:
                    requireAvailable(code, operandStart, 4);
                    return 4 + code[operandStart + 3];

                case LOOKUP_SWITCH:
                    requireAvailable(code, operandStart, 2);
                    return 2 + code[operandStart + 1] * 2;

                default:
                    return operandCount;
            }
        }

        public boolean isConstantOperand(int operandIndex)
        {
            if (operandIndex < 0)
            {
                throw new IndexOutOfBoundsException("operandIndex=" + operandIndex);
            }

            return operandIndex < Long.SIZE && (constantMask & (1L << operandIndex)) != 0L ||
                    trailingConstantStart >= 0 && operandIndex >= trailingConstantStart;
        }

        private static void requireAvailable(int[] code, int start, int count)
        {
            if (code == null || start < 0 || start + count > code.length)
            {
                throw new IllegalArgumentException("Truncated VM instruction operands");
            }
        }
    }
}
