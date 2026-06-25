package nhcm.bytecodevm.AdvInsn;

import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.tree.LabelNode;

@FunctionalInterface
interface JumpEmitter
{
    void jumpIfFalse(InsnBuilder builder, LabelNode falseLabel);
}
