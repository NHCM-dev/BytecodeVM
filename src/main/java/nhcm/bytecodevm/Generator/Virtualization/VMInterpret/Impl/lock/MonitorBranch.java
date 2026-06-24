package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.lock;

import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.tree.InsnList;

import java.util.Set;

public class MonitorBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.MONITOR.getOpcodes();
    }

    @Override
    public InsnList generate(InterpretContext context, Opcs opcode)
    {
        InsnBuilder ib = new InsnBuilder();
        popObject(ib, context);

        switch (opcode)
        {
            case MONITORENTER -> context.vm.monitorEnter.invokeStatic(ib);
            case MONITOREXIT -> context.vm.monitorExit.invokeStatic(ib);
            default -> throw new IllegalArgumentException("Unsupported monitor opcode: " + opcode);
        }

        return ib.toInsnList();
    }
}
