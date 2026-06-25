package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math;

import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.AdvInsn.Expr;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math.Abstract.UnaryMathBranch;

public class NegateBranch extends UnaryMathBranch
{
    public NegateBranch()
    {
        super(VMOpcode.NEGATE);
    }

    @Override
    protected Expr operation(Expr value)
    {
        return AdvInsnBuilder.negative(value);
    }
}
