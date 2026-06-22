package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math;

import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math.Abstract.BinaryMathBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.NumericType;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;

public class MultiplyBranch extends BinaryMathBranch
{
    public MultiplyBranch()
    {
        super(VMOpcode.MULTIPLY);
    }

    @Override
    protected void emitOperation(InsnBuilder ib, NumericType type)
    {
        switch (type)
        {
            case INT: ib.imul(); break;
            case LONG: ib.lmul(); break;
            case FLOAT: ib.fmul(); break;
            case DOUBLE: ib.dmul(); break;
        }
    }
}
