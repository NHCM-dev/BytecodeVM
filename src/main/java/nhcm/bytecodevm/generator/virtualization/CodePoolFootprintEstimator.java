package nhcm.bytecodevm.generator.virtualization;

import nhcm.bytecodevm.data.vminsn.VMInstruction;
import nhcm.bytecodevm.data.vminsn.VMMethod;
import nhcm.bytecodevm.data.vminsn.VMOperand;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Estimates serialized VM payload before randomized CodePool bytecode is generated. */
public final class CodePoolFootprintEstimator
{
    private static final int RECORD_BYTES = ProtectedVMMethod.RECORD_SIZE * Integer.BYTES;
    private static final int HANDLER_SIZE = 4;

    private CodePoolFootprintEstimator()
    {
    }

    public static long estimate(VMMethod method)
    {
        return estimate(method, method.getInstructions(), 0, method.getInstructions().size());
    }

    public static long estimate(
            VMMethod method,
            List<VMInstruction> instructions,
            int from,
            int to)
    {
        long bytes = 256L;
        Set<Integer> constants = new HashSet<>();
        for (int index = from; index < to; index++)
        {
            VMInstruction instruction = instructions.get(index);
            bytes += instructionBytes(method, instruction, constants);
        }
        bytes += overlappingHandlerBytes(method, instructions, from, to);
        return bytes;
    }

    public static int weightedMidpoint(
            VMMethod method,
            List<VMInstruction> instructions,
            int from,
            int to)
    {
        if (to - from <= 1)
        {
            return from;
        }
        long total = 0L;
        for (int index = from; index < to; index++)
        {
            total += instructionBytes(method, instructions.get(index), new HashSet<>());
        }
        long target = Math.max(1L, total / 2L);
        long current = 0L;
        for (int index = from; index < to - 1; index++)
        {
            current += instructionBytes(method, instructions.get(index), new HashSet<>());
            if (current >= target)
            {
                return index + 1;
            }
        }
        return from + (to - from) / 2;
    }

    private static long instructionBytes(
            VMMethod method,
            VMInstruction instruction,
            Set<Integer> constants)
    {
        long bytes = RECORD_BYTES + Integer.BYTES * 5L;
        bytes += instruction.operandCount() * Integer.BYTES;
        for (VMOperand operand : instruction.operands)
        {
            if (operand.constantReference && constants.add(operand.rawValue))
            {
                bytes += constantBytes(method, operand.rawValue);
            }
        }
        return bytes;
    }

    private static long overlappingHandlerBytes(
            VMMethod method,
            List<VMInstruction> instructions,
            int from,
            int to)
    {
        if (from >= to)
        {
            return 0L;
        }
        int startPc = instructions.get(from).programCounter;
        int endPc = instructions.get(to - 1).nextProgramCounter;
        long bytes = 0L;
        for (int index = 0; index < method.exceptionHandlers.length; index += HANDLER_SIZE)
        {
            int handlerStart = method.exceptionHandlers[index];
            int handlerEnd = method.exceptionHandlers[index + 1];
            if (startPc < handlerEnd && endPc > handlerStart)
            {
                bytes += HANDLER_SIZE * Integer.BYTES;
                int typeIndex = method.exceptionHandlers[index + 3];
                if (typeIndex >= 0)
                {
                    bytes += constantBytes(method, typeIndex);
                }
            }
        }
        return bytes;
    }

    private static long constantBytes(VMMethod method, int index)
    {
        if (index < 0 || index >= method.constants.length)
        {
            return 0L;
        }
        Object value = method.constants[index];
        return switch (value)
        {
            case String string -> 32L + string.length() * 2L;
            case Type type -> 32L + type.getDescriptor().length() * 2L;
            case Handle ignored -> 96L;
            case ConstantDynamic ignored -> 160L;
            case Long ignored -> 24L;
            case Double ignored -> 24L;
            default -> 16L;
        };
    }
}
