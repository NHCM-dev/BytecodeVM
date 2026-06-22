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
        if (!opcodes().contains(opcode))
        {
            throw new IllegalArgumentException("Unsupported local load opcode: " + opcode);
        }

        InsnBuilder ib = new InsnBuilder();
        context.loadFrame(ib);
        context.loadFrame(ib);
        ib.getField(context.frameClassName, "locals", "[Ljava/lang/Object;");
        context.nextToken(ib);
        ib.aaload();
        context.invokeFramePush(ib);
        return ib.toInsnList();
    }
}
