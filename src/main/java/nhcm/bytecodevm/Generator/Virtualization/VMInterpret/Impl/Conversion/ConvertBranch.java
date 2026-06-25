package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Conversion;

import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.AdvInsn.Expr;
import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.NumericType;

import java.util.Set;

public class ConvertBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.CONVERT.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        NumericType sourceType = sourceType(opcode);
        NumericType targetType = targetType(opcode);

        popNumber(ib, context, sourceType);
        pushNumber(ib, context, targetType, converted(opcode, context.rightValue(sourceType)));
    }

    private static Expr converted(Opcs opcode, Expr value)
    {
        return switch (opcode)
        {
            case I2L, F2L, D2L -> AdvInsnBuilder.toLong(value);
            case I2F, L2F, D2F -> AdvInsnBuilder.toFloat(value);
            case I2D, L2D, F2D -> AdvInsnBuilder.toDouble(value);
            case L2I, F2I, D2I -> AdvInsnBuilder.toInt(value);
            case I2B -> AdvInsnBuilder.cast(value, "B");
            case I2C -> AdvInsnBuilder.cast(value, "C");
            case I2S -> AdvInsnBuilder.cast(value, "S");
            default -> throw new IllegalArgumentException("Not a conversion opcode: " + opcode);
        };
    }

    private static NumericType sourceType(Opcs opcode)
    {
        return switch (opcode)
        {
            case I2L, I2F, I2D, I2B, I2C, I2S -> NumericType.INT;
            case L2I, L2F, L2D -> NumericType.LONG;
            case F2I, F2L, F2D -> NumericType.FLOAT;
            case D2I, D2L, D2F -> NumericType.DOUBLE;
            default -> throw new IllegalArgumentException("Not a conversion opcode: " + opcode);
        };
    }

    private static NumericType targetType(Opcs opcode)
    {
        return switch (opcode)
        {
            case L2I, F2I, D2I, I2B, I2C, I2S -> NumericType.INT;
            case I2L, F2L, D2L -> NumericType.LONG;
            case I2F, L2F, D2F -> NumericType.FLOAT;
            case I2D, L2D, F2D -> NumericType.DOUBLE;
            default -> throw new IllegalArgumentException("Not a conversion opcode: " + opcode);
        };
    }
}
