package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Local;

import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.AdvInsn.Local;
import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;

import java.util.Set;

public class LoadLocalBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.LOAD_LOCAL.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        Local localIndex = context.intLocal("localIndex", InterpretContext.RIGHT_VALUE);
        context.nextOperand(ib, localIndex);
        if (opcode == Opcs.LLOAD || opcode == Opcs.DLOAD)
        {
            pushObjectWithWidth(ib, context, AdvInsnBuilder.arrayAt(context.locals(), localIndex), AdvInsnBuilder.constant(2));
        }
        else
        {
            pushObject(ib, context, AdvInsnBuilder.arrayAt(context.locals(), localIndex));
        }
    }
}
