package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Object;

import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;

import java.util.Set;

public class CastBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.CAST.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        var classIndex = context.intLocal("classIndex", InterpretContext.JUMP_TARGET);
        var targetClass = context.local("targetClass", "java/lang/Class", InterpretContext.FIELD_VALUE);
        context.nextToken(ib, classIndex);
        ib.set(targetClass, context.loadClass(context.constantString(classIndex)));

        popObject(ib, context);
        pushObject(ib, context, AdvInsnBuilder.callVirtual(
                targetClass,
                "java/lang/Class",
                "cast",
                "java/lang/Object",
                context.stackObject()));
    }
}
