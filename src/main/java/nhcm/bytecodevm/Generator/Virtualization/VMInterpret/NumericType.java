package nhcm.bytecodevm.Generator.Virtualization.VMInterpret;

import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.AdvInsn.Expr;
import nhcm.bytecodevm.Enums.Opcs;
import org.objectweb.asm.Type;

public enum NumericType
{
    INT("java/lang/Integer"),
    LONG("java/lang/Long"),
    FLOAT("java/lang/Float"),
    DOUBLE("java/lang/Double");

    private final String wrapper;

    NumericType(String wrapper)
    {
        this.wrapper = wrapper;
    }

    public static NumericType fromOpcode(Opcs opcode)
    {
        return switch (opcode.name().charAt(0))
        {
            case 'I' -> INT;
            case 'L' -> LONG;
            case 'F' -> FLOAT;
            case 'D' -> DOUBLE;
            default -> throw new IllegalArgumentException("Not a numeric opcode: " + opcode);
        };
    }

    public Expr unbox(Expr value)
    {
        return AdvInsnBuilder.unbox(value, type());
    }

    public Expr box(Expr value)
    {
        return AdvInsnBuilder.callStatic(wrapper, "valueOf", wrapper, value);
    }

    public Type type()
    {
        return switch (this)
        {
            case INT -> Type.INT_TYPE;
            case LONG -> Type.LONG_TYPE;
            case FLOAT -> Type.FLOAT_TYPE;
            case DOUBLE -> Type.DOUBLE_TYPE;
        };
    }

    public String descriptor()
    {
        return type().getDescriptor();
    }

    public int stackWidth()
    {
        return this == LONG || this == DOUBLE ? 2 : 1;
    }

    public String framePushName()
    {
        return switch (this)
        {
            case INT -> "pushInt";
            case LONG -> "pushLong";
            case FLOAT -> "pushFloat";
            case DOUBLE -> "pushDouble";
        };
    }

    public String framePushDescriptor()
    {
        return switch (this)
        {
            case INT -> "(I)V";
            case LONG -> "(J)V";
            case FLOAT -> "(F)V";
            case DOUBLE -> "(D)V";
        };
    }

    public String framePopName()
    {
        return switch (this)
        {
            case INT -> "popInt";
            case LONG -> "popLong";
            case FLOAT -> "popFloat";
            case DOUBLE -> "popDouble";
        };
    }

    public String framePopDescriptor()
    {
        return switch (this)
        {
            case INT -> "()I";
            case LONG -> "()J";
            case FLOAT -> "()F";
            case DOUBLE -> "()D";
        };
    }
}
