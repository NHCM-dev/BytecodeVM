import nhcm.bytecodevm.ObfuscatedExample.TargetClass;

public class Test
{
    public static void main(String[] args)
    {
        TargetClass target = new TargetClass(13, "sample");

        check("add(19, 23)", 42, target.add(19, 23));
        check("mix(7, 11)", (7 * 31 + 11) ^ 0x5A5A, target.mix(7, 11));
        check("absolute(-91)", 91, target.absolute(-91));
        check("absolute(37)", 37, target.absolute(37));
        check("clamp(-4, 0, 10)", 0, target.clamp(-4, 0, 10));
        check("clamp(7, 0, 10)", 7, target.clamp(7, 0, 10));
        check("clamp(18, 0, 10)", 10, target.clamp(18, 0, 10));
        check("sumTo(10)", 55, target.sumTo(10));
        check("scramble(12345)", scramble(12345), target.scramble(12345));
        check("mixWithSeed(9)", 9 * 31 + 13, target.mixWithSeed(9));

        System.out.println("state     : " + target.describe());
        System.out.println("instances : " + TargetClass.createdInstances());
        System.out.println("magic     : 0x" + Integer.toHexString(TargetClass.magic()));
    }

    private static int scramble(int value)
    {
        return ((value << 5) ^ (value >>> 3)) * 0x045D9F3B;
    }

    private static void check(String method, int expected, int actual)
    {
        System.out.println("=== " + method + " ===");
        System.out.println("expected : " + expected);
        System.out.println("actual   : " + actual);
        System.out.println("success  : " + (expected == actual));
        System.out.println();

        if (expected != actual)
        {
            throw new AssertionError(method + ": expected " + expected + ", got " + actual);
        }
    }
}
