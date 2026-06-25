package nhcm.bytecodevm.Generator.Virtualization;

import nhcm.bytecodevm.Config.BytecodeVMConfig;
import nhcm.bytecodevm.Data.CompiledMethod;
import nhcm.bytecodevm.Data.VMInsn.VMInstruction;
import nhcm.bytecodevm.Data.VMInsn.VMMethod;
import nhcm.bytecodevm.Data.VMInsn.VMOperand;
import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Utils.RandomUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProtectedVMMethod
{
    public static final int RECORD_SIZE = 6;
    public static final int HANDLER_SIZE = 4;

    public static final int LAYOUT_PC = 0;
    public static final int LAYOUT_ORIGINAL_PC = 1;
    public static final int LAYOUT_NEXT_PC = 2;
    public static final int LAYOUT_OPERAND_START = 3;
    public static final int LAYOUT_OPERAND_COUNT = 4;
    public static final int LAYOUT_CONSTANT_MASK = 5;

    public static final int SALT_LAYOUT = 0x5a2d3f17;
    public static final int SALT_OPCODE = 0x26a0c9d3;
    public static final int SALT_OPCODE_MAP = 0x1c9e8b57;
    public static final int SALT_OPERAND = 0x64f2a7b9;
    public static final int SALT_CONSTANT = 0x35bca913;
    public static final int SALT_HANDLER = 0x0f9186dd;
    public static final int SALT_ARRAY = 0x4e67c6a7;
    public static final int SALT_STRING = 0x6d4ac2f1;

    public final int[] opcodeStream;
    public final int[] operandStream;
    public final int[] layoutStream;
    public final Object[] constants;
    public final int[] exceptionHandlers;
    public final int[] opcodeMap;
    public final int methodKey;

    private ProtectedVMMethod(
            int[] opcodeStream,
            int[] operandStream,
            int[] layoutStream,
            Object[] constants,
            int[] exceptionHandlers,
            int[] opcodeMap,
            int methodKey)
    {
        this.opcodeStream = opcodeStream;
        this.operandStream = operandStream;
        this.layoutStream = layoutStream;
        this.constants = constants;
        this.exceptionHandlers = exceptionHandlers;
        this.opcodeMap = opcodeMap;
        this.methodKey = methodKey;
    }

    public static ProtectedVMMethod from(CompiledMethod compiledMethod, BytecodeVMConfig config)
    {
        VMMethod method = compiledMethod.vmMethod;
        List<VMInstruction> instructions = method.getInstructions();
        boolean protect = config.protectCodePool;
        int methodKey = protect ? nonZeroRandom() : 0;

        Map<Integer, Integer> virtualPcByOriginalPc = createVirtualPcMap(instructions, config);
        ConstantLayout constantLayout = createConstantLayout(method.constants, config);
        OpcodeLayout opcodeLayout = createOpcodeLayout(instructions, config, methodKey);
        List<VMInstruction> records = new ArrayList<>(instructions);
        if (protect && (config.shuffleInstructionBlocks || config.virtualizeInstructionAddresses))
        {
            RandomUtils.shuffle(records);
        }

        Map<VMInstruction, Integer> slotByInstruction = new IdentityHashMap<>();
        for (int slot = 0; slot < records.size(); slot++)
        {
            slotByInstruction.put(records.get(slot), slot);
        }

        int[] operandStarts = createOperandStarts(records, protect && config.splitCodeStreams);
        int totalOperands = 0;
        for (VMInstruction instruction : records)
        {
            totalOperands += instruction.operandCount();
        }

        int[] opcodeStream = new int[records.size()];
        int[] operandStream = new int[totalOperands];
        int[] layoutStream = new int[records.size() * RECORD_SIZE];

        for (VMInstruction instruction : records)
        {
            int slot = slotByInstruction.get(instruction);
            int virtualPc = virtualPcByOriginalPc.get(instruction.programCounter);
            int nextPc = instruction.nextProgramCounter >= method.code.length
                    ? -1
                    : virtualPcByOriginalPc.get(instruction.nextProgramCounter);
            int operandStart = operandStarts[slot];
            int operandCount = instruction.operandCount();
            int constantMask = protect && config.bindConstantsToOperands
                    ? constantMask(instruction)
                    : 0;

            int virtualOpcode = opcodeLayout.virtualByRealOpcode.get(instruction.mutatedOpcode);
            opcodeStream[slot] = protect && config.perMethodOpcodeMap
                    ? virtualOpcode ^ opcodeMix(methodKey, virtualPc, slot)
                    : virtualOpcode;

            setLayout(layoutStream, slot, LAYOUT_PC, virtualPc, methodKey, protect);
            setLayout(layoutStream, slot, LAYOUT_ORIGINAL_PC, instruction.programCounter, methodKey, protect);
            setLayout(layoutStream, slot, LAYOUT_NEXT_PC, nextPc, methodKey, protect);
            setLayout(layoutStream, slot, LAYOUT_OPERAND_START, operandStart, methodKey, protect);
            setLayout(layoutStream, slot, LAYOUT_OPERAND_COUNT, operandCount, methodKey, protect);
            setLayout(layoutStream, slot, LAYOUT_CONSTANT_MASK, constantMask, methodKey, protect);

            for (int operandIndex = 0; operandIndex < operandCount; operandIndex++)
            {
                VMOperand operand = instruction.operand(operandIndex);
                int value = remapOperand(
                        instruction,
                        operand,
                        operandIndex,
                        virtualPcByOriginalPc,
                        constantLayout.indexByOriginal,
                        config);
                if (protect && config.bindConstantsToOperands && operand.constantReference)
                {
                    value ^= constantMix(methodKey, virtualPc, instruction.mutatedOpcode, operandIndex);
                }
                if (protect && config.encryptOperands)
                {
                    value ^= operandMix(methodKey, virtualPc, instruction.mutatedOpcode, operandIndex, operandStart + operandIndex);
                }
                operandStream[operandStart + operandIndex] = value;
            }
        }

        return new ProtectedVMMethod(
                opcodeStream,
                operandStream,
                layoutStream,
                constantLayout.constants,
                protectExceptionHandlers(method, constantLayout.indexByOriginal, virtualPcByOriginalPc, methodKey, protect),
                opcodeLayout.encodedOpcodeMap,
                methodKey);
    }

    public static int mix(int key, int a, int b, int c)
    {
        int x = key ^ 0x9e3779b9;
        x ^= a + 0x7f4a7c15 + (x << 6) + (x >>> 2);
        x ^= b + 0x94d049bb + (x << 6) + (x >>> 2);
        x ^= c + 0x2545f491 + (x << 6) + (x >>> 2);
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        x *= 0x846ca68b;
        x ^= x >>> 16;
        return x;
    }

    public static int layoutMix(int methodKey, int slot, int field)
    {
        return mix(methodKey, slot, field, SALT_LAYOUT);
    }

    public static int opcodeMix(int methodKey, int virtualPc, int slot)
    {
        return mix(methodKey, virtualPc, slot, SALT_OPCODE);
    }

    public static int opcodeMapMix(int methodKey, int virtualOpcode)
    {
        return mix(methodKey, virtualOpcode, SALT_OPCODE_MAP, 0);
    }

    public static int operandMix(int methodKey, int virtualPc, int opcode, int operandIndex, int operandPosition)
    {
        return mix(methodKey ^ opcode, virtualPc, operandIndex, SALT_OPERAND ^ operandPosition);
    }

    public static int constantMix(int methodKey, int virtualPc, int opcode, int operandIndex)
    {
        return mix(methodKey ^ opcode, virtualPc, operandIndex, SALT_CONSTANT);
    }

    public static int handlerMix(int methodKey, int handlerSlot, int field)
    {
        return mix(methodKey, handlerSlot, field, SALT_HANDLER);
    }

    public static int arrayMix(int key, int index)
    {
        return mix(key, index, SALT_ARRAY, 0);
    }

    public static int stringMix(int key, int index)
    {
        return mix(key, index, SALT_STRING, 0);
    }

    public static EncodedString encodeString(String value)
    {
        int key = nonZeroRandom();
        int[] chars = new int[value.length()];
        for (int i = 0; i < chars.length; i++)
        {
            chars[i] = value.charAt(i) ^ stringMix(key, i);
        }
        return new EncodedString(chars, key);
    }

    private static int[] protectExceptionHandlers(
            VMMethod method,
            Map<Integer, Integer> constantIndexByOriginal,
            Map<Integer, Integer> virtualPcByOriginalPc,
            int methodKey,
            boolean protect)
    {
        int[] handlers = new int[method.exceptionHandlers.length];
        for (int index = 0; index < method.exceptionHandlers.length; index += HANDLER_SIZE)
        {
            int handlerSlot = index / HANDLER_SIZE;
            int startPc = method.exceptionHandlers[index];
            int endPc = method.exceptionHandlers[index + 1];
            int handlerPc = virtualPcByOriginalPc.get(method.exceptionHandlers[index + 2]);
            int typeIndex = method.exceptionHandlers[index + 3] < 0
                    ? -1
                    : constantIndexByOriginal.get(method.exceptionHandlers[index + 3]);
            handlers[index] = protect ? startPc ^ handlerMix(methodKey, handlerSlot, 0) : startPc;
            handlers[index + 1] = protect ? endPc ^ handlerMix(methodKey, handlerSlot, 1) : endPc;
            handlers[index + 2] = protect ? handlerPc ^ handlerMix(methodKey, handlerSlot, 2) : handlerPc;
            handlers[index + 3] = protect ? typeIndex ^ handlerMix(methodKey, handlerSlot, 3) : typeIndex;
        }
        return handlers;
    }

    private static Map<Integer, Integer> createVirtualPcMap(
            List<VMInstruction> instructions,
            BytecodeVMConfig config)
    {
        Map<Integer, Integer> virtualByOriginal = new LinkedHashMap<>();
        Set<Integer> used = new HashSet<>();
        boolean virtualize = config.protectCodePool && config.virtualizeInstructionAddresses;
        for (VMInstruction instruction : instructions)
        {
            int virtualPc = virtualize ? nonZeroRandom() : instruction.programCounter;
            while (!used.add(virtualPc))
            {
                virtualPc = nonZeroRandom();
            }
            virtualByOriginal.put(instruction.programCounter, virtualPc);
        }
        return virtualByOriginal;
    }

    private static ConstantLayout createConstantLayout(Object[] constants, BytecodeVMConfig config)
    {
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < constants.length; index++)
        {
            order.add(index);
        }
        if (config.protectCodePool && config.shuffleConstants)
        {
            RandomUtils.shuffle(order);
        }

        Object[] shuffled = new Object[constants.length];
        Map<Integer, Integer> indexByOriginal = new HashMap<>();
        for (int newIndex = 0; newIndex < order.size(); newIndex++)
        {
            int originalIndex = order.get(newIndex);
            shuffled[newIndex] = protectConstant(constants[originalIndex], config);
            indexByOriginal.put(originalIndex, newIndex);
        }
        return new ConstantLayout(shuffled, indexByOriginal);
    }

    private static Object protectConstant(Object value, BytecodeVMConfig config)
    {
        if (!config.protectCodePool)
        {
            return value;
        }
        if (value instanceof String string)
        {
            return encodeString(string);
        }
        if (value instanceof org.objectweb.asm.Type type)
        {
            return new EncodedType(type.getDescriptor());
        }
        return value;
    }

    private static OpcodeLayout createOpcodeLayout(
            List<VMInstruction> instructions,
            BytecodeVMConfig config,
            int methodKey)
    {
        List<Integer> realOpcodes = new ArrayList<>();
        for (VMInstruction instruction : instructions)
        {
            if (!realOpcodes.contains(instruction.mutatedOpcode))
            {
                realOpcodes.add(instruction.mutatedOpcode);
            }
        }

        List<Integer> virtualOpcodes = new ArrayList<>();
        for (int index = 0; index < realOpcodes.size(); index++)
        {
            virtualOpcodes.add(index);
        }
        if (config.protectCodePool && config.perMethodOpcodeMap)
        {
            RandomUtils.shuffle(virtualOpcodes);
        }

        Map<Integer, Integer> virtualByReal = new HashMap<>();
        int[] opcodeMap = new int[realOpcodes.size()];
        for (int index = 0; index < realOpcodes.size(); index++)
        {
            int realOpcode = realOpcodes.get(index);
            int virtualOpcode = virtualOpcodes.get(index);
            virtualByReal.put(realOpcode, virtualOpcode);
            opcodeMap[virtualOpcode] = config.protectCodePool && config.perMethodOpcodeMap
                    ? realOpcode ^ opcodeMapMix(methodKey, virtualOpcode)
                    : realOpcode;
        }
        return new OpcodeLayout(virtualByReal, opcodeMap);
    }

    private static int[] createOperandStarts(List<VMInstruction> records, boolean shuffle)
    {
        int[] starts = new int[records.size()];
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < records.size(); slot++)
        {
            slots.add(slot);
        }
        if (shuffle)
        {
            RandomUtils.shuffle(slots);
        }

        int cursor = 0;
        for (int slot : slots)
        {
            starts[slot] = cursor;
            cursor += records.get(slot).operandCount();
        }
        return starts;
    }

    private static int remapOperand(
            VMInstruction instruction,
            VMOperand operand,
            int operandIndex,
            Map<Integer, Integer> virtualPcByOriginalPc,
            Map<Integer, Integer> constantIndexByOriginal,
            BytecodeVMConfig config)
    {
        int value = operand.rawValue;
        if (isJumpTargetOperand(instruction.opcode, operandIndex))
        {
            Integer virtualTarget = virtualPcByOriginalPc.get(value);
            if (virtualTarget == null)
            {
                throw new IllegalStateException("Jump target has no virtual pc: " + value);
            }
            return virtualTarget;
        }
        if (operand.constantReference)
        {
            Integer remapped = constantIndexByOriginal.get(value);
            if (remapped == null)
            {
                throw new IllegalStateException("Constant operand has no protected index: " + value);
            }
            return remapped;
        }
        return value;
    }

    private static boolean isJumpTargetOperand(Opcs opcode, int operandIndex)
    {
        return switch (opcode)
        {
            case IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE,
                 IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE,
                 IF_ACMPEQ, IF_ACMPNE, IFNULL, IFNONNULL, GOTO -> operandIndex == 0;
            case TABLESWITCH -> operandIndex == 2 || operandIndex >= 4;
            case LOOKUPSWITCH -> operandIndex == 0 || (operandIndex >= 3 && (operandIndex & 1) == 1);
            default -> false;
        };
    }

    private static int constantMask(VMInstruction instruction)
    {
        int mask = 0;
        for (int operandIndex = 0; operandIndex < instruction.operandCount(); operandIndex++)
        {
            if (instruction.opcode.isConstantOperand(operandIndex) && operandIndex < Integer.SIZE)
            {
                mask |= 1 << operandIndex;
            }
        }
        return mask;
    }

    private static void setLayout(
            int[] layout,
            int slot,
            int field,
            int value,
            int methodKey,
            boolean protect)
    {
        layout[slot * RECORD_SIZE + field] = protect ? value ^ layoutMix(methodKey, slot, field) : value;
    }

    private static int nonZeroRandom()
    {
        int value;
        do
        {
            value = RandomUtils.randomInt();
        } while (value == 0);
        return value;
    }

    private record ConstantLayout(Object[] constants, Map<Integer, Integer> indexByOriginal)
    {
    }

    private record OpcodeLayout(Map<Integer, Integer> virtualByRealOpcode, int[] encodedOpcodeMap)
    {
    }

    public record EncodedString(int[] chars, int key)
    {
    }

    public record EncodedType(String descriptor)
    {
    }
}
