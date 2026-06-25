package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Object;

import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;

import java.util.Set;

public class NewObjectBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.NEW_OBJECT.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        var classIndex = context.intLocal("classIndex", InterpretContext.JUMP_TARGET);
        var marker = context.local("identityMarker", "[Ljava/lang/Object;", InterpretContext.FIELD_VALUE);

        context.nextToken(ib, classIndex);
        ib.set(marker, AdvInsnBuilder.newArray("java/lang/Object", AdvInsnBuilder.constant(1)));
        ib.setArray(marker, AdvInsnBuilder.constant(0), AdvInsnBuilder.arrayAt(context.constants(), classIndex));
        pushObject(ib, context, marker);
    }
}
