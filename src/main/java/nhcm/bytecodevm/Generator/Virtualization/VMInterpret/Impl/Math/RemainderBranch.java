package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math;

import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.AdvInsn.Expr;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math.Abstract.BinaryMathBranch;

public class RemainderBranch extends BinaryMathBranch
{
    public RemainderBranch()
    {
        super(VMOpcode.REMAINDER);
    }

    @Override
    protected Expr operation(Expr left, Expr right)
    {
        return AdvInsnBuilder.remainder(left, right);
    }
}
