package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math;

import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math.Abstract.BinaryMathBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.NumericType;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;

public class BitwiseAndBranch extends BinaryMathBranch
{
    public BitwiseAndBranch()
    {
        super(VMOpcode.BITWISE_AND);
    }

    @Override
    protected void emitOperation(InsnBuilder ib, NumericType type)
    {
        switch (type)
        {
            case INT: ib.iand(); break;
            case LONG: ib.land(); break;
            default: throw new IllegalArgumentException("Unsupported AND type: " + type);
        }
    }
}
