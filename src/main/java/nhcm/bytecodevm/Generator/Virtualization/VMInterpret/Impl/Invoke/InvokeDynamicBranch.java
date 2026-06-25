package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Invoke;

import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;

import java.util.Set;

public class InvokeDynamicBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.INVOKE_DYNAMIC.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        throw new IllegalStateException("Invokedynamic nodes should be replaced by bridging methods normal invokes");
    }
}
