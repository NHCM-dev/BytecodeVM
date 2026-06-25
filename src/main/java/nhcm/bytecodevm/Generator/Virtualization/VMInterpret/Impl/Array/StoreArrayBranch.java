package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Array;

import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.AdvInsn.Expr;
import nhcm.bytecodevm.AdvInsn.Local;
import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.NumericType;

import java.util.Set;

public class StoreArrayBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.STORE_ARRAY.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        Local index = context.middleValue();
        Local array = context.objectLocal("array", InterpretContext.RIGHT_VALUE);

        switch (opcode)
        {
            case IASTORE, CASTORE, SASTORE -> popInt(ib, context, InterpretContext.LEFT_VALUE);
            case LASTORE -> popLong(ib, context, InterpretContext.LEFT_VALUE);
            case FASTORE -> popFloat(ib, context, InterpretContext.LEFT_VALUE);
            case DASTORE -> popDouble(ib, context, InterpretContext.LEFT_VALUE);
            case AASTORE, BASTORE -> popObject(ib, context, InterpretContext.LEFT_VALUE);
            default -> throw new IllegalArgumentException("Unsupported array store opcode: " + opcode);
        }

        popInt(ib, context, InterpretContext.MIDDLE_VALUE);
        popObject(ib, context, InterpretContext.RIGHT_VALUE);

        switch (opcode)
        {
            case IASTORE -> ib.setArray(
                    AdvInsnBuilder.cast(array, "[I"),
                    index,
                    context.leftValue(NumericType.INT));
            case LASTORE -> ib.setArray(
                    AdvInsnBuilder.cast(array, "[J"),
                    index,
                    context.leftValue(NumericType.LONG));
            case FASTORE -> ib.setArray(
                    AdvInsnBuilder.cast(array, "[F"),
                    index,
                    context.leftValue(NumericType.FLOAT));
            case DASTORE -> ib.setArray(
                    AdvInsnBuilder.cast(array, "[D"),
                    index,
                    context.leftValue(NumericType.DOUBLE));
            case AASTORE -> ib.setArray(
                    AdvInsnBuilder.cast(array, "[Ljava/lang/Object;"),
                    index,
                    context.objectLocal("value", InterpretContext.LEFT_VALUE));
            case BASTORE -> generateByteOrBooleanStore(ib, context, array, index);
            case CASTORE -> ib.setArray(
                    AdvInsnBuilder.cast(array, "[C"),
                    index,
                    context.leftValue(NumericType.INT));
            case SASTORE -> ib.setArray(
                    AdvInsnBuilder.cast(array, "[S"),
                    index,
                    context.leftValue(NumericType.INT));
            default -> throw new IllegalArgumentException("Unsupported array store opcode: " + opcode);
        }
    }

    private static void generateByteOrBooleanStore(
            AdvInsnBuilder ib,
            InterpretContext context,
            Expr array,
            Expr index)
    {
        Local value = context.objectLocal("value", InterpretContext.LEFT_VALUE);
        ib.ifElse(
                AdvInsnBuilder.isInstanceOf(array, "[Z"),
                b -> b.setArray(
                        AdvInsnBuilder.cast(array, "[Z"),
                        index,
                        AdvInsnBuilder.unbox(value, "Z")),
                b -> b.setArray(
                        AdvInsnBuilder.cast(array, "[B"),
                        index,
                        AdvInsnBuilder.callVirtual(
                                AdvInsnBuilder.cast(value, "java/lang/Number"),
                                "java/lang/Number",
                                "byteValue",
                                "B")));
    }
}
