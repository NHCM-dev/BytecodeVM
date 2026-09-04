package nhcm.bytecodevm.generator;

import com.google.gson.GsonBuilder;
import nhcm.bytecodevm.BuildInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

/** Structured planning and generation statistics shared by inspect and protect. */
public record ObfuscationReport(
        String mode,
        String version,
        String input,
        String output,
        Long seed,
        String inputSha256,
        String outputSha256,
        long elapsedMillis,
        Map<String, Object> effectiveConfig,
        Map<String, String> watermark,
        int inputClasses,
        int inputResources,
        int totalMethods,
        int eligibleMethods,
        int explicitlyIncludedMethods,
        int explicitlyExcludedMethods,
        int matchedMethods,
        int calledMethodsIncluded,
        int calledMethodsExcluded,
        int fixedConstants,
        int preEncryptedStrings,
        int preEncryptedNumbers,
        int inlinedFields,
        int rewrittenFieldAccesses,
        int inlinedProtectedMethods,
        int rewrittenProtectedCalls,
        Map<String, Integer> skippedMethods,
        int vmSetCount,
        List<VMSet> vmSets,
        List<MethodPlan> methods,
        List<Diagnostic> diagnostics,
        int outputClasses,
        int outputResources,
        int generatedClasses,
        boolean outputVerified)
{
    public ObfuscationReport
    {
        vmSets = List.copyOf(vmSets);
        methods = List.copyOf(methods);
        diagnostics = List.copyOf(diagnostics);
        effectiveConfig = immutableOrderedMap(effectiveConfig);
        watermark = immutableOrderedMap(watermark);
        skippedMethods = immutableOrderedMap(skippedMethods);
    }

    public static String currentVersion()
    {
        return BuildInfo.VERSION;
    }

    public ObfuscationReport withElapsedMillis(long value)
    {
        return new ObfuscationReport(
                mode, version, input, output, seed, inputSha256, outputSha256, value,
                effectiveConfig, watermark,
                inputClasses, inputResources, totalMethods, eligibleMethods,
                explicitlyIncludedMethods, explicitlyExcludedMethods, matchedMethods,
                calledMethodsIncluded, calledMethodsExcluded, fixedConstants,
                preEncryptedStrings, preEncryptedNumbers,
                inlinedFields, rewrittenFieldAccesses, inlinedProtectedMethods, rewrittenProtectedCalls,
                skippedMethods, vmSetCount, vmSets, methods, diagnostics,
                outputClasses, outputResources, generatedClasses,
                outputVerified);
    }

    public ObfuscationReport withOutputVerified(boolean value)
    {
        return new ObfuscationReport(
                mode, version, input, output, seed, inputSha256, outputSha256, elapsedMillis,
                effectiveConfig, watermark,
                inputClasses, inputResources, totalMethods, eligibleMethods,
                explicitlyIncludedMethods, explicitlyExcludedMethods, matchedMethods,
                calledMethodsIncluded, calledMethodsExcluded, fixedConstants,
                preEncryptedStrings, preEncryptedNumbers,
                inlinedFields, rewrittenFieldAccesses, inlinedProtectedMethods, rewrittenProtectedCalls,
                skippedMethods, vmSetCount, vmSets, methods, diagnostics,
                outputClasses, outputResources, generatedClasses,
                value);
    }

    public Map<String, Integer> structures()
    {
        Map<String, Integer> structures = new LinkedHashMap<>();
        for (VMSet vmSet : vmSets)
        {
            structures.merge(vmSet.structure(), 1, Integer::sum);
        }
        return structures;
    }

    public void writeJson(Path path) throws IOException
    {
        Path absolute = path.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null)
        {
            Files.createDirectories(parent);
        }
        String json = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create()
                .toJson(this);
        Files.writeString(absolute, json + System.lineSeparator());
    }

    public record VMSet(String name, String structure, int methodCount)
    {
    }

    public record MethodPlan(
            String owner,
            String name,
            String descriptor,
            String selection,
            String vmSet,
            String structure)
    {
    }

    public record Diagnostic(String level, String code, String message)
    {
    }

    private static <K, V> Map<K, V> immutableOrderedMap(Map<K, V> values)
    {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
