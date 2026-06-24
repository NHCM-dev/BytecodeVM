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
        LabelNode objectValue = new LabelNode();
        LabelNode longValue = new LabelNode();
        LabelNode floatValue = new LabelNode();
        LabelNode doubleValue = new LabelNode();
        LabelNode done = new LabelNode();

        emitRawPopIndexAndType(ib, context);
        emitLoadAndClearBoxedStackValue(ib, context);

        ib.iload(InterpretContext.STACK_TYPE);
        ib.iconst1();
        ib.ifIcmpNe(longValue);
        loadStackWord(ib, context);
        ib.l2i();
        ib.invokeStatic("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;");
        ib.goto_(done);

        ib.label(longValue);
        ib.iload(InterpretContext.STACK_TYPE);
        ib.iconst2();
        ib.ifIcmpNe(floatValue);
        loadStackWord(ib, context);
        ib.invokeStatic("java/lang/Long", "valueOf", "(J)Ljava/lang/Long;");
        ib.goto_(done);

        ib.label(floatValue);
        ib.iload(InterpretContext.STACK_TYPE);
        ib.iconst3();
        ib.ifIcmpNe(doubleValue);
        loadStackWord(ib, context);
        ib.l2i();
        ib.invokeStatic("java/lang/Float", "intBitsToFloat", "(I)F");
        ib.invokeStatic("java/lang/Float", "valueOf", "(F)Ljava/lang/Float;");
        ib.goto_(done);

        ib.label(doubleValue);
        ib.iload(InterpretContext.STACK_TYPE);
        ib.iconst4();
        ib.ifIcmpNe(objectValue);
        loadStackWord(ib, context);
        ib.invokeStatic("java/lang/Double", "longBitsToDouble", "(J)D");
        ib.invokeStatic("java/lang/Double", "valueOf", "(D)Ljava/lang/Double;");
        ib.goto_(done);

        ib.label(objectValue);
        ib.aload(InterpretContext.STACK_OBJECT);

        ib.label(done);
    }

    protected static void popObject(InsnBuilder ib, InterpretContext context, int local)
    {
        popObject(ib, context);
        ib.astore(local);
    }

    protected static void popNumber(InsnBuilder ib, InterpretContext context, NumericType type)
    {
        LabelNode boxed = new LabelNode();
        LabelNode done = new LabelNode();

        emitRawPopIndexAndType(ib, context);
        ib.iload(InterpretContext.STACK_TYPE);
        ib.pushInt(typeTag(type));
        ib.ifIcmpNe(boxed);

        loadStackWord(ib, context);
        switch (type)
        {
            case INT -> ib.l2i();
            case LONG -> { }
            case FLOAT ->
            {
                ib.l2i();
                ib.invokeStatic("java/lang/Float", "intBitsToFloat", "(I)F");
            }
            case DOUBLE -> ib.invokeStatic("java/lang/Double", "longBitsToDouble", "(J)D");
        }
        ib.goto_(done);

        ib.label(boxed);
        emitLoadAndClearBoxedStackValue(ib, context);
        ib.aload(InterpretContext.STACK_OBJECT);
        type.unbox(ib);

        ib.label(done);
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
        ib.astore(InterpretContext.STACK_OBJECT);
        emitPushObjectWithConstantWidth(ib, context, InterpretContext.STACK_OBJECT, width);
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
        emitPushObjectPrefix(ib, context, objectLocal);
        ib.aload(InterpretContext.FRAME);
        context.frame.stackWidths.get(ib);
        ib.aload(InterpretContext.FRAME);
        context.frame.stackPointer.get(ib);
        ib.iload(widthLocal);
        ib.iastore();
        emitPushSuffix(ib, context);
    }

    /** Boxes and pushes the primitive currently on top of the JVM operand stack. */
    protected static void pushNumber(InsnBuilder ib, InterpretContext context, NumericType type)
    {
        type.store(ib, InterpretContext.RIGHT_VALUE);
        emitPushWordPrefix(ib, context);
        ib.aload(InterpretContext.FRAME);
        context.frame.stackWords.get(ib);
        ib.aload(InterpretContext.FRAME);
        context.frame.stackPointer.get(ib);
        type.load(ib, InterpretContext.RIGHT_VALUE);
        switch (type)
        {
            case INT -> ib.i2l();
            case LONG -> { }
            case FLOAT ->
            {
                ib.invokeStatic("java/lang/Float", "floatToRawIntBits", "(F)I");
                ib.i2l();
            }
            case DOUBLE -> ib.invokeStatic("java/lang/Double", "doubleToRawLongBits", "(D)J");
        }
        ib.lastore();
        emitPushWordSuffix(ib, context, type);
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

    private static void emitPushObjectWithConstantWidth(
            InsnBuilder ib,
            InterpretContext context,
            int objectLocal,
            int width)
    {
        emitPushObjectPrefix(ib, context, objectLocal);
        ib.aload(InterpretContext.FRAME);
        context.frame.stackWidths.get(ib);
        ib.aload(InterpretContext.FRAME);
        context.frame.stackPointer.get(ib);
        ib.pushInt(width);
        ib.iastore();
        emitPushSuffix(ib, context);
    }

    private static void emitPushObjectPrefix(
            InsnBuilder ib,
            InterpretContext context,
            int objectLocal)
    {
        ib.aload(InterpretContext.FRAME);
        context.frame.stack.get(ib);
        ib.aload(InterpretContext.FRAME);
        context.frame.stackPointer.get(ib);
        ib.aload(objectLocal);
        ib.aastore();

        ib.aload(InterpretContext.FRAME);
        context.frame.stackTypes.get(ib);
        ib.aload(InterpretContext.FRAME);
        context.frame.stackPointer.get(ib);
        ib.iconst0();
        ib.iastore();
    }

    private static void emitPushWordPrefix(
            InsnBuilder ib,
            InterpretContext context)
    {
        ib.aload(InterpretContext.FRAME);
        context.frame.stack.get(ib);
        ib.aload(InterpretContext.FRAME);
        context.frame.stackPointer.get(ib);
        ib.aconstNull();
        ib.aastore();
    }

    private static void emitPushWordSuffix(
            InsnBuilder ib,
            InterpretContext context,
            NumericType type)
    {
        ib.aload(InterpretContext.FRAME);
        context.frame.stackTypes.get(ib);
        ib.aload(InterpretContext.FRAME);
        context.frame.stackPointer.get(ib);
        ib.pushInt(typeTag(type));
        ib.iastore();

        ib.aload(InterpretContext.FRAME);
        context.frame.stackWidths.get(ib);
        ib.aload(InterpretContext.FRAME);
        context.frame.stackPointer.get(ib);
        ib.pushInt(type.stackWidth());
        ib.iastore();

        emitPushSuffix(ib, context);
    }

    private static void emitPushSuffix(
            InsnBuilder ib,
            InterpretContext context)
    {
        ib.aload(InterpretContext.FRAME);
        ib.dup();
        context.frame.stackPointer.get(ib);
        ib.iconst1();
        ib.iadd();
        context.frame.stackPointer.put(ib);
    }

    private static void emitRawPopIndexAndType(
            InsnBuilder ib,
            InterpretContext context)
    {
        ib.aload(InterpretContext.FRAME);
        context.frame.stackPointer.get(ib);
        ib.iconst1();
        ib.isub();
        ib.istore(InterpretContext.STACK_INDEX);

        ib.aload(InterpretContext.FRAME);
        ib.iload(InterpretContext.STACK_INDEX);
        context.frame.stackPointer.put(ib);

        ib.aload(InterpretContext.FRAME);
        context.frame.stackTypes.get(ib);
        ib.iload(InterpretContext.STACK_INDEX);
        ib.iaload();
        ib.istore(InterpretContext.STACK_TYPE);

        ib.aload(InterpretContext.FRAME);
        context.frame.stackTypes.get(ib);
        ib.iload(InterpretContext.STACK_INDEX);
        ib.iconst0();
        ib.iastore();

        ib.aload(InterpretContext.FRAME);
        context.frame.stackWidths.get(ib);
        ib.iload(InterpretContext.STACK_INDEX);
        ib.iconst0();
        ib.iastore();
    }

    private static void emitLoadAndClearBoxedStackValue(
            InsnBuilder ib,
            InterpretContext context)
    {
        ib.aload(InterpretContext.FRAME);
        context.frame.stack.get(ib);
        ib.iload(InterpretContext.STACK_INDEX);
        ib.aaload();
        ib.astore(InterpretContext.STACK_OBJECT);

        ib.aload(InterpretContext.FRAME);
        context.frame.stack.get(ib);
        ib.iload(InterpretContext.STACK_INDEX);
        ib.aconstNull();
        ib.aastore();
    }

    private static void loadStackWord(
            InsnBuilder ib,
            InterpretContext context)
    {
        ib.aload(InterpretContext.FRAME);
        context.frame.stackWords.get(ib);
        ib.iload(InterpretContext.STACK_INDEX);
        ib.laload();
    }

    private static int typeTag(NumericType type)
    {
        return switch (type)
        {
            case INT -> 1;
            case LONG -> 2;
            case FLOAT -> 3;
            case DOUBLE -> 4;
        };
    }
}
