package nhcm.bytecodevm.Data.VMInsn;

public class VMOperand
{
    public final int index;
    public final int rawValue;
    public final boolean constantReference;
    public final Object value;

    VMOperand(int index, int rawValue, boolean constantReference, Object value)
    {
        this.index = index;
        this.rawValue = rawValue;
        this.constantReference = constantReference;
        this.value = value;
    }

    public int asInt()
    {
        if (constantReference)
        {
            throw new IllegalStateException("Operand " + index + " is a constant reference");
        }
        return rawValue;
    }

    public Object asConstant()
    {
        if (!constantReference)
        {
            throw new IllegalStateException("Operand " + index + " is an immediate value");
        }
        return value;
    }

    public <T> T asConstant(Class<T> type)
    {
        return type.cast(asConstant());
    }

    @Override
    public String toString()
    {
        return constantReference
                ? "#" + rawValue + '(' + value + ')'
                : Integer.toString(rawValue);
    }
}
