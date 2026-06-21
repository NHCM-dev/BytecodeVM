package nhcm.bytecodevm.ObfuscatedExample;

final class CodePool
{
    static final int LOAD_LOCAL = 0x6A09E667;
    static final int STORE_LOCAL = 0xBB67AE85;
    static final int PUSH_INT = 0x3C6EF372;
    static final int ADD_INT = 0xA54FF53A;
    static final int SUBTRACT_INT = 0x510E527F;
    static final int MULTIPLY_INT = 0x9B05688C;
    static final int XOR_INT = 0x1F83D9AB;
    static final int NEGATE_INT = 0x5BE0CD19;
    static final int SHIFT_LEFT_INT = 0x12835B01;
    static final int UNSIGNED_SHIFT_RIGHT_INT = 0x243185BE;
    static final int IF_INT_GE_ZERO = 0xCBBB9D5D;
    static final int IF_INT_LESS_THAN = 0x550C7DC3;
    static final int IF_INT_GREATER_THAN = 0x72BE5D74;
    static final int GOTO = 0x80DEB1FE;
    static final int RETURN_INT = 0x629A292A;

    static final int ADD_METHOD = 0x13572468;
    static final int MIX_METHOD = 0x6C8E9CF1;
    static final int ABSOLUTE_METHOD = 0x24681357;
    static final int CLAMP_METHOD = 0x55AA11EE;
    static final int SUM_TO_METHOD = 0x10293847;
    static final int SCRAMBLE_METHOD = 0x7F4A2C19;
    static final int MIX_WITH_SEED_METHOD = 0x31415926;

    private static final int[] ADD_CODE = {
            0xE769BBB6, 0x1F1E5ADF, 0x543553DB,
            0x781BCC3F, 0x4233AF3E, 0xA345FF72
    };

    private static final int[] MIX_CODE = {
            0x98B0032F, 0xE9D169CD, 0xEFCC20EB, 0xA2E807E7,
            0xA611B9F7, 0x07192322, 0xC8AB0D9C, 0xDECE0A2C,
            0xE0BCB1B8, 0x9B1570A2, 0x0CBE85B3, 0x9DAA3634
    };

    private static final int[] ABSOLUTE_CODE = {
            0xD0568C89, 0x791C7519, 0x3983776D, 0xE014352E,
            0xDD64CDB0, 0x61C179FF, 0xE9ECBA09, 0x2CDEF8D0,
            0xE85E0EF5, 0xE6207686, 0xCBC9DA1D
    };

    private static final int[] CLAMP_CODE = {
            0xA1948E30, 0x9B986FEB, 0x5D3939B2, 0x6A04206D,
            0x9E417FAB, 0x8981272F, 0x6B823733, 0xED438683,
            0xAA5CB7C4, 0x621B48A9, 0xD3F7CA08, 0x1B4895F7,
            0x60E1151A, 0xE5CC0F73, 0xF5AE810B, 0x9FA1DC24,
            0xAE31BF4F, 0x787D030F, 0x85FD86FC, 0x95B12E4F,
            0xBE570358
    };

    private static final int[] SUM_TO_CODE = {
            0xB270B28C, 0x209A2338, 0xFA53E8F5, 0x7E0AEDA7,
            0xCF346FA6, 0xD99C5BF9, 0x1AA59580, 0x2DB059E7,
            0x2B271E01, 0x63CE962C, 0xCEB9D4E1, 0x2215CBF8,
            0x6A3D02D1, 0x0100E7DC, 0xA797BEA6, 0x627B9B86,
            0xEDD167A9, 0xCE34AF24, 0xF3C09FA5, 0xD980ECD6,
            0x77395282, 0xF17635F8, 0xDFAF36A1, 0x5FDEAE1F,
            0x67550BC8, 0xCF0F3C7C, 0xD0DD0B80, 0x6A50BD4B,
            0xE3C1FF6D, 0x505ED1E6, 0x4C89DF0E, 0xCE656926,
            0x7B480074
    };

    private static final int[] SCRAMBLE_CODE = {
            0x8B74B3C7, 0xC7584A7D, 0xB2DE678B, 0x19038ABD,
            0x3BC88DFB, 0x2F773624, 0x7987239B, 0xE154D87F,
            0xA0429ABA, 0x05C45F6B, 0x3F7D6278, 0x26DC2DE6,
            0x0DE01BE0, 0x78705AA2, 0xF01CC4B3
    };

    private static final int[] MIX_WITH_SEED_CODE = {
            0xC57FC6F8, 0x6349E07B, 0xFAFD3386,
            0x88C9E2B1, 0x93DDEE6A, 0x6E91D67B,
            0xF7B9E34F, 0x7CD05F4E, 0xFB97B0AD
    };

    private CodePool()
    {
    }

    static int[] encodedCode(int codeId)
    {
        return switch (codeId)
        {
            case ADD_METHOD -> ADD_CODE;
            case MIX_METHOD -> MIX_CODE;
            case ABSOLUTE_METHOD -> ABSOLUTE_CODE;
            case CLAMP_METHOD -> CLAMP_CODE;
            case SUM_TO_METHOD -> SUM_TO_CODE;
            case SCRAMBLE_METHOD -> SCRAMBLE_CODE;
            case MIX_WITH_SEED_METHOD -> MIX_WITH_SEED_CODE;
            default -> throw new IllegalArgumentException("Unknown code id: " + codeId);
        };
    }

    static int decodeToken(int[] encodedCode, int codeId, int tokenIndex)
    {
        int key = Integer.rotateLeft(codeId ^ 0x9E3779B9, tokenIndex & 31)
                + tokenIndex * 0x045D9F3B;
        return encodedCode[tokenIndex] ^ key;
    }

    static int maxLocals(int codeId)
    {
        return switch (codeId)
        {
            case ADD_METHOD, MIX_METHOD, CLAMP_METHOD, SUM_TO_METHOD -> 4;
            case ABSOLUTE_METHOD, SCRAMBLE_METHOD -> 2;
            case MIX_WITH_SEED_METHOD -> 3;
            default -> throw new IllegalArgumentException("Unknown code id: " + codeId);
        };
    }

    static int maxStack(int codeId)
    {
        return switch (codeId)
        {
            case ADD_METHOD -> 2;
            case MIX_METHOD -> 3;
            case ABSOLUTE_METHOD -> 1;
            case CLAMP_METHOD, SUM_TO_METHOD, MIX_WITH_SEED_METHOD -> 2;
            case SCRAMBLE_METHOD -> 3;
            default -> throw new IllegalArgumentException("Unknown code id: " + codeId);
        };
    }
}
