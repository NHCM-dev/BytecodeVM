package nhcm.bytecodevm.ObfuscatedExample;

public class TargetClass
{
    private static final int MAGIC = 0x045D9F3B;
    private static int global = 100;
    private static int instances;

    private final int seed;
    private final String label;
    private int counter;
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

    public int originalAdd(int a, int b)
    {
        return a + b;
    }

    public int add(int a, int b)
    {
        calls++;
        return VM1.executeInt(CodePool.ADD_METHOD, this, new Object[]{a, b});
    }

    public int originalMix(int a, int b)
    {
        return (a * 31 + b) ^ 0x5A5A;
    }

    public int mix(int a, int b)
    {
        calls++;
        return VM1.executeInt(CodePool.MIX_METHOD, this, new Object[]{a, b});
    }

    public int originalAbsolute(int value)
    {
        return value >= 0 ? value : -value;
    }

    public int absolute(int value)
    {
        calls++;
        return VM1.executeInt(CodePool.ABSOLUTE_METHOD, this, new Object[]{value});
    }

    public int originalClamp(int value, int min, int max)
    {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    public int clamp(int value, int min, int max)
    {
        calls++;
        return VM1.executeInt(
                CodePool.CLAMP_METHOD,
                this,
                new Object[]{value, min, max});
    }

    public int originalSumTo(int limit)
    {
        int result = 0;
        for (int index = 1; index <= limit; index++) result += index;
        return result;
    }

    public int sumTo(int limit)
    {
        calls++;
        return VM1.executeInt(CodePool.SUM_TO_METHOD, this, new Object[]{limit});
    }

    public int originalScramble(int value)
    {
        return ((value << 5) ^ (value >>> 3)) * MAGIC;
    }

    public int scramble(int value)
    {
        calls++;
        return VM1.executeInt(CodePool.SCRAMBLE_METHOD, this, new Object[]{value});
    }

    public int originalMixWithSeed(int value)
    {
        return value * 31 + seed;
    }

    public int mixWithSeed(int value)
    {
        calls++;
        return VM1.executeInt(
                CodePool.MIX_WITH_SEED_METHOD,
                this,
                new Object[]{value, seed});
    }

    public int originalAddSeed(int value)
    {
        return seed + value;
    }

    public int addSeed(int value)
    {
        calls++;
        return VM1.executeInt(CodePool.ADD_SEED_METHOD, this, new Object[]{value});
    }

    public int originalIncrementCounter(int amount)
    {
        counter += amount;
        return counter;
    }

    public int incrementCounter(int amount)
    {
        calls++;
        return VM1.executeInt(
                CodePool.INCREMENT_COUNTER_METHOD,
                this,
                new Object[]{amount});
    }

    public int originalAddGlobal(int amount)
    {
        global += amount;
        return global;
    }

    public int addGlobal(int amount)
    {
        calls++;
        return VM1.executeInt(CodePool.ADD_GLOBAL_METHOD, this, new Object[]{amount});
    }

    private int helper(int value)
    {
        return value * 3 + 1;
    }

    public int originalCallHelper(int value)
    {
        return helper(value) + seed;
    }

    public int callHelper(int value)
    {
        calls++;
        return VM1.executeInt(CodePool.CALL_HELPER_METHOD, this, new Object[]{value});
    }

    private static int rotate(int value)
    {
        return Integer.rotateLeft(value, 7);
    }

    public int originalCallStatic(int value)
    {
        return rotate(value) ^ 0x5A5A;
    }

    public int callStatic(int value)
    {
        calls++;
        return VM1.executeInt(CodePool.CALL_STATIC_METHOD, this, new Object[]{value});
    }

    public String describe()
    {
        return label + ':' + seed + ':' + counter + ':' + calls;
    }

    public static int createdInstances()
    {
        return instances;
    }

    public static int magic()
    {
        return MAGIC;
    }

    public static void resetGlobal()
    {
        global = 100;
    }
}
