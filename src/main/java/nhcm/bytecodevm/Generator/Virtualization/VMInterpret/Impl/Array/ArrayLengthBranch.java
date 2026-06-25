package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Array;

import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;

import java.util.Set;

public class ArrayLengthBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.ARRAY_LENGTH.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        popObject(ib, context);
        pushInt(ib, context, AdvInsnBuilder.callStatic(
                "java/lang/reflect/Array",
                "getLength",
                "I",
                context.stackObject()));
    }
}
