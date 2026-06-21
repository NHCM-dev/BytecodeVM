package nhcm.bytecodevm.ObfuscatedExample;

final class CodePool
{
    // VM opcode -> randomized integer mapping used by the generated dispatcher.
    static final int ALOAD = 0xC0AC29B7;
    static final int ILOAD = 0x6A09E667;
    static final int ISTORE = 0xBB67AE85;
    static final int ICONST_0 = 0x243F6A88;
    static final int ICONST_1 = 0x85A308D3;
    static final int ICONST_3 = 0x13198A2E;
    static final int ICONST_5 = 0x03707344;
    static final int BIPUSH = 0x3C6EF372;
    static final int SIPUSH = 0xA4093822;
    static final int LDC = 0x299F31D0;
    static final int IADD = 0xA54FF53A;
    static final int IMUL = 0x9B05688C;
    static final int IXOR = 0x1F83D9AB;
    static final int INEG = 0x5BE0CD19;
    static final int ISHL = 0x12835B01;
    static final int IUSHR = 0x243185BE;
    static final int IFGE = 0xCBBB9D5D;
    static final int IF_ICMPLT = 0x550C7DC3;
    static final int IF_ICMPGT = 0x72BE5D74;
    static final int GOTO = 0x80DEB1FE;
    static final int IRETURN = 0x629A292A;
    static final int GETFIELD = 0xDEADBEEF;
    static final int PUTFIELD = 0xCAFEBABE;
    static final int GETSTATIC = 0x0F1E2D3C;
    static final int PUTSTATIC = 0x89ABCDEF;
    static final int INVOKEVIRTUAL = 0x10203040;
    static final int INVOKESTATIC = 0x20304050;

    // Original method -> code id mapping used by the generated stubs.
    static final int ADD_METHOD = 0x13572468;
    static final int MIX_METHOD = 0x6C8E9CF1;
    static final int ABSOLUTE_METHOD = 0x24681357;
    static final int CLAMP_METHOD = 0x55AA11EE;
    static final int SUM_TO_METHOD = 0x10293847;
    static final int SCRAMBLE_METHOD = 0x7F4A2C19;
    static final int MIX_WITH_SEED_METHOD = 0x31415926;
    static final int ADD_SEED_METHOD = 0x0BADB002;
    static final int INCREMENT_COUNTER_METHOD = 0x5EED1234;
    static final int ADD_GLOBAL_METHOD = 0x73A91C2D;
    static final int CALL_HELPER_METHOD = 0x4F1E2D3C;
    static final int CALL_STATIC_METHOD = 0x66CC8842;

    // These arrays contain encoded [opcode, operand, ...] tokens, not plain VM code.
    private static final int[] ADD_CODE = {
            0xE769BBB6, 0x1F1E5ADF, 0x543553DB,
            0x781BCC3F, 0x4233AF3E, 0xA345FF72
    };

    private static final int[] MIX_CODE = {
            0x98B0032F, 0xE9D169CD, 0xEFCC20EB, 0xA2E807E7,
            0xA611B9F7, 0x07192322, 0xC8AB0D9C, 0xDECE0A2C,
            0x78DB7AE8, 0x9B1570A2, 0x0CBE85B3, 0x9DAA3634
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
            0xAA212B76, 0x9BFD8DBD, 0x41344672, 0xFBA9E576,
            0x483D3251, 0xD99C5BFB, 0xCBCBDD62, 0x2DB059E7,
            0x2B271E01, 0x63CE962E, 0xD60E6FF2, 0x2215CBE2,
            0x728AB9C2, 0x0100E7C0, 0xA797BEA6, 0x627B9B87,
            0x229774F4, 0x755301A2, 0x568F6A9D, 0x08EEA434,
            0x77395283, 0x1EDCDB4C, 0x7AE0C398, 0xD8D7F3E8,
            0x67550BCA, 0xEA9E78B8, 0x6BBAA503, 0x00595B2F,
            0x631F4E91, 0x32C4F8C4
    };

    private static final int[] SCRAMBLE_CODE = {
            0x8B74B3C7, 0xC7584A7D, 0x8DC0E7BD, 0x0B80D1B9,
            0x4342309D, 0x457ED042, 0x6A9EA9B4, 0xF90BAEB3,
            0xBFC14312, 0x086AEB05, 0x20FEBBD3, 0x81B7B618,
            0x6B27ADF1
    };

    private static final int[] MIX_WITH_SEED_CODE = {
            0xC57FC6F8, 0x6349E07B, 0xFAFD3386, 0x88C9E2B1,
            0x93DDEE6A, 0xC43419AB, 0xF7B9E34D, 0x0732149B,
            0x990D9987, 0x138BD870, 0x042AB709, 0x4445203E,
            0xF4F75891
    };

    private static final int[] ADD_SEED_CODE = {
            0x5536E00C, 0x2F9332B2, 0x818BDB8B, 0xB9EF2B8D,
            0x6B2318A4, 0xC92D539B, 0xEAEDCC20, 0xEBF43866,
            0x18F94057, 0x3E443914
    };

