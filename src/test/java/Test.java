import nhcm.bytecodevm.ObfuscatedExample.TargetClass;

public class Test
{
    public static void main(String[] args)
    {
        TargetClass original = new TargetClass(13, "original");
        TargetClass virtualized = new TargetClass(13, "virtualized");

        check("add", original.originalAdd(19, 23), virtualized.add(19, 23));
        check("mix", original.originalMix(7, 11), virtualized.mix(7, 11));
        check("absolute negative", original.originalAbsolute(-91), virtualized.absolute(-91));
        check("absolute positive", original.originalAbsolute(37), virtualized.absolute(37));
        check("clamp low", original.originalClamp(-4, 0, 10), virtualized.clamp(-4, 0, 10));
        check("clamp middle", original.originalClamp(7, 0, 10), virtualized.clamp(7, 0, 10));
        check("clamp high", original.originalClamp(18, 0, 10), virtualized.clamp(18, 0, 10));
        check("sumTo", original.originalSumTo(10), virtualized.sumTo(10));
        check("scramble", original.originalScramble(12345), virtualized.scramble(12345));
        check("mixWithSeed", original.originalMixWithSeed(9), virtualized.mixWithSeed(9));

        check("GETFIELD seed", original.originalAddSeed(29), virtualized.addSeed(29));
        check(
                "GETFIELD/PUTFIELD counter #1",
                original.originalIncrementCounter(5),
                virtualized.incrementCounter(5));
        check(
                "GETFIELD/PUTFIELD counter #2",
                original.originalIncrementCounter(8),
                virtualized.incrementCounter(8));

        TargetClass.resetGlobal();
        int originalGlobal = original.originalAddGlobal(21);
        TargetClass.resetGlobal();
        check("GETSTATIC/PUTSTATIC global", originalGlobal, virtualized.addGlobal(21));

        check(
                "INVOKEVIRTUAL private helper",
                original.originalCallHelper(14),
                virtualized.callHelper(14));
        check(
                "INVOKESTATIC private rotate",
                original.originalCallStatic(0x12345678),
                virtualized.callStatic(0x12345678));

        System.out.println("original state   : " + original.describe());
        System.out.println("virtualized state: " + virtualized.describe());
        System.out.println("instances        : " + TargetClass.createdInstances());
    }

    private static void check(String method, int expected, int actual)
    {
        System.out.println("=== " + method + " ===");
        System.out.println("original result   : " + expected);
        System.out.println("virtualized result: " + actual);
        System.out.println("same              : " + (expected == actual));
        System.out.println();

        if (expected != actual)
        {
            throw new AssertionError(method + ": expected " + expected + ", got " + actual);
        }
    }
}
