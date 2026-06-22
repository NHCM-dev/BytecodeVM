package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math;

import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math.Abstract.ShiftMathBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.NumericType;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;

public class ShiftLeftBranch extends ShiftMathBranch
{
    public ShiftLeftBranch()
    {
        super(VMOpcode.SHIFT_LEFT);
    }

    @Override
    protected void emitOperation(InsnBuilder ib, NumericType type)
    {
        switch (type)
        {
            case INT: ib.ishl(); break;
            case LONG: ib.lshl(); break;
            default: throw new IllegalArgumentException("Unsupported shift type: " + type);
        }
    }
}
