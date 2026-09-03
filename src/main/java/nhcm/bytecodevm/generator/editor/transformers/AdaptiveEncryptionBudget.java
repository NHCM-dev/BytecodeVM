package nhcm.bytecodevm.generator.editor.transformers;

import org.objectweb.asm.commons.CodeSizeEvaluator;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

import java.util.Collection;

/** Conservative per-method growth budget for constant pre-encryption. */
final class AdaptiveEncryptionBudget
{
    enum Kind
    {
        STRING(8_000, 5_000, 48, 2_048),
        NUMBER(12_000, 5_000, 512, Integer.MAX_VALUE);

        private final int maximumMethodSize;
        private final int maximumGrowth;
        private final int maximumConstants;
        private final int maximumUnits;

        Kind(int maximumMethodSize, int maximumGrowth, int maximumConstants, int maximumUnits)
        {
            this.maximumMethodSize = maximumMethodSize;
            this.maximumGrowth = maximumGrowth;
            this.maximumConstants = maximumConstants;
            this.maximumUnits = maximumUnits;
        }
    }

    private int bytesRemaining;
    private int constantsRemaining;
    private int unitsRemaining;
    private int estimatedGrowth;

    private AdaptiveEncryptionBudget(MethodNode method, Kind kind, int candidateCount, long totalUnits)
    {
        int currentSize = bytecodeSize(method);
        int sizeHeadroom = Math.max(0, kind.maximumMethodSize - currentSize);

        // Large candidate sets are exactly where a full transform is dangerous. Keep
        // an additional reserve that grows with both count and string content size.
        long pressure = Math.max(0, candidateCount - 16L) * 8L + Math.max(0L, totalUnits - 512L) / 8L;
        int pressureReserve = (int) Math.min(kind.maximumGrowth / 3L, pressure);
        bytesRemaining = Math.max(0, Math.min(kind.maximumGrowth, sizeHeadroom) - pressureReserve);

        int sizeAdjustedCount = Math.max(0, kind.maximumConstants - currentSize / 96);
        constantsRemaining = Math.min(candidateCount, sizeAdjustedCount);
        unitsRemaining = kind.maximumUnits == Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : Math.max(0, kind.maximumUnits - currentSize / 8);
    }

    static AdaptiveEncryptionBudget strings(MethodNode method, int candidates, long characters)
    {
        return new AdaptiveEncryptionBudget(method, Kind.STRING, candidates, characters);
    }

    static AdaptiveEncryptionBudget numbers(MethodNode method, int candidates)
    {
        return new AdaptiveEncryptionBudget(method, Kind.NUMBER, candidates, 0L);
    }

    boolean reserve(int generatedBytes, int originalBytes, int units)
    {
        int growth = growth(generatedBytes, originalBytes);
        if (constantsRemaining <= 0 || bytesRemaining < growth || unitsRemaining < units)
        {
            return false;
        }
        constantsRemaining--;
        bytesRemaining -= growth;
        if (unitsRemaining != Integer.MAX_VALUE)
        {
            unitsRemaining -= units;
        }
        estimatedGrowth += growth;
        return true;
    }

    void release(int generatedBytes, int originalBytes, int units)
    {
        int growth = growth(generatedBytes, originalBytes);
        constantsRemaining++;
        bytesRemaining += growth;
        if (unitsRemaining != Integer.MAX_VALUE)
        {
            unitsRemaining += units;
        }
        estimatedGrowth -= growth;
    }

    static Global global(Collection<ClassNode> classes, Kind kind)
    {
        long sourceBytes = 0L;
        int methodCount = 0;
        for (ClassNode owner : classes)
        {
            for (MethodNode method : owner.methods)
            {
                if (method.instructions != null && method.instructions.size() != 0)
                {
                    sourceBytes += bytecodeSize(method);
                    methodCount++;
                }
            }
        }

        return switch (kind)
        {
            case STRING -> new Global(
                    clamp(sourceBytes / 2L, 128_000L, 1_500_000L),
                    clamp((long) methodCount * 4L, 256L, 8_192L));
            case NUMBER -> new Global(
                    clamp(sourceBytes / 4L, 128_000L, 750_000L),
                    clamp((long) methodCount * 16L, 2_048L, 65_536L));
        };
    }

    private static int growth(int generatedBytes, int originalBytes)
    {
        return Math.max(1, generatedBytes - originalBytes);
    }

    private static int clamp(long value, long minimum, long maximum)
    {
        return (int) Math.max(minimum, Math.min(maximum, value));
    }

    static final class Global
    {
        private int bytesRemaining;
        private int constantsRemaining;

        private Global(int bytesRemaining, int constantsRemaining)
        {
            this.bytesRemaining = bytesRemaining;
            this.constantsRemaining = constantsRemaining;
        }

        boolean reserve(int generatedBytes, int originalBytes)
        {
            int growth = growth(generatedBytes, originalBytes);
            if (constantsRemaining <= 0 || bytesRemaining < growth)
            {
                return false;
            }
            constantsRemaining--;
            bytesRemaining -= growth;
            return true;
        }

        void release(int generatedBytes, int originalBytes)
        {
            constantsRemaining++;
            bytesRemaining += growth(generatedBytes, originalBytes);
        }
    }

    int estimatedGrowth()
    {
        return estimatedGrowth;
    }

    static int bytecodeSize(MethodNode method)
    {
        CodeSizeEvaluator evaluator = new CodeSizeEvaluator(null);
        method.accept(evaluator);
        return evaluator.getMaxSize();
    }

    static int bytecodeSize(InsnList instructions)
    {
        CodeSizeEvaluator evaluator = new CodeSizeEvaluator(null);
        instructions.accept(evaluator);
        return evaluator.getMaxSize();
    }

    static int bytecodeSize(AbstractInsnNode instruction)
    {
        CodeSizeEvaluator evaluator = new CodeSizeEvaluator(null);
        instruction.accept(evaluator);
        return evaluator.getMaxSize();
    }
}
