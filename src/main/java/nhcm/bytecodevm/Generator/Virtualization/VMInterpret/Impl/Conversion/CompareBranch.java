package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Conversion;

import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.NumericType;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.tree.InsnList;

import java.util.Set;

public class CompareBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.COMPARE.getOpcodes();
    }

    @Override
    public InsnList generate(InterpretContext context, Opcs opcode)
    {
        InsnBuilder ib = new InsnBuilder();

        NumericType type = NumericType.fromOpcode(opcode);

        popNumber(ib, context, type, InterpretContext.RIGHT_VALUE);
        popNumber(ib, context, type, InterpretContext.LEFT_VALUE);

        type.load(ib, InterpretContext.LEFT_VALUE);
        type.load(ib, InterpretContext.RIGHT_VALUE);

        switch (opcode)
        {
            case LCMP -> ib.lcmp();

            case FCMPL -> ib.fcmpl();
            case FCMPG -> ib.fcmpg();

            case DCMPL -> ib.dcmpl();
            case DCMPG -> ib.dcmpg();
        }

        pushInt(ib, context);
        return ib.toInsnList();
    }
}
