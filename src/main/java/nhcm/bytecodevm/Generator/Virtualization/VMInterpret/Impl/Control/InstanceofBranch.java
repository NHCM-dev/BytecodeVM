package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Control;

import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.tree.InsnList;

import java.util.Set;

public class InstanceofBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.INSTANCE_OF.getOpcodes();
    }

    @Override
    public InsnList generate(InterpretContext context, Opcs opcode)
    {
        InsnBuilder ib = new InsnBuilder();
        ib.aload(InterpretContext.CONSTANTS);
        context.nextToken(ib);
        context.vm.constantString.invokeStatic(ib);
        context.vm.loadOwner.invokeStatic(ib);
        popObject(ib, context);
        ib.invokeVirtual("java/lang/Class", "isInstance", "(Ljava/lang/Object;)Z");
        pushInt(ib, context);
        return ib.toInsnList();
    }
}
