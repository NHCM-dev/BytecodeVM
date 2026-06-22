package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math;

import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math.Abstract.ShiftMathBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.NumericType;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;

public class ShiftRightBranch extends ShiftMathBranch
{
    public ShiftRightBranch()
    {
        super(VMOpcode.SHIFT_RIGHT);
    }

    @Override
    protected void emitOperation(InsnBuilder ib, NumericType type)
    {
        switch (type)
        {
            case INT: ib.ishr(); break;
            case LONG: ib.lshr(); break;
            default: throw new IllegalArgumentException("Unsupported shift type: " + type);
        }
    }
}
