package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Array;

import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.NumericType;

import java.util.Set;

public class LoadArrayBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.LOAD_ARRAY.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        popInt(ib, context, InterpretContext.MIDDLE_VALUE);
        popObject(ib, context, InterpretContext.RIGHT_VALUE);

        switch (opcode)
        {
            case IALOAD -> pushNumber(ib, context, NumericType.INT, AdvInsnBuilder.arrayAt(
                    AdvInsnBuilder.cast(context.objectLocal("array", InterpretContext.RIGHT_VALUE), "[I"),
                    context.middleValue()));
            case LALOAD -> pushNumber(ib, context, NumericType.LONG, AdvInsnBuilder.arrayAt(
                    AdvInsnBuilder.cast(context.objectLocal("array", InterpretContext.RIGHT_VALUE), "[J"),
                    context.middleValue()));
            case FALOAD -> pushNumber(ib, context, NumericType.FLOAT, AdvInsnBuilder.arrayAt(
                    AdvInsnBuilder.cast(context.objectLocal("array", InterpretContext.RIGHT_VALUE), "[F"),
                    context.middleValue()));
            case DALOAD -> pushNumber(ib, context, NumericType.DOUBLE, AdvInsnBuilder.arrayAt(
                    AdvInsnBuilder.cast(context.objectLocal("array", InterpretContext.RIGHT_VALUE), "[D"),
                    context.middleValue()));
            case AALOAD -> pushObject(ib, context, AdvInsnBuilder.arrayAt(
                    AdvInsnBuilder.cast(context.objectLocal("array", InterpretContext.RIGHT_VALUE), "[Ljava/lang/Object;"),
                    context.middleValue()));
            case BALOAD -> generateByteOrBooleanLoad(ib, context);
            case CALOAD -> pushNumber(ib, context, NumericType.INT, AdvInsnBuilder.arrayAt(
                    AdvInsnBuilder.cast(context.objectLocal("array", InterpretContext.RIGHT_VALUE), "[C"),
                    context.middleValue()));
            case SALOAD -> pushNumber(ib, context, NumericType.INT, AdvInsnBuilder.arrayAt(
                    AdvInsnBuilder.cast(context.objectLocal("array", InterpretContext.RIGHT_VALUE), "[S"),
                    context.middleValue()));
            default -> throw new IllegalArgumentException("Unsupported array load opcode: " + opcode);
        }
    }

    private static void generateByteOrBooleanLoad(AdvInsnBuilder ib, InterpretContext context)
    {
        var array = context.objectLocal("array", InterpretContext.RIGHT_VALUE);
        ib.ifElse(
                AdvInsnBuilder.isInstanceOf(array, "[Z"),
                b -> pushInt(b, context, AdvInsnBuilder.arrayAt(
                        AdvInsnBuilder.cast(array, "[Z"),
                        context.middleValue())),
                b -> pushInt(b, context, AdvInsnBuilder.arrayAt(
                        AdvInsnBuilder.cast(array, "[B"),
                        context.middleValue())));
    }
}
