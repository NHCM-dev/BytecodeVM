package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math;

import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.AdvInsn.Expr;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math.Abstract.ShiftMathBranch;

public class ShiftRightBranch extends ShiftMathBranch
{
    public ShiftRightBranch()
    {
        super(VMOpcode.SHIFT_RIGHT);
    }

    @Override
    protected Expr operation(Expr value, Expr distance)
    {
        return AdvInsnBuilder.shiftRight(value, distance);
    }
}