    private static final int[] INCREMENT_COUNTER_CODE = {
            0x0076423A, 0x86127656, 0xD2895242, 0x13EC3A1F,
            0x1F1D35C9, 0x31218DDD, 0x3AC578B5, 0x8BC5217C,
            0x581772A2, 0x67461A11, 0x95566F53, 0xC3CE6C38,
            0xDB1C52D1, 0xEC3B287D, 0xD8022572, 0xBDBD8E5C,
            0xB167B48A, 0x2153149E, 0xFCCC358D, 0xC5693E53,
            0x102C7E40, 0xAFFC020E
    };

    private static final int[] ADD_GLOBAL_CODE = {
            0xE28048A8, 0xDF9A6A64, 0xBF34D4C8, 0x7A0C0A5A,
            0x8155305D, 0xC9A0CEC5, 0x2484D5A7, 0x56A58B96,
            0xC1528EC7, 0x0E1C2489, 0xA53E8C06, 0xAA99B31A,
            0x1ABCC59D, 0x0573B3B3, 0xD683F0A3, 0x1E4F2D23,
            0xAB6EE14C, 0x77FB470D
    };

    private static final int[] CALL_HELPER_CODE = {
            0x11857D32, 0xA6B04846, 0x276976EA, 0x966381DE,
            0x342BF509, 0x3AFEACE1, 0x6486DCD7, 0xB3399D87,
            0x4C417FA9, 0xB95F8D02, 0xD0FA4F92, 0xA407B8FD,
            0xC9ABD3D5, 0x6351D020, 0x92402981, 0x4EF0C833,
            0xF8C5EDF3
    };

    private static final int[] CALL_STATIC_CODE = {
            0x92F2179C, 0xF6558333, 0xCC9B4635, 0xD4F86D90,
            0xA1359CAA, 0x35525BA4, 0x592E3A60, 0x388160BB,
            0x1EDEAF8A, 0x00AD48AF, 0x79EA011B
    };

    // Member references are method-local constants addressed by integer operands.
    private static final Object[] NO_CONSTANTS = new Object[0];
    private static final String TARGET_OWNER =
            "nhcm/bytecodevm/ObfuscatedExample/TargetClass";
    private static final Object[] SCRAMBLE_CONSTANTS = {0x045D9F3B};
    private static final Object[] MIX_WITH_SEED_CONSTANTS = {
            TARGET_OWNER, "seed", "I"
    };
    private static final Object[] ADD_SEED_CONSTANTS = {
            TARGET_OWNER, "seed", "I"
    };
    private static final Object[] COUNTER_CONSTANTS = {
            TARGET_OWNER, "counter", "I"
    };
    private static final Object[] GLOBAL_CONSTANTS = {
            TARGET_OWNER, "global", "I"
    };
    private static final Object[] HELPER_CONSTANTS = {
            TARGET_OWNER, "helper", "(I)I",
            TARGET_OWNER, "seed", "I"
    };
    private static final Object[] STATIC_METHOD_CONSTANTS = {
            TARGET_OWNER, "rotate", "(I)I"
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
            case ADD_SEED_METHOD -> ADD_SEED_CODE;
            case INCREMENT_COUNTER_METHOD -> INCREMENT_COUNTER_CODE;
            case ADD_GLOBAL_METHOD -> ADD_GLOBAL_CODE;
            case CALL_HELPER_METHOD -> CALL_HELPER_CODE;
            case CALL_STATIC_METHOD -> CALL_STATIC_CODE;
            default -> throw new IllegalArgumentException("Unknown code id: " + codeId);
        };
    }

    static Object[] constants(int codeId)
    {
        return switch (codeId)
        {
            case ADD_SEED_METHOD -> ADD_SEED_CONSTANTS;
            case INCREMENT_COUNTER_METHOD -> COUNTER_CONSTANTS;
            case ADD_GLOBAL_METHOD -> GLOBAL_CONSTANTS;
            case CALL_HELPER_METHOD -> HELPER_CONSTANTS;
            case CALL_STATIC_METHOD -> STATIC_METHOD_CONSTANTS;
            case SCRAMBLE_METHOD -> SCRAMBLE_CONSTANTS;
            case MIX_WITH_SEED_METHOD -> MIX_WITH_SEED_CONSTANTS;
            case ADD_METHOD, MIX_METHOD, ABSOLUTE_METHOD, CLAMP_METHOD,
                    SUM_TO_METHOD -> NO_CONSTANTS;
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
            case MIX_WITH_SEED_METHOD, INCREMENT_COUNTER_METHOD, ADD_GLOBAL_METHOD -> 3;
            case ADD_SEED_METHOD, CALL_HELPER_METHOD, CALL_STATIC_METHOD -> 2;
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
            case ADD_SEED_METHOD, INCREMENT_COUNTER_METHOD, ADD_GLOBAL_METHOD,
                    CALL_HELPER_METHOD, CALL_STATIC_METHOD -> 2;
            default -> throw new IllegalArgumentException("Unknown code id: " + codeId);
        };
    }
}
