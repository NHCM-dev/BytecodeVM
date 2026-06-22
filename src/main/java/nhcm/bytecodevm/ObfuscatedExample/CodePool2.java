package nhcm.bytecodevm.ObfuscatedExample;

public final class CodePool2 implements VMCodePool
{
    public static final VMCodePool INSTANCE = new CodePool2();

    private static final int[][] CODES = {
            {1779033703, 1, 1013904242, 31, -1694144372, -1062458953, 0,
                    -559038737, 0, 1, 2, -1521486534, 1654270250},
            {-1062458953, 0, -559038737, 0, 1, 2, 1779033703, 1,
                    -1521486534, 1654270250},
            {-1062458953, 0, -559038737, 0, 1, 2, 1779033703, 1,
                    -1521486534, -1150833019, 2, -1062458953, 0, 1779033703, 2,
                    -889275714, 0, 1, 2, 1779033703, 2, 1654270250},
            {253635900, 0, 1, 2, 1779033703, 1, -1521486534, -1150833019, 2,
                    1779033703, 2, -1985229329, 0, 1, 2, 1779033703, 2,
                    1654270250},
            {-1062458953, 0, 1779033703, 1, 270544960, 0, 1, 2, 0,
                    -1062458953, 0, -559038737, 3, 4, 5, -1521486534,
                    1654270250},
            {1779033703, 1, 540033104, 0, 1, 2, 0, -1542899678, 23130,
                    528734635, 1654270250}
    };

    private static final String TARGET_OWNER =
            "nhcm/bytecodevm/ObfuscatedExample/TargetClass";

    private static final Object[][] CONSTANTS = {
            {TARGET_OWNER, "seed", "I"},
            {TARGET_OWNER, "seed", "I"},
            {TARGET_OWNER, "counter", "I"},
            {TARGET_OWNER, "global", "I"},
            {TARGET_OWNER, "helper", "(I)I", TARGET_OWNER, "seed", "I"},
            {TARGET_OWNER, "rotate", "(I)I"}
    };

    private static final int[] MAX_LOCALS = {3, 2, 3, 3, 2, 2};
    private static final int[] MAX_STACK = {2, 2, 2, 2, 2, 2};

    private CodePool2()
    {
    }

    @Override
    public VMProgram find(int codeId)
    {
        int methodIndex;
        switch (codeId)
        {
            case 0x31415926: methodIndex = 0; break;
            case 0x0BADB002: methodIndex = 1; break;
            case 0x5EED1234: methodIndex = 2; break;
            case 0x73A91C2D: methodIndex = 3; break;
            case 0x4F1E2D3C: methodIndex = 4; break;
            case 0x66CC8842: methodIndex = 5; break;
            default: methodIndex = -1;
        }
        return methodIndex < 0 ? null : new VMProgram(
                CODES[methodIndex],
                CONSTANTS[methodIndex],
                MAX_LOCALS[methodIndex],
                MAX_STACK[methodIndex]);
    }
}
