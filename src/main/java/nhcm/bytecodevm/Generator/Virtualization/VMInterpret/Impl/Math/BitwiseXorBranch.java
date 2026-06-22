package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math;

import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math.Abstract.BinaryMathBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.NumericType;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;

public class BitwiseXorBranch extends BinaryMathBranch
{
    public BitwiseXorBranch()
    {
        super(VMOpcode.BITWISE_XOR);
    }

    @Override
    protected void emitOperation(InsnBuilder ib, NumericType type)
    {
        switch (type)
        {
            case INT: ib.ixor(); break;
            case LONG: ib.lxor(); break;
            default: throw new IllegalArgumentException("Unsupported XOR type: " + type);
        }
    }
}
