package nhcm.bytecodevm.Generator.Virtualization.VMInterpret;

import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.AdvInsn.Expr;
import nhcm.bytecodevm.AdvInsn.FieldAccess;
import nhcm.bytecodevm.AdvInsn.Local;
import nhcm.bytecodevm.Generator.GlobalClass.MethodFrameLayout;
import nhcm.bytecodevm.Generator.Virtualization.VMRuntimeLayout;
import nhcm.bytecodevm.Utils.Builder.FieldRef;
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
    public static final int EXCEPTION_HANDLERS = 41;
    public static final int INSTRUCTION_PC = 42;
    public static final int THROWN = 43;
    public static final int HANDLER_PC = 44;
    public static final int STACK_INDEX = 45;
    public static final int STACK_TYPE = 46;
    public static final int STACK_OBJECT = 47;
    public static final int INVOKE_RETURN_TYPE = 48;
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
    public final String programClassName;
    public final MethodFrameLayout frame;
    public final VMRuntimeLayout vm;
    public final LabelNode loopStart;

    public InterpretContext(String vmClassName, String frameClassName, LabelNode loopStart)
    {
        this(vmClassName, frameClassName, null, loopStart);
    }

    public InterpretContext(String vmClassName, String frameClassName, String programClassName, LabelNode loopStart)
    {
        this.vmClassName = vmClassName;
        this.frameClassName = frameClassName;
        this.programClassName = programClassName;
        this.frame = new MethodFrameLayout(frameClassName);
        this.vm = new VMRuntimeLayout(vmClassName, "L" + frameClassName + ";");
        this.loopStart = loopStart;
    }

    public Local program()
    {
        if (programClassName == null)
        {
            throw new IllegalStateException("programClassName is required for AdvInsnBuilder program() access");
        }
        return AdvInsnBuilder.local("program", programClassName, PROGRAM);
    }

    public Local frame()
    {
        return AdvInsnBuilder.local("frame", frameClassName, FRAME);
    }

    public Local code()
    {
        return AdvInsnBuilder.local("code", "[I", CODE);
    }

    public Local constants()
    {
        return AdvInsnBuilder.local("constants", "[Ljava/lang/Object;", CONSTANTS);
    }

    public Local opcode()
    {
        return AdvInsnBuilder.local("opcode", "I", OPCODE);
    }

    public Local exceptionHandlers()
    {
        return AdvInsnBuilder.local("exceptionHandlers", "[I", EXCEPTION_HANDLERS);
    }

    public Local instructionPc()
    {
        return AdvInsnBuilder.local("instructionPc", "I", INSTRUCTION_PC);
    }

    public Local thrown()
    {
        return AdvInsnBuilder.local("thrown", "java/lang/Throwable", THROWN);
    }

    public Local handlerPc()
    {
        return AdvInsnBuilder.local("handlerPc", "I", HANDLER_PC);
    }

    public FieldAccess frameField(FieldRef field)
    {
        return AdvInsnBuilder.field(frame(), field);
    }

    public FieldAccess frameProgramCounter()
    {
        return frameField(frame.programCounter);
    }

    public FieldAccess frameReturned()
    {
        return frameField(frame.returned);
    }

    public FieldAccess frameReturnValue()
    {
        return frameField(frame.returnValue);
    }

    public Expr tokenAtProgramCounter()
    {
        return AdvInsnBuilder.arrayAt(code(), frameProgramCounter());
    }

    public void nextToken(AdvInsnBuilder ib, Local target)
    {
        ib.set(target, tokenAtProgramCounter());
        ib.set(frameProgramCounter(), AdvInsnBuilder.plus(frameProgramCounter(), AdvInsnBuilder.constant(1)));
    }

    public void loadFrame(InsnBuilder ib)
    {
        ib.aload(FRAME);
    }

    public void popObject(InsnBuilder ib)
    {
        loadFrame(ib);
        frame.pop.invokeVirtual(ib);
    }

    public void popNumber(InsnBuilder ib, NumericType type, int local)
    {
        popObject(ib);
        type.unbox(ib);
        type.store(ib, local);
    }

    public void invokeFramePush(InsnBuilder ib)
    {
        frame.push.invokeVirtual(ib);
    }

    public void nextToken(InsnBuilder ib)
    {
        ib.aload(CODE);
        loadFrame(ib);
        ib.dup();
        frame.programCounter.get(ib);
        ib.dupX1();
        ib.iconst1();
        ib.iadd();
        frame.programCounter.put(ib);
        ib.iaload();
    }
}
