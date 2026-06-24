package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Control;

import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.tree.InsnList;

import java.util.Set;

public class GotoBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.GOTO.getOpcodes();
    }

    @Override
    public InsnList generate(InterpretContext context, Opcs opcode)
    {
        InsnBuilder ib = new InsnBuilder();

        context.loadFrame(ib);
        context.nextToken(ib);
        context.frame.programCounter.put(ib);

        return ib.toInsnList();
    }
}
