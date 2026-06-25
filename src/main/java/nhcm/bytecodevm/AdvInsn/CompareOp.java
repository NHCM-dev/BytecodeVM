package nhcm.bytecodevm.AdvInsn;

import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.tree.LabelNode;

enum CompareOp
{
    EQUAL("=="),
    NOT_EQUAL("!="),
    LESS_THAN("<"),
    LESS_OR_EQUAL("<="),
    GREATER_THAN(">"),
    GREATER_OR_EQUAL(">=");

    final String symbol;

    CompareOp(String symbol)
    {
        this.symbol = symbol;
    }

    void emitIntFalseJump(InsnBuilder ib, LabelNode label)
    {
        switch (this)
        {
            case EQUAL -> ib.ifIcmpNe(label);
            case NOT_EQUAL -> ib.ifIcmpEq(label);
            case LESS_THAN -> ib.ifIcmpGe(label);
            case LESS_OR_EQUAL -> ib.ifIcmpGt(label);
            case GREATER_THAN -> ib.ifIcmpLe(label);
            case GREATER_OR_EQUAL -> ib.ifIcmpLt(label);
        }
    }

    void emitZeroFalseJump(InsnBuilder ib, LabelNode label)
    {
        switch (this)
        {
            case EQUAL -> ib.ifne(label);
            case NOT_EQUAL -> ib.ifeq(label);
            case LESS_THAN -> ib.ifge(label);
            case LESS_OR_EQUAL -> ib.ifgt(label);
            case GREATER_THAN -> ib.ifle(label);
            case GREATER_OR_EQUAL -> ib.iflt(label);
        }
    }
}
