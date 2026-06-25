package nhcm.bytecodevm.Generator.Virtualization.VMInterpret;

import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.AdvInsn.Expr;
import nhcm.bytecodevm.AdvInsn.FieldAccess;
import nhcm.bytecodevm.AdvInsn.Local;
import nhcm.bytecodevm.Generator.GlobalClass.MethodFrameLayout;
import nhcm.bytecodevm.Generator.Virtualization.VMRuntimeLayout;
import nhcm.bytecodevm.Utils.Builder.FieldRef;
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
    public static final int INSTRUCTION_INDEX = 49;
    public static final int ORIGINAL_PC = 50;
    public static final int OPERAND_INDEX = 51;
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
        this.vm = new VMRuntimeLayout(
                vmClassName,
                "L" + frameClassName + ";",
                programClassName == null ? null : "L" + programClassName + ";");
        this.loopStart = loopStart;
    }

    public Local program()
    {
        if (programClassName == null)
        {
            throw new IllegalStateException("programClassName is required for program() access");
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
        return intLocal("opcode", OPCODE);
    }

    public Local instructionIndex()
    {
        return intLocal("instructionIndex", INSTRUCTION_INDEX);
    }

    public Local originalPc()
    {
        return intLocal("originalPc", ORIGINAL_PC);
    }

    public Local operandIndex()
    {
        return intLocal("operandIndex", OPERAND_INDEX);
    }

    public Local rightValue(NumericType type)
    {
        return local("rightValue", type.descriptor(), RIGHT_VALUE);
    }

    public Local leftValue(NumericType type)
    {
        return local("leftValue", type.descriptor(), LEFT_VALUE);
    }

    public Local middleValue()
    {
        return intLocal("middleValue", MIDDLE_VALUE);
    }

    public Local objectLocal(String name, int slot)
    {
        return local(name, "java/lang/Object", slot);
    }

    public Local intLocal(String name, int slot)
    {
        return local(name, "I", slot);
    }

    public Local longLocal(String name, int slot)
    {
        return local(name, "J", slot);
    }

    public Local floatLocal(String name, int slot)
    {
        return local(name, "F", slot);
    }

    public Local doubleLocal(String name, int slot)
    {
        return local(name, "D", slot);
    }

    public Local local(String name, String type, int slot)
    {
        return AdvInsnBuilder.local(name, type, slot);
    }

    public Local localForSlot(String name, int slot)
    {
        return switch (slot)
        {
            case ARRAY_LENGTHS -> local(name, "[I", slot);
            case ARRAY_COMPONENT, INVOKE_RETURN_TYPE -> local(name, "java/lang/Class", slot);
            case INVOKE_TYPE -> local(name, "java/lang/invoke/MethodType", slot);
            case INVOKE_ARGUMENTS -> local(name, "[Ljava/lang/Object;", slot);
            default -> objectLocal(name, slot);
        };
    }

    public Local exceptionHandlers()
    {
        return local("exceptionHandlers", "[I", EXCEPTION_HANDLERS);
    }

    public Local instructionPc()
    {
        return intLocal("instructionPc", INSTRUCTION_PC);
    }

    public Local thrown()
    {
        return local("thrown", "java/lang/Throwable", THROWN);
    }

    public Local handlerPc()
    {
        return intLocal("handlerPc", HANDLER_PC);
    }

    public Local stackIndex()
    {
        return intLocal("stackIndex", STACK_INDEX);
    }

    public Local stackType()
    {
        return intLocal("stackType", STACK_TYPE);
    }

    public Local stackObject()
    {
        return objectLocal("stackObject", STACK_OBJECT);
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

    public Expr locals()
    {
        return frameField(frame.locals);
    }

    public Expr stack()
    {
        return frameField(frame.stack);
    }

    public Expr stackTypes()
    {
        return frameField(frame.stackTypes);
    }

    public Expr stackWidths()
    {
        return frameField(frame.stackWidths);
    }

    public Expr stackWords()
    {
        return frameField(frame.stackWords);
    }

    public Expr tokenAtProgramCounter()
    {
        return AdvInsnBuilder.arrayAt(code(), frameProgramCounter());
    }

    public Expr constantString(Expr index)
    {
        return AdvInsnBuilder.callStatic(
                vm.owner,
                vm.constantString.name(),
                "java/lang/String",
                constants(),
                index);
    }

    public Expr loadClass(Expr className)
    {
        return AdvInsnBuilder.callStatic(
                vm.owner,
                vm.loadOwner.name(),
                "java/lang/Class",
                className);
    }

    public void nextToken(AdvInsnBuilder ib, Local target)
    {
        nextOperand(ib, target);
    }

    public void nextOperand(AdvInsnBuilder ib, Local target)
    {
        ib.set(target, AdvInsnBuilder.callStatic(
                vm.owner,
                vm.decodeOperand.name(),
                "I",
                program(),
                instructionIndex(),
                operandIndex(),
                opcode()));
        ib.increment(operandIndex(), 1);
    }
}
