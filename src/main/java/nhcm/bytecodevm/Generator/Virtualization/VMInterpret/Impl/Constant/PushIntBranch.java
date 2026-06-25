package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Constant;

import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.AdvInsn.Local;
import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.NumericType;
import org.objectweb.asm.Opcodes;

import java.util.Set;

public class PushIntBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.PUSH_INT.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        if (opcode.hasOperand)
        {
            Local value = context.intLocal("intConstant", InterpretContext.RIGHT_VALUE);
            context.nextToken(ib, value);
            pushNumber(ib, context, NumericType.INT, value);
            return;
        }
        pushNumber(ib, context, NumericType.INT, AdvInsnBuilder.constant(opcode.opcode - Opcodes.ICONST_0));
    }
}
