package nhcm.bytecodevm.ObfuscatedExample;

public class TargetClass
{
    private static final int MAGIC = 0x45D9F3B;
    private static int instances;

    private final int seed;
    private final String label;
    private int calls;

    public TargetClass()
    {
        this(17, "default");
    }

    public TargetClass(int seed, String label)
    {
        this.seed = seed;
        this.label = label;
        instances++;
    }

    /*
     * Original Java:
     *   return a + b;
     *
     * Original JVM bytecode:
     *   0: iload_1
     *   1: iload_2
     *   2: iadd
     *   3: ireturn
     */
    public int add(int a, int b)
    {
        calls++;
        return VM1.executeInt(CodePool.ADD_METHOD, this, new Object[]{a, b});
    }

    /*
     * Original Java:
     *   return (a * 31 + b) ^ 0x5A5A;
     *
     * Original JVM bytecode:
     *   0: iload_1
     *   1: bipush 31
     *   3: imul
     *   4: iload_2
     *   5: iadd
     *   6: sipush 23130
     *   9: ixor
     *  10: ireturn
     */
    public int mix(int a, int b)
    {
        calls++;
        return VM1.executeInt(CodePool.MIX_METHOD, this, new Object[]{a, b});
    }

    /*
     * Original Java:
     *   return value >= 0 ? value : -value;
     *
     * Original JVM bytecode:
     *   0: iload_1
     *   1: ifge 7
     *   4: iload_1
     *   5: ineg
     *   6: ireturn
     *   7: iload_1
     *   8: ireturn
     */
    public int absolute(int value)
    {
        calls++;
        return VM1.executeInt(CodePool.ABSOLUTE_METHOD, this, new Object[]{value});
    }

    /*
     * Original Java:
     *   if (value < min) return min;
     *   if (value > max) return max;
     *   return value;
     *
     * Original JVM bytecode uses IF_ICMPGE, IF_ICMPLE and three IRETURN paths.
     */
    public int clamp(int value, int min, int max)
    {
        calls++;
        return VM1.executeInt(CodePool.CLAMP_METHOD, this, new Object[]{value, min, max});
    }

    /*
     * Original Java:
     *   int result = 0;
     *   for (int i = 1; i <= limit; i++) result += i;
     *   return result;
     *
     * Original JVM bytecode uses ISTORE, IINC-like arithmetic,
     * IF_ICMPGT and a backward GOTO.
     */
    public int sumTo(int limit)
    {
        calls++;
        return VM1.executeInt(CodePool.SUM_TO_METHOD, this, new Object[]{limit});
    }

    /*
     * Original Java:
     *   return ((value << 5) ^ (value >>> 3)) * MAGIC;
     *
     * Original JVM bytecode uses ISHL, IUSHR, IXOR and IMUL.
     */
    public int scramble(int value)
    {
        calls++;
        return VM1.executeInt(CodePool.SCRAMBLE_METHOD, this, new Object[]{value});
    }

    /*
     * The field read remains in the small JVM stub; the arithmetic body is virtualized.
     * Original Java: return value * 31 + seed;
     */
    public int mixWithSeed(int value)
    {
        calls++;
        return VM1.executeInt(CodePool.MIX_WITH_SEED_METHOD, this, new Object[]{value, seed});
    }

    public String describe()
    {
        return label + ':' + seed + ':' + calls;
    }

    public static int createdInstances()
    {
        return instances;
    }

    public static int magic()
    {
        return MAGIC;
    }
}
