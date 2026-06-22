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
        return VM1.execute(0x13572468, this, a, b);
    }

    public int originalMix(int a, int b)
    {
        return (a * 31 + b) ^ 0x5A5A;
    }

    public int mix(int a, int b)
    {
        calls++;
        return VM1.execute(0x6C8E9CF1, this, a, b);
    }

    public int originalAbsolute(int value)
    {
        return value >= 0 ? value : -value;
    }

    public int absolute(int value)
    {
        calls++;
        return VM1.execute(0x24681357, this, value);
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
        return VM1.execute(
                0x55AA11EE,
                this,
                value, min, max);
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
        return VM1.execute(0x10293847, this, limit);
    }

    public int originalScramble(int value)
    {
        return ((value << 5) ^ (value >>> 3)) * MAGIC;
    }

    public int scramble(int value)
    {
        calls++;
        return VM1.execute(0x7F4A2C19, this, value);
    }

    public int originalMixWithSeed(int value)
    {
        return value * 31 + seed;
    }

    public int mixWithSeed(int value)
    {
        calls++;
        return VM1.execute(
                0x31415926,
                this,
                value, seed);
    }

    public int originalAddSeed(int value)
    {
        return seed + value;
    }

    public int addSeed(int value)
    {
        calls++;
        return VM1.execute(0x0BADB002, this, value);
    }

    public int originalIncrementCounter(int amount)
    {
        counter += amount;
        return counter;
    }

    public int incrementCounter(int amount)
    {
        calls++;
        return VM1.execute(
                0x5EED1234,
                this,
                amount);
    }

    public int originalAddGlobal(int amount)
    {
        global += amount;
        return global;
    }

    public int addGlobal(int amount)
    {
        calls++;
        return VM1.execute(0x73A91C2D, this, amount);
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
        return VM1.execute(0x4F1E2D3C, this, value);
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
        return VM1.execute(0x66CC8842, this, value);
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
