package nhcm.bytecodevm.generator.editor.transformers;

import nhcm.bytecodevm.config.BytecodeVMConfig;
import nhcm.bytecodevm.generator.abstracts.Transformer;
import nhcm.bytecodevm.config.sdk.SdkAnnotationReader;
import nhcm.bytecodevm.utils.MethodUtils;
import nhcm.bytecodevm.utils.RandomUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Reconstructs primitive number constants from per-site encrypted bit patterns. */
public class NumberTransformer extends Transformer
{
    private ConstantEncryptionStats stats = ConstantEncryptionStats.empty();

    public NumberTransformer(BytecodeVMConfig config)
    {
        super(config, "preEncryptNumbers");
    }

    @Override
    public int transform(Collection<ClassNode> classNodes)
    {
        int changed = 0;
        int candidatesSeen = 0;
        long estimatedGrowth = 0L;
        AdaptiveEncryptionBudget.Global globalBudget =
                AdaptiveEncryptionBudget.global(classNodes, AdaptiveEncryptionBudget.Kind.NUMBER);
        for (ClassNode owner : classNodes)
        {
            for (MethodNode method : owner.methods)
            {
                if (!MethodUtils.hasBody(method))
                {
                    continue;
                }

                SdkAnnotationReader.MethodDirectives directives =
                        SdkAnnotationReader.methodDirectives(owner, method);
                Boolean sdkOverride = directives.excluded()
                        ? Boolean.FALSE
                        : directives.options().preEncryptNumbers();
                if (!config.preEncryptNumbers && sdkOverride == null)
                {
                    continue;
                }
                if (!shouldEncrypt(owner, method, sdkOverride))
                {
                    continue;
                }

                List<AbstractInsnNode> constants = new ArrayList<>();
                for (AbstractInsnNode instruction : method.instructions)
                {
                    if (numberValue(instruction) != null)
                    {
                        constants.add(instruction);
                    }
                }
                if (constants.isEmpty())
                {
                    continue;
                }

                candidatesSeen += constants.size();
                AdaptiveEncryptionBudget budget = AdaptiveEncryptionBudget.numbers(method, constants.size());
                RandomUtils.shuffle(constants);

                int extraStack = 0;
                for (AbstractInsnNode instruction : constants)
                {
                    Number value = numberValue(instruction);
                    InsnList replacement = replacement(value);
                    int generatedBytes = AdaptiveEncryptionBudget.bytecodeSize(replacement);
                    int originalBytes = AdaptiveEncryptionBudget.bytecodeSize(instruction);
                    if (!globalBudget.reserve(generatedBytes, originalBytes))
                    {
                        continue;
                    }
                    if (!budget.reserve(generatedBytes, originalBytes, 1))
                    {
                        globalBudget.release(generatedBytes, originalBytes);
                        continue;
                    }
                    method.instructions.insertBefore(instruction, replacement);
                    method.instructions.remove(instruction);
                    extraStack = Math.max(extraStack,
                            value instanceof Long || value instanceof Double ? 2 : 1);
                    changed++;
                }
                estimatedGrowth += budget.estimatedGrowth();
                method.maxStack += extraStack;
            }
        }
        stats = new ConstantEncryptionStats(
                candidatesSeen,
                changed,
                candidatesSeen - changed,
                estimatedGrowth);
        return changed;
    }

    public ConstantEncryptionStats stats()
    {
        return stats;
    }

    private static Number numberValue(AbstractInsnNode instruction)
    {
        int opcode = instruction.getOpcode();
        if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5)
        {
            return opcode - Opcodes.ICONST_0;
        }
        if (instruction instanceof IntInsnNode intInsn &&
            (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH))
        {
            return intInsn.operand;
        }
        if (opcode == Opcodes.LCONST_0 || opcode == Opcodes.LCONST_1)
        {
            return (long) (opcode - Opcodes.LCONST_0);
        }
        if (opcode >= Opcodes.FCONST_0 && opcode <= Opcodes.FCONST_2)
        {
            return (float) (opcode - Opcodes.FCONST_0);
        }
        if (opcode == Opcodes.DCONST_0 || opcode == Opcodes.DCONST_1)
        {
            return (double) (opcode - Opcodes.DCONST_0);
        }
        if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof Number number)
        {
            return number;
        }
        return null;
    }

    private static InsnList replacement(Number value)
    {
        return switch (value)
        {
            case Integer integer -> encryptedInt(integer);
            case Long number -> encryptedLong(number);
            case Float number -> encryptedFloat(number);
            case Double number -> encryptedDouble(number);
            default -> throw new IllegalArgumentException(
                    "Unsupported number constant: " + value.getClass().getName());
        };
    }

    private static InsnList encryptedInt(int value)
    {
        int key = randomNonZeroInt();
        InsnList instructions = new InsnList();
        instructions.add(new LdcInsnNode(value ^ key));
        instructions.add(new LdcInsnNode(key));
        instructions.add(new InsnNode(Opcodes.IXOR));
        return instructions;
    }

    private static InsnList encryptedLong(long value)
    {
        long key = randomNonZeroLong();
        InsnList instructions = new InsnList();
        instructions.add(new LdcInsnNode(value ^ key));
        instructions.add(new LdcInsnNode(key));
        instructions.add(new InsnNode(Opcodes.LXOR));
        return instructions;
    }

    private static InsnList encryptedFloat(float value)
    {
        InsnList instructions = encryptedInt(Float.floatToRawIntBits(value));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "java/lang/Float",
                "intBitsToFloat",
                "(I)F",
                false));
        return instructions;
    }

    private static InsnList encryptedDouble(double value)
    {
        InsnList instructions = encryptedLong(Double.doubleToRawLongBits(value));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "java/lang/Double",
                "longBitsToDouble",
                "(J)D",
                false));
        return instructions;
    }

    private static int randomNonZeroInt()
    {
        int key;
        do
        {
            key = RandomUtils.randomInt();
        } while (key == 0);
        return key;
    }

    private static long randomNonZeroLong()
    {
        long key;
        do
        {
            key = ((long) RandomUtils.randomInt() << Integer.SIZE) |
                  (RandomUtils.randomInt() & 0xFFFF_FFFFL);
        } while (key == 0L);
        return key;
    }
}
