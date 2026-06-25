package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Local;

import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.AdvInsn.Local;
import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;

import java.util.Set;

public class StoreLocalBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.STORE_LOCAL.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        Local localIndex = context.intLocal("localIndex", InterpretContext.RIGHT_VALUE);
        Local value = context.objectLocal("localValue", InterpretContext.LEFT_VALUE);
        context.nextToken(ib, localIndex);
        popObject(ib, context, value);
        ib.setArray(context.locals(), localIndex, value);
    }
}
