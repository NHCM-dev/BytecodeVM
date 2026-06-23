package nhcm.bytecodevm.Generator.Virtualization.VMInterpret;

import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.tree.LabelNode;

public final class InterpretContext
{
    public static final int PROGRAM = 0;
    public static final int FRAME = 1;
    public static final int CODE = 2;
    public static final int CONSTANTS = 3;
    public static final int OPCODE = 4;
    public static final int RIGHT_VALUE = 5;
    public static final int LEFT_VALUE = 7;
    public static final int MIDDLE_VALUE = 24;
    public static final int DUP_VALUE_1 = 24;
    public static final int DUP_VALUE_2 = 25;
    public static final int DUP_VALUE_3 = 26;
    public static final int DUP_VALUE_4 = 27;
    public static final int DUP_WIDTH_1 = 28;
    public static final int DUP_WIDTH_2 = 29;
    public static final int DUP_WIDTH_3 = 30;
    public static final int DUP_WIDTH_4 = 31;
    public static final int FIELD_OWNER = 32;
    public static final int FIELD_NAME = 33;
    public static final int FIELD_DESCRIPTOR = 34;
    public static final int FIELD_RESULT = 35;
    public static final int ARRAY_ATYPE = 36;
    public static final int ARRAY_COMPONENT = 37;
    public static final int ARRAY_DIMENSIONS = 38;
    public static final int ARRAY_LENGTHS = 39;
    public static final int ARRAY_INDEX = 40;
    public static final int JUMP_TARGET = 9;
    public static final int SWITCH_KEY = 10;
    public static final int SWITCH_MIN = 11;
    public static final int SWITCH_COUNT = 12;
    public static final int SWITCH_INDEX = 13;
    public static final int SWITCH_CANDIDATE = 14;
    public static final int INVOKE_OWNER = 15;
    public static final int INVOKE_NAME = 16;
    public static final int INVOKE_TYPE = 17;
    public static final int INVOKE_ARGUMENTS = 18;
    public static final int INVOKE_INDEX = 19;
    public static final int INVOKE_RECEIVER = 20;
    public static final int INVOKE_RESULT = 21;
    public static final int FIELD_VALUE = 22;
    public static final int FIELD_RECEIVER = 23;

    public final String vmClassName;
    public final String frameClassName;
    public final LabelNode loopStart;

    public InterpretContext(String vmClassName, String frameClassName, LabelNode loopStart)
    {
        this.vmClassName = vmClassName;
        this.frameClassName = frameClassName;
        this.loopStart = loopStart;
    }

    public void loadFrame(InsnBuilder ib)
    {
        ib.aload(FRAME);
    }

    public void popObject(InsnBuilder ib)
    {
        loadFrame(ib);
        ib.invokeVirtual(frameClassName, "pop", "()Ljava/lang/Object;");
    }

    public void popNumber(InsnBuilder ib, NumericType type, int local)
    {
        popObject(ib);
        type.unbox(ib);
        type.store(ib, local);
    }

    public void invokeFramePush(InsnBuilder ib)
    {
        ib.invokeVirtual(frameClassName, "push", "(Ljava/lang/Object;)V");
    }

    public void nextToken(InsnBuilder ib)
    {
        ib.aload(CODE);
        loadFrame(ib);
        ib.invokeStatic(
                vmClassName,
                "nextToken",
                "([IL" + frameClassName + ";)I");
    }
}
