package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math;

import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math.Abstract.BinaryMathBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.NumericType;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;

public class DivideBranch extends BinaryMathBranch
{
    public DivideBranch()
    {
        super(VMOpcode.DIVIDE);
    }

    @Override
    protected void emitOperation(InsnBuilder ib, NumericType type)
    {
        switch (type)
        {
            case INT: ib.idiv(); break;
            case LONG: ib.ldiv(); break;
            case FLOAT: ib.fdiv(); break;
            case DOUBLE: ib.ddiv(); break;
        }
    }
}
