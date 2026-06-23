package nhcm.bytecodevm.Generator.Virtualization.VMInterpret;

import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import nhcm.bytecodevm.Utils.TypeUtils;

public enum NumericType
{
    INT("java/lang/Integer", "intValue", "()I", "(I)Ljava/lang/Integer;"),
    LONG("java/lang/Long", "longValue", "()J", "(J)Ljava/lang/Long;"),
    FLOAT("java/lang/Float", "floatValue", "()F", "(F)Ljava/lang/Float;"),
    DOUBLE("java/lang/Double", "doubleValue", "()D", "(D)Ljava/lang/Double;");

    private final String wrapper;
    private final String unboxMethod;
    private final String unboxDescriptor;
    private final String boxDescriptor;

    NumericType(
            String wrapper,
            String unboxMethod,
            String unboxDescriptor,
            String boxDescriptor)
    {
        this.wrapper = wrapper;
        this.unboxMethod = unboxMethod;
        this.unboxDescriptor = unboxDescriptor;
        this.boxDescriptor = boxDescriptor;
    }

    public static NumericType fromOpcode(Opcs opcode)
    {
        switch (opcode.name().charAt(0))
        {
            case 'I': return INT;
            case 'L': return LONG;
            case 'F': return FLOAT;
            case 'D': return DOUBLE;
            default: throw new IllegalArgumentException("Not a numeric opcode: " + opcode);
        }
    }

    public void unbox(InsnBuilder ib)
    {
        if (this == INT)
        {
            TypeUtils.unboxIntLike(ib);
            return;
        }
        ib.checkCast(wrapper);
        ib.invokeVirtual(wrapper, unboxMethod, unboxDescriptor);
    }

    public void box(InsnBuilder ib)
    {
        ib.invokeStatic(wrapper, "valueOf", boxDescriptor);
    }

    public void load(InsnBuilder ib, int local)
    {
        switch (this)
        {
            case INT: ib.iload(local); break;
            case LONG: ib.lload(local); break;
            case FLOAT: ib.fload(local); break;
            case DOUBLE: ib.dload(local); break;
        }
    }

    public void store(InsnBuilder ib, int local)
    {
        switch (this)
        {
            case INT: ib.istore(local); break;
            case LONG: ib.lstore(local); break;
            case FLOAT: ib.fstore(local); break;
            case DOUBLE: ib.dstore(local); break;
        }
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
