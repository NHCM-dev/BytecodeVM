package nhcm.bytecodevm.AdvInsn;

import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.Type;

enum MathOp
{
    ADD("+"),
    SUBTRACT("-"),
    MULTIPLY("*"),
    DIVIDE("/"),
    REMAINDER("%"),
    BITWISE_AND("&"),
    BITWISE_OR("|"),
    BITWISE_XOR("^");

    final String symbol;

    MathOp(String symbol)
    {
        this.symbol = symbol;
    }

    void emit(InsnBuilder ib, Type type)
    {
        switch (this)
        {
            case ADD -> emitAdd(ib, type);
            case SUBTRACT -> emitSubtract(ib, type);
            case MULTIPLY -> emitMultiply(ib, type);
            case DIVIDE -> emitDivide(ib, type);
            case REMAINDER -> emitRemainder(ib, type);
            case BITWISE_AND -> {
                if (type.getSort() == Type.LONG) ib.land();
                else ib.iand();
            }
            case BITWISE_OR -> {
                if (type.getSort() == Type.LONG) ib.lor();
                else ib.ior();
            }
            case BITWISE_XOR -> {
                if (type.getSort() == Type.LONG) ib.lxor();
                else ib.ixor();
            }
        }
    }

    private static void emitAdd(InsnBuilder ib, Type type)
    {
        switch (type.getSort())
        {
            case Type.LONG -> ib.ladd();
            case Type.FLOAT -> ib.fadd();
            case Type.DOUBLE -> ib.dadd();
            default -> ib.iadd();
        }
    }

    private static void emitSubtract(InsnBuilder ib, Type type)
    {
        switch (type.getSort())
        {
            case Type.LONG -> ib.lsub();
            case Type.FLOAT -> ib.fsub();
            case Type.DOUBLE -> ib.dsub();
            default -> ib.isub();
        }
    }

    private static void emitMultiply(InsnBuilder ib, Type type)
    {
        switch (type.getSort())
        {
            case Type.LONG -> ib.lmul();
            case Type.FLOAT -> ib.fmul();
            case Type.DOUBLE -> ib.dmul();
            default -> ib.imul();
        }
    }

    private static void emitDivide(InsnBuilder ib, Type type)
    {
        switch (type.getSort())
        {
            case Type.LONG -> ib.ldiv();
            case Type.FLOAT -> ib.fdiv();
            case Type.DOUBLE -> ib.ddiv();
            default -> ib.idiv();
        }
    }

    private static void emitRemainder(InsnBuilder ib, Type type)
    {
        switch (type.getSort())
        {
            case Type.LONG -> ib.lrem();
            case Type.FLOAT -> ib.frem();
            case Type.DOUBLE -> ib.drem();
            default -> ib.irem();
        }
    }
}
