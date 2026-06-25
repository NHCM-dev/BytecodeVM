package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math;

import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.AdvInsn.Expr;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math.Abstract.ShiftMathBranch;

public class ShiftLeftBranch extends ShiftMathBranch
{
    public ShiftLeftBranch()
    {
        super(VMOpcode.SHIFT_LEFT);
    }

    @Override
    protected Expr operation(Expr value, Expr distance)
    {
        return AdvInsnBuilder.shiftLeft(value, distance);
    }
}
