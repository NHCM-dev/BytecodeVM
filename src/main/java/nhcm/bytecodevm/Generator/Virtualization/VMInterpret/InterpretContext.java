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
