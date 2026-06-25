package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Constant;

import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.NumericType;
import org.objectweb.asm.Opcodes;

import java.util.Set;

public class PushLongBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.PUSH_LONG.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        pushNumber(ib, context, NumericType.LONG, AdvInsnBuilder.constant((long) (opcode.opcode - Opcodes.LCONST_0)));
    }
}
