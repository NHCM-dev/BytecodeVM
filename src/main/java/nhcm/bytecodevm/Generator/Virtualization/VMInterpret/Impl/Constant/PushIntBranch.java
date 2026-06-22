package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Constant;

import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;

import java.util.Set;

public class PushIntBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.PUSH_INT.getOpcodes();
    }

    @Override
    public InsnList generate(InterpretContext context, Opcs opcode)
    {
        InsnBuilder ib = new InsnBuilder();
        if (opcode.hasOperand)
        {
            context.nextToken(ib);
        }
        else
        {
            ib.pushInt(opcode.opcode - Opcodes.ICONST_0);
        }
        pushInt(ib, context);
        return ib.toInsnList();
    }
}
