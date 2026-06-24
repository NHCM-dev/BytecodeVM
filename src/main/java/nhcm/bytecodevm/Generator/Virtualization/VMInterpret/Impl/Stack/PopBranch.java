package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Stack;

import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;

import java.util.Set;

public class PopBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.POP.getOpcodes();
    }

    @Override
    public InsnList generate(InterpretContext context, Opcs opcode)
    {
        InsnBuilder ib = new InsnBuilder();
        context.loadFrame(ib);
        context.frame.peekWidth.invokeVirtual(ib);
        ib.istore(InterpretContext.DUP_WIDTH_1);
        popObject(ib, context, InterpretContext.DUP_VALUE_1);

        if (opcode == Opcs.POP2)
        {
            LabelNode done = new LabelNode();
            jumpIfCategory2(ib, InterpretContext.DUP_WIDTH_1, done);
            popObject(ib, context);
            ib.pop();
            ib.label(done);
        }
        return ib.toInsnList();
    }
}
