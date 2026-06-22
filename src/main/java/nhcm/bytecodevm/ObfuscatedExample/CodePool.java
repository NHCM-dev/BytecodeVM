package nhcm.bytecodevm.ObfuscatedExample;

/**
 * Equivalent source for a generated code-pool class. The field and method names
 * would normally be randomized by the generator.
 */
public final class CodePool implements VMCodePool
{
    public static final VMCodePool INSTANCE = new CodePool();

    private static final int[][] CODES = {
            {1779033703, 1, 1779033703, 2, -1521486534, 1654270250},
            {1779033703, 1, 1013904242, 31, -1694144372, 1779033703, 2,
                    -1521486534, -1542899678, 23130, 528734635, 1654270250},
            {1779033703, 1, -876896931, 8, 1779033703, 1, 1541459225,
                    1654270250, 1779033703, 1, 1654270250},
            {1779033703, 1, 1779033703, 2, 1426881987, 15, 1779033703, 1,
                    1779033703, 3, 1925078388, 18, 1779033703, 1, 1654270250,
                    1779033703, 2, 1654270250, 1779033703, 3, 1654270250},
            {608135816, -1150833019, 2, -2052912941, -1150833019, 3,
                    1779033703, 3, 1779033703, 1, 1925078388, 27, 1779033703, 2,
                    1779033703, 3, -1521486534, -1150833019, 2, 1779033703, 3,
                    -2052912941, -1521486534, -1150833019, 3, -2132889090, 6,
                    1779033703, 2, 1654270250},
            {1779033703, 1, 57701188, 310598401, 1779033703, 1, 320440878,
                    607225278, 528734635, 698298832, 0, -1694144372, 1654270250}
    };

    private static final Object[][] CONSTANTS = {
            {},
            {},
            {},
            {},
            {},
            {0x045D9F3B}
    };

    private static final int[] MAX_LOCALS = {4, 4, 2, 4, 4, 2};
    private static final int[] MAX_STACK = {2, 3, 1, 2, 2, 3};

    private CodePool()
    {
    }

    @Override
    public VMProgram find(int codeId)
    {
        int methodIndex;
        switch (codeId)
        {
            case 0x13572468: methodIndex = 0; break;
            case 0x6C8E9CF1: methodIndex = 1; break;
            case 0x24681357: methodIndex = 2; break;
            case 0x55AA11EE: methodIndex = 3; break;
            case 0x10293847: methodIndex = 4; break;
            case 0x7F4A2C19: methodIndex = 5; break;
            default: methodIndex = -1;
        }
        return methodIndex < 0 ? null : new VMProgram(
                CODES[methodIndex],
                CONSTANTS[methodIndex],
                MAX_LOCALS[methodIndex],
                MAX_STACK[methodIndex]);
    }
}
