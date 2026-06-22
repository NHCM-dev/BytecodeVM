package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math;

import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math.Abstract.BinaryMathBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.NumericType;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;

public class AddBranch extends BinaryMathBranch
{
    public AddBranch()
    {
        super(VMOpcode.ADD);
    }

    @Override
    protected void emitOperation(InsnBuilder ib, NumericType type)
    {
        switch (type)
        {
            case INT: ib.iadd(); break;
            case LONG: ib.ladd(); break;
            case FLOAT: ib.fadd(); break;
            case DOUBLE: ib.dadd(); break;
        }
    }
}
