package nhcm.bytecodevm.ObfuscatedExample;

import java.util.Objects;

public final class VMProgram
{
    private final int[] code;
    private final Object[] constants;
    private final int maxLocals;
    private final int maxStack;

    public VMProgram(
            int[] code,
            Object[] constants,
            int maxLocals,
            int maxStack)
    {
        this.code = Objects.requireNonNull(code, "code");
        this.constants = Objects.requireNonNull(constants, "constants");
        this.maxLocals = maxLocals;
        this.maxStack = maxStack;
    }

    public int[] code()
    {
        return code;
    }

    public Object[] constants()
    {
        return constants;
    }

    public int maxLocals()
    {
        return maxLocals;
    }

    public int maxStack()
    {
        return maxStack;
    }
}
