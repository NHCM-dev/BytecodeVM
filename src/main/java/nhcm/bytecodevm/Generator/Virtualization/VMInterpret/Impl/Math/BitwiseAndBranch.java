package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math;

import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.AdvInsn.Expr;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math.Abstract.BinaryMathBranch;

public class BitwiseAndBranch extends BinaryMathBranch
{
    public BitwiseAndBranch()
    {
        super(VMOpcode.BITWISE_AND);
    }

    @Override
    protected Expr operation(Expr left, Expr right)
    {
        return AdvInsnBuilder.bitAnd(left, right);
    }
}
