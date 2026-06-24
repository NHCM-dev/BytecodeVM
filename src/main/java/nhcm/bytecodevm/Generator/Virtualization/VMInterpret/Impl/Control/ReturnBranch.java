package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Control;

import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.tree.InsnList;

import java.util.Set;

public class ReturnBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.RETURN.getOpcodes();
    }

    @Override
    public InsnList generate(InterpretContext context, Opcs opcode)
    {
        if (!opcodes().contains(opcode))
        {
            throw new IllegalArgumentException("Unsupported return opcode: " + opcode);
        }

        InsnBuilder ib = new InsnBuilder();

        context.loadFrame(ib);
        if (opcode == Opcs.RETURN)
        {
            ib.aconstNull();
        }
        else
        {
            popObject(ib, context);
        }
        context.frame.returnValue.put(ib);

        context.loadFrame(ib);
        ib.iconst1();
        context.frame.returned.put(ib);

        ib._return();
        return ib.toInsnList();
    }

    @Override
    public boolean term(Opcs opcode)
    {
        return true;
    }
}
