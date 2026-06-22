package nhcm.bytecodevm.Data.VMInsn;

import nhcm.bytecodevm.Tools.OpcMutator;
import nhcm.bytecodevm.Enums.Opcs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

public class VMMethod implements Iterable<VMInstruction>
{
    public final int[] code;
    public final Object[] constants;
    public final int maxLocals;
    public final int maxStack;

    private final OpcMutator opcMutator;
    private List<VMInstruction> instructionCache;
    private Map<Integer, VMInstruction> instructionByPc;

    public VMMethod(int[] code, Object[] constants, int maxLocals, int maxStack)
    {
        this(code, constants, maxLocals, maxStack, null);
    }

    public VMMethod(
            int[] code,
            Object[] constants,
            int maxLocals,
            int maxStack,
            OpcMutator opcMutator)
    {
        this.code = Objects.requireNonNull(code, "code");
        this.constants = Objects.requireNonNull(constants, "constants");
        this.maxLocals = maxLocals;
        this.maxStack = maxStack;
        this.opcMutator = opcMutator;
    }

    public synchronized List<VMInstruction> getInstructions()
    {
        if (opcMutator == null)
        {
            throw new IllegalStateException(
                    "This VMMethod has no OpcMutator; use getInstructions(mutator)");
        }
        if (instructionCache == null)
        {
            instructionCache = decodeInstructions(opcMutator);
            Map<Integer, VMInstruction> byPc = new HashMap<>();
            for (VMInstruction instruction : instructionCache)
            {
                byPc.put(instruction.programCounter, instruction);
            }
            instructionByPc = Collections.unmodifiableMap(byPc);
        }
        return instructionCache;
    }

    public List<VMInstruction> getInstructions(OpcMutator mutator)
    {
        Objects.requireNonNull(mutator, "mutator");
        if (mutator == opcMutator)
        {
            return getInstructions();
        }
        return decodeInstructions(mutator);
    }

    private List<VMInstruction> decodeInstructions(OpcMutator mutator)
    {
        List<VMInstruction> instructions = new ArrayList<>();
        int pc = 0;

        while (pc < code.length)
        {
            int instructionPc = pc;
            int mutatedOpcode = code[pc++];
            Opcs opcode = mutator.fromMutated(mutatedOpcode);

            if (opcode == null)
            {
                throw malformed("Unknown opcode " + mutatedOpcode, instructionPc);
            }

            int operandCount;
            try
            {
                operandCount = opcode.getOperandCount(code, pc);
            }
            catch (RuntimeException exception)
            {
                throw malformed("Cannot read operands for " + opcode, instructionPc, exception);
            }

            if (operandCount < 0 || pc + operandCount > code.length)
            {
                throw malformed("Truncated operands for " + opcode, instructionPc);
            }

            List<VMOperand> operands = new ArrayList<>(operandCount);
            for (int operandIndex = 0; operandIndex < operandCount; operandIndex++)
            {
                int rawValue = code[pc++];
                boolean constantReference = opcode.isConstantOperand(operandIndex);
                Object value = rawValue;

                if (constantReference)
                {
                    if (rawValue < 0 || rawValue >= constants.length)
                    {
                        throw malformed(
                                "Invalid constant #" + rawValue + " for " + opcode,
                                instructionPc);
                    }
                    value = constants[rawValue];
                }

                operands.add(new VMOperand(
                        operandIndex,
                        rawValue,
                        constantReference,
                        value));
            }

            instructions.add(new VMInstruction(
                    instructionPc,
                    pc,
                    mutatedOpcode,
                    opcode,
                    operands));
        }

        return Collections.unmodifiableList(instructions);
    }

    public VMInstruction instructionAt(int pc)
    {
        getInstructions();
        VMInstruction instruction = instructionByPc.get(pc);
        if (instruction != null) return instruction;
        throw new NoSuchElementException("No VM instruction at pc " + pc);
    }

    @Override
    public Iterator<VMInstruction> iterator()
    {
        return getInstructions().iterator();
    }

    private static IllegalArgumentException malformed(String message, int pc)
    {
        return new IllegalArgumentException(message + " at pc " + pc);
    }

    private static IllegalArgumentException malformed(
            String message,
            int pc,
            RuntimeException cause)
    {
        return new IllegalArgumentException(message + " at pc " + pc, cause);
    }
}
