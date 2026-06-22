package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math;

import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math.Abstract.UnaryMathBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.NumericType;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;

public class NegateBranch extends UnaryMathBranch
{
    public NegateBranch()
    {
        super(VMOpcode.NEGATE);
    }

    @Override
    protected void emitOperation(InsnBuilder ib, NumericType type)
    {
        switch (type)
        {
            case INT: ib.ineg(); break;
            case LONG: ib.lneg(); break;
            case FLOAT: ib.fneg(); break;
            case DOUBLE: ib.dneg(); break;
        }
    }
}
