package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math.Abstract;

import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.AdvInsn.Expr;
import nhcm.bytecodevm.AdvInsn.Local;
import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.NumericType;

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
    public final void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        if (!vmOpcode.contains(opcode))
        {
            throw new IllegalArgumentException(opcode + " is not handled by " + vmOpcode);
        }

        NumericType type = NumericType.fromOpcode(opcode);
        Local value = context.rightValue(type);
        popNumber(ib, context, type, value);
        pushNumber(ib, context, type, operation(value));
    }

    protected abstract Expr operation(Expr value);
}
