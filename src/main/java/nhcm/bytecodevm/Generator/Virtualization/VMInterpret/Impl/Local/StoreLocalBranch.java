package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Local;

import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.tree.InsnList;

import java.util.Set;

public class StoreLocalBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.STORE_LOCAL.getOpcodes();
    }

    @Override
    public InsnList generate(InterpretContext context, Opcs opcode)
    {
        InsnBuilder ib = new InsnBuilder();
        context.loadFrame(ib);
        ib.getField(context.frameClassName, "locals", "[Ljava/lang/Object;");
        context.nextToken(ib);
        popObject(ib, context);
        ib.aastore();
        return ib.toInsnList();
    }
}
