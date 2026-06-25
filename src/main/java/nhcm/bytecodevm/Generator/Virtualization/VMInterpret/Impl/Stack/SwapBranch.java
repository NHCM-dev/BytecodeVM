package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Stack;

import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;

import java.util.Set;

public class SwapBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.SWAP.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        popObject(ib, context, InterpretContext.RIGHT_VALUE);
        popObject(ib, context, InterpretContext.LEFT_VALUE);
        pushObject(ib, context, InterpretContext.RIGHT_VALUE);
        pushObject(ib, context, InterpretContext.LEFT_VALUE);
    }
}
