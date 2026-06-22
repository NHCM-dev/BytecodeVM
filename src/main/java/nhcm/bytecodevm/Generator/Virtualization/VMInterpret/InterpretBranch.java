package nhcm.bytecodevm.Generator.Virtualization.VMInterpret;

import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.tree.InsnList;

import java.util.Set;

public abstract class InterpretBranch
{
    public abstract Set<Opcs> opcodes();

    public abstract InsnList generate(InterpretContext context, Opcs opcode);

    public boolean term(Opcs opcode)
    {
        return false;
    }

    protected static void popInt(InsnBuilder ib, InterpretContext context, int local)
    {
        popObject(ib, context, "java/lang/Integer", "intValue", "()I");
        ib.istore(local);
    }

    protected static void popLong(InsnBuilder ib, InterpretContext context, int local)
    {
        popObject(ib, context, "java/lang/Long", "longValue", "()J");
        ib.lstore(local);
    }

    protected static void popFloat(InsnBuilder ib, InterpretContext context, int local)
    {
        popObject(ib, context, "java/lang/Float", "floatValue", "()F");
        ib.fstore(local);
    }

    protected static void popDouble(InsnBuilder ib, InterpretContext context, int local)
    {
        popObject(ib, context, "java/lang/Double", "doubleValue", "()D");
        ib.dstore(local);
    }

    protected static void popObject(
            InsnBuilder ib,
            InterpretContext context,
            String wrapper,
            String unboxMethod,
            String unboxDescriptor)
    {
        ib.aload(InterpretContext.FRAME);
        ib.invokeVirtual(context.frameClassName, "pop", "()Ljava/lang/Object;");
        ib.checkCast(wrapper);
        ib.invokeVirtual(wrapper, unboxMethod, unboxDescriptor);
    }
}
