package nhcm.bytecodevm.AdvInsn;

import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.tree.LabelNode;

public interface Condition
{
    default String source()
    {
        return "<condition>";
    }

    default void jumpIfTrue(InsnBuilder builder, LabelNode trueLabel)
    {
        LabelNode falseLabel = new LabelNode();
        jumpIfFalse(builder, falseLabel);
        builder.goto_(trueLabel);
        builder.label(falseLabel);
    }

    default void jumpIfTrue(AdvInsnBuilder builder, LabelNode trueLabel)
    {
        jumpIfTrue(builder.rawBuilder(), trueLabel);
    }

    void jumpIfFalse(InsnBuilder builder, LabelNode falseLabel);

    default void jumpIfFalse(AdvInsnBuilder builder, LabelNode falseLabel)
    {
        jumpIfFalse(builder.rawBuilder(), falseLabel);
    }
}
