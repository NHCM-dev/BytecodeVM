package nhcm.bytecodevm.generator.editor.transformers;

/** Selection statistics for an adaptive constant pre-encryption pass. */
public record ConstantEncryptionStats(int candidates, int encrypted, int skipped, long estimatedGrowth)
{
    public static ConstantEncryptionStats empty()
    {
        return new ConstantEncryptionStats(0, 0, 0, 0L);
    }
}
