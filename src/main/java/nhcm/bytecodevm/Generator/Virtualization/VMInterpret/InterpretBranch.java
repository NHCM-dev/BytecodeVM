package nhcm.bytecodevm.Generator.Virtualization.VMInterpret;

import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;

import java.util.Set;

public abstract class InterpretBranch
{
    public abstract Set<Opcs> opcodes();

    public abstract InsnList generate(InterpretContext context, Opcs opcode);

    public boolean term(Opcs opcode)
    {
        return false;
    }

    protected static void popObject(InsnBuilder ib, InterpretContext context)
    {
        context.loadFrame(ib);
        ib.invokeVirtual(context.frameClassName, "pop", "()Ljava/lang/Object;");
    }

    protected static void popObject(InsnBuilder ib, InterpretContext context, int local)
    {
        popObject(ib, context);
        ib.astore(local);
    }

    protected static void popNumber(InsnBuilder ib, InterpretContext context, NumericType type)
    {
        context.loadFrame(ib);
        ib.invokeVirtual(context.frameClassName, type.framePopName(), type.framePopDescriptor());
    }

    protected static void popNumber(InsnBuilder ib, InterpretContext context, NumericType type, int local)
    {
        popNumber(ib, context, type);
        type.store(ib, local);
    }

    protected static void popInt(InsnBuilder ib, InterpretContext context)
    {
        popNumber(ib, context, NumericType.INT);
    }

    protected static void popInt(InsnBuilder ib, InterpretContext context, int local)
    {
        popNumber(ib, context, NumericType.INT, local);
    }

    protected static void popLong(InsnBuilder ib, InterpretContext context)
    {
        popNumber(ib, context, NumericType.LONG);
    }

    protected static void popLong(InsnBuilder ib, InterpretContext context, int local)
    {
        popNumber(ib, context, NumericType.LONG, local);
    }

    protected static void popFloat(InsnBuilder ib, InterpretContext context)
    {
        popNumber(ib, context, NumericType.FLOAT);
    }

    protected static void popFloat(InsnBuilder ib, InterpretContext context, int local)
    {
        popNumber(ib, context, NumericType.FLOAT, local);
    }

    protected static void popDouble(InsnBuilder ib, InterpretContext context)
    {
        popNumber(ib, context, NumericType.DOUBLE);
    }

    protected static void popDouble(InsnBuilder ib, InterpretContext context, int local)
    {
        popNumber(ib, context, NumericType.DOUBLE, local);
    }

    /** Pushes the Object currently on top of the generated JVM operand stack. */
    protected static void pushObject(InsnBuilder ib, InterpretContext context)
    {
        pushObjectWithWidth(ib, context, 1);
    }

    protected static void pushObjectWithWidth(
            InsnBuilder ib,
            InterpretContext context,
            int width)
    {
        context.loadFrame(ib);
        ib.swap();
        ib.pushInt(width);
        ib.invokeVirtual(context.frameClassName, "push", "(Ljava/lang/Object;I)V");
    }

    protected static void pushObject(InsnBuilder ib, InterpretContext context, int local)
    {
        ib.aload(local);
        pushObject(ib, context);
    }

    protected static void pushObjectWithWidth(
            InsnBuilder ib,
            InterpretContext context,
            int objectLocal,
            int widthLocal)
    {
        context.loadFrame(ib);
        ib.aload(objectLocal);
        ib.iload(widthLocal);
        ib.invokeVirtual(context.frameClassName, "push", "(Ljava/lang/Object;I)V");
    }

    /** Boxes and pushes the primitive currently on top of the JVM operand stack. */
    protected static void pushNumber(InsnBuilder ib, InterpretContext context, NumericType type)
    {
        type.store(ib, InterpretContext.RIGHT_VALUE);
        context.loadFrame(ib);
        type.load(ib, InterpretContext.RIGHT_VALUE);
        ib.invokeVirtual(context.frameClassName, type.framePushName(), type.framePushDescriptor());
    }

    protected static void pushNumber(InsnBuilder ib, InterpretContext context, NumericType type, int local)
    {
        type.load(ib, local);
        pushNumber(ib, context, type);
    }

    protected static void pushInt(InsnBuilder ib, InterpretContext context)
    {
        pushNumber(ib, context, NumericType.INT);
    }

    protected static void pushInt(InsnBuilder ib, InterpretContext context, int local)
    {
        pushNumber(ib, context, NumericType.INT, local);
    }

    protected static void pushLong(InsnBuilder ib, InterpretContext context)
    {
        pushNumber(ib, context, NumericType.LONG);
    }

    protected static void pushLong(InsnBuilder ib, InterpretContext context, int local)
    {
        pushNumber(ib, context, NumericType.LONG, local);
    }

    protected static void pushFloat(InsnBuilder ib, InterpretContext context)
    {
        pushNumber(ib, context, NumericType.FLOAT);
    }

    protected static void pushFloat(InsnBuilder ib, InterpretContext context, int local)
    {
        pushNumber(ib, context, NumericType.FLOAT, local);
    }

    protected static void pushDouble(InsnBuilder ib, InterpretContext context)
    {
        pushNumber(ib, context, NumericType.DOUBLE);
    }

    protected static void pushDouble(InsnBuilder ib, InterpretContext context, int local)
    {
        pushNumber(ib, context, NumericType.DOUBLE, local);
    }

    protected static void jumpIfCategory2(InsnBuilder ib, int widthLocal, LabelNode target)
    {
        ib.iload(widthLocal);
        ib.iconst2();
        ib.ifIcmpEq(target);
    }
}
