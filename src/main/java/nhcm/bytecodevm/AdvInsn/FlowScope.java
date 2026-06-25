package nhcm.bytecodevm.AdvInsn;

import org.objectweb.asm.tree.LabelNode;

record FlowScope(LabelNode continueLabel, LabelNode breakLabel)
{
}
