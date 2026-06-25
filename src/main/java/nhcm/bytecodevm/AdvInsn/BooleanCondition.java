package nhcm.bytecodevm.AdvInsn;

import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.tree.LabelNode;

record BooleanCondition(Expr value, boolean expectedFalse) implements Condition
{
    @Override
    public void jumpIfFalse(InsnBuilder builder, LabelNode falseLabel)
    {
        value.emit(builder);
        if (expectedFalse)
        {
            builder.ifne(falseLabel);
        }
        else
        {
            builder.ifeq(falseLabel);
        }
    }

    @Override
    public String source()
    {
        return expectedFalse ? "!(" + value.source() + ")" : value.source();
    }
}
