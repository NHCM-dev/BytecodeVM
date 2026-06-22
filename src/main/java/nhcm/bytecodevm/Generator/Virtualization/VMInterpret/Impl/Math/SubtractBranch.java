package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math;

import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math.Abstract.BinaryMathBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.NumericType;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;

public class SubtractBranch extends BinaryMathBranch
{
    public SubtractBranch()
    {
        super(VMOpcode.SUBTRACT);
    }

    @Override
    protected void emitOperation(InsnBuilder ib, NumericType type)
    {
        switch (type)
        {
            case INT: ib.isub(); break;
            case LONG: ib.lsub(); break;
            case FLOAT: ib.fsub(); break;
            case DOUBLE: ib.dsub(); break;
        }
    }
}
