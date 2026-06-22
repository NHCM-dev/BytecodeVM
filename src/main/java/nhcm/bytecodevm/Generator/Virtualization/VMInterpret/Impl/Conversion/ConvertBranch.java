package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Conversion;

import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.NumericType;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.tree.InsnList;

import java.util.Set;

public class ConvertBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.CONVERT.getOpcodes();
    }

    @Override
    public InsnList generate(InterpretContext context, Opcs opcode)
    {
        InsnBuilder ib = new InsnBuilder();
        NumericType sourceType = sourceType(opcode);
        NumericType targetType = targetType(opcode);

        popNumber(ib, context, sourceType);
        switch (opcode)
        {
            case I2L -> ib.i2l();
            case I2F -> ib.i2f();
            case I2D -> ib.i2d();

            case L2I -> ib.l2i();
            case L2F -> ib.l2f();
            case L2D -> ib.l2d();

            case F2I -> ib.f2i();
            case F2L -> ib.f2l();
            case F2D -> ib.f2d();

            case D2I -> ib.d2i();
            case D2L -> ib.d2l();
            case D2F -> ib.d2f();

            case I2B -> ib.i2b();
            case I2C -> ib.i2c();
            case I2S -> ib.i2s();
        }
        pushNumber(ib, context, targetType);
        return ib.toInsnList();
    }

    private static NumericType sourceType(Opcs opcode)
    {
        return switch (opcode)
        {
            case I2L, I2F, I2D, I2B, I2C, I2S -> NumericType.INT;
            case L2I, L2F, L2D -> NumericType.LONG;
            case F2I, F2L, F2D -> NumericType.FLOAT;
            case D2I, D2L, D2F -> NumericType.DOUBLE;
            default -> throw new IllegalArgumentException(
                    "Not a conversion opcode: " + opcode);
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
            default -> throw new IllegalArgumentException(
                    "Not a conversion opcode: " + opcode);
        };
    }
}
