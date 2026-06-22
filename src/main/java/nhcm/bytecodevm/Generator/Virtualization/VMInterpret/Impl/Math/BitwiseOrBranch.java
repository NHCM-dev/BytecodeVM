package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math;

import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math.Abstract.BinaryMathBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.NumericType;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;

public class BitwiseOrBranch extends BinaryMathBranch
{
    public BitwiseOrBranch()
    {
        super(VMOpcode.BITWISE_OR);
    }

    @Override
    protected void emitOperation(InsnBuilder ib, NumericType type)
    {
        switch (type)
        {
            case INT: ib.ior(); break;
            case LONG: ib.lor(); break;
            default: throw new IllegalArgumentException("Unsupported OR type: " + type);
        }
    }
}
