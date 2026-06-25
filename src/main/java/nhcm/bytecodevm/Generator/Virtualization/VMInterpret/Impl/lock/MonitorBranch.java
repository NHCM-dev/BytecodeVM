package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.lock;

import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;

import java.util.Set;

public class MonitorBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.MONITOR.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        popObject(ib, context);
        switch (opcode)
        {
            case MONITORENTER -> ib.directCall(AdvInsnBuilder.callStatic(
                    context.vm.owner,
                    context.vm.monitorEnter.name(),
                    "V",
                    context.stackObject()));
            case MONITOREXIT -> ib.directCall(AdvInsnBuilder.callStatic(
                    context.vm.owner,
                    context.vm.monitorExit.name(),
                    "V",
                    context.stackObject()));
            default -> throw new IllegalArgumentException("Unsupported monitor opcode: " + opcode);
        }
    }
}
