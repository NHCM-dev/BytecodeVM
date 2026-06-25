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

public abstract class ShiftMathBranch extends InterpretBranch
{
    private final VMOpcode vmOpcode;

    protected ShiftMathBranch(VMOpcode vmOpcode)
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

        NumericType valueType = NumericType.fromOpcode(opcode);
        Local distance = context.rightValue(NumericType.INT);
        Local value = context.leftValue(valueType);
        popNumber(ib, context, NumericType.INT, distance);
        popNumber(ib, context, valueType, value);
        pushNumber(ib, context, valueType, operation(value, distance));
    }

    protected abstract Expr operation(Expr value, Expr distance);
}
