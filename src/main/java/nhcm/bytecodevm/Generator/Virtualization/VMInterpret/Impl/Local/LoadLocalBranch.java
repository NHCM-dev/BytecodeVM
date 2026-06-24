package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Local;

import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.tree.InsnList;

import java.util.Set;

public class LoadLocalBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.LOAD_LOCAL.getOpcodes();
    }

    @Override
    public InsnList generate(InterpretContext context, Opcs opcode)
    {
        InsnBuilder ib = new InsnBuilder();
        context.loadFrame(ib);
        context.frame.locals.get(ib);
        context.nextToken(ib);
        ib.aaload();
        if (opcode == Opcs.LLOAD || opcode == Opcs.DLOAD)
        {
            pushObjectWithWidth(ib, context, 2);
        }
        else
        {
            pushObject(ib, context);
        }
        return ib.toInsnList();
    }
}
