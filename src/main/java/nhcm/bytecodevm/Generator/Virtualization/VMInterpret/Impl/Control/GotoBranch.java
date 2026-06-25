package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Control;

import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;

import java.util.Set;

public class GotoBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.GOTO.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        var jumpTarget = context.intLocal("jumpTarget", InterpretContext.JUMP_TARGET);
        context.nextToken(ib, jumpTarget);
        ib.set(context.frameProgramCounter(), jumpTarget);
    }
}
