package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Control;

import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;

import java.util.Set;

public class ReturnBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.RETURN.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        if (!opcodes().contains(opcode))
        {
            throw new IllegalArgumentException("Unsupported return opcode: " + opcode);
        }

        if (opcode == Opcs.RETURN)
        {
            ib.set(context.frameReturnValue(), AdvInsnBuilder.nullValue("java/lang/Object"));
        }
        else
        {
            popObject(ib, context);
            ib.set(context.frameReturnValue(), context.stackObject());
        }
        ib.set(context.frameReturned(), AdvInsnBuilder.constant(true));
        ib.returnVoid();
    }

    @Override
    public boolean term(Opcs opcode)
    {
        return true;
    }
}
