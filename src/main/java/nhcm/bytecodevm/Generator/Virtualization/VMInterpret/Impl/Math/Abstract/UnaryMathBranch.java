package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math.Abstract;

import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.NumericType;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.tree.InsnList;

import java.util.Set;

public abstract class UnaryMathBranch extends InterpretBranch
{
    private final VMOpcode vmOpcode;

    protected UnaryMathBranch(VMOpcode vmOpcode)
    {
        this.vmOpcode = vmOpcode;
    }

    @Override
    public final Set<Opcs> opcodes()
    {
        return vmOpcode.getOpcodes();
    }

    @Override
    public final InsnList generate(InterpretContext context, Opcs opcode)
    {
        if (!vmOpcode.contains(opcode))
        {
            throw new IllegalArgumentException(opcode + " is not handled by " + vmOpcode);
        }

        NumericType type = NumericType.fromOpcode(opcode);
        InsnBuilder ib = new InsnBuilder();
        popNumber(ib, context, type, InterpretContext.RIGHT_VALUE);
        type.load(ib, InterpretContext.RIGHT_VALUE);
        emitOperation(ib, type);
        pushNumber(ib, context, type);
        return ib.toInsnList();
    }

    protected abstract void emitOperation(InsnBuilder ib, NumericType type);
}
