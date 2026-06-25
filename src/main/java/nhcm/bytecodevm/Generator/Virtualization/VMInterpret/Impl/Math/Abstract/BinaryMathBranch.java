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

public abstract class BinaryMathBranch extends InterpretBranch
{
    private final VMOpcode vmOpcode;

    protected BinaryMathBranch(VMOpcode vmOpcode)
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
        Local right = context.rightValue(type);
        Local left = context.leftValue(type);
        popNumber(ib, context, type, right);
        popNumber(ib, context, type, left);
        pushNumber(ib, context, type, operation(left, right));
    }

    protected abstract Expr operation(Expr left, Expr right);
}
