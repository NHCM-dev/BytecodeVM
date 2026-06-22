package nhcm.bytecodevm.Enums;

import org.objectweb.asm.Opcodes;

public enum Acc
{
    PUBLIC(Opcodes.ACC_PUBLIC),
    PRIVATE(Opcodes.ACC_PRIVATE),
    PROTECTED(Opcodes.ACC_PROTECTED),
    STATIC(Opcodes.ACC_STATIC),
    FINAL(Opcodes.ACC_FINAL),
    SUPER(Opcodes.ACC_SUPER),
    SYNCHRONIZED(Opcodes.ACC_SYNCHRONIZED),
    OPEN(Opcodes.ACC_OPEN),
    TRANSITIVE(Opcodes.ACC_TRANSITIVE),
    VOLATILE(Opcodes.ACC_VOLATILE),
    BRIDGE(Opcodes.ACC_BRIDGE),
    STATIC_PHASE(Opcodes.ACC_STATIC_PHASE),
    VARARGS(Opcodes.ACC_VARARGS),
    TRANSIENT(Opcodes.ACC_TRANSIENT),
    NATIVE(Opcodes.ACC_NATIVE),
    INTERFACE(Opcodes.ACC_INTERFACE),
    ABSTRACT(Opcodes.ACC_ABSTRACT),
    STRICT(Opcodes.ACC_STRICT),
    SYNTHETIC(Opcodes.ACC_SYNTHETIC),
    ANNOTATION(Opcodes.ACC_ANNOTATION),
    ENUM(Opcodes.ACC_ENUM),
    MANDATED(Opcodes.ACC_MANDATED),
    MODULE(Opcodes.ACC_MODULE),
    RECORD(Opcodes.ACC_RECORD),
    DEPRECATED(Opcodes.ACC_DEPRECATED);

    public final int asmOpcodeValue;

    Acc(int asmOpcodeValue)
    {
        this.asmOpcodeValue = asmOpcodeValue;
    }

    public static Acc fromAsmOpcodeValue(int asmOpcodeValue)
    {
        for (Acc acc : values())
        {
            if (acc.asmOpcodeValue == asmOpcodeValue)
            {
                return acc;
            }
        }
        throw new IllegalArgumentException("Unknown access modifier: " + asmOpcodeValue);
    }
}
