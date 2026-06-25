package nhcm.bytecodevm.AdvInsn;

import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.Type;

enum ShiftOp
{
    LEFT("<<"),
    RIGHT(">>"),
    UNSIGNED_RIGHT(">>>");

    final String symbol;

    ShiftOp(String symbol)
    {
        this.symbol = symbol;
    }

    void emit(InsnBuilder ib, Type leftType)
    {
        switch (this)
        {
            case LEFT -> {
                if (leftType.getSort() == Type.LONG) ib.lshl();
                else ib.ishl();
            }
            case RIGHT -> {
                if (leftType.getSort() == Type.LONG) ib.lshr();
                else ib.ishr();
            }
            case UNSIGNED_RIGHT -> {
                if (leftType.getSort() == Type.LONG) ib.lushr();
                else ib.iushr();
            }
        }
    }
}
