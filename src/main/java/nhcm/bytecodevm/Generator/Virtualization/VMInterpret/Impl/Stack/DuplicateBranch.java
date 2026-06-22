package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Stack;

import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.tree.InsnList;

import java.util.Set;

public class DuplicateBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.DUPLICATE.getOpcodes();
    }

    @Override
    public InsnList generate(InterpretContext context, Opcs opcode)
    {
        InsnBuilder ib = new InsnBuilder();
        context.loadFrame(ib); // 给 frame.push 准备 receiver

        context.loadFrame(ib);
        ib.getField(context.frameClassName, "stack", "[Ljava/lang/Object;");

        context.loadFrame(ib);
        ib.getField(context.frameClassName, "stackPointer", "I");

        ib.iconst1();
        ib.isub();
        ib.aaload();

        context.invokeFramePush(ib);
        return ib.toInsnList();
    }
}
