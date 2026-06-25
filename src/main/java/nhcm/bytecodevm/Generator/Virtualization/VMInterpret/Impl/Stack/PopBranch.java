package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Stack;

import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;

import java.util.Set;

public class PopBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.POP.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        var width = context.intLocal("popWidth", InterpretContext.DUP_WIDTH_1);
        popObjectAndWidth(ib, context, InterpretContext.DUP_VALUE_1, InterpretContext.DUP_WIDTH_1);

        if (opcode == Opcs.POP2)
        {
            ib.ifCondition(
                    AdvInsnBuilder.notEqual(width, AdvInsnBuilder.constant(2)),
                    b -> popObject(b, context));
        }
    }
}
