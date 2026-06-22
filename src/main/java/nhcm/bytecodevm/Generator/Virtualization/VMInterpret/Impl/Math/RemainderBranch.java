package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math;

import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math.Abstract.BinaryMathBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.NumericType;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;

public class RemainderBranch extends BinaryMathBranch
{
    public RemainderBranch()
    {
        super(VMOpcode.REMAINDER);
    }

    @Override
    protected void emitOperation(InsnBuilder ib, NumericType type)
    {
        switch (type)
        {
            case INT: ib.irem(); break;
            case LONG: ib.lrem(); break;
            case FLOAT: ib.frem(); break;
            case DOUBLE: ib.drem(); break;
        }
    }
}
