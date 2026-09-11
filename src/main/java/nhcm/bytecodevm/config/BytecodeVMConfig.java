package nhcm.bytecodevm.config;

import lombok.Builder;
import nhcm.bytecodevm.BytecodeVM;
import nhcm.bytecodevm.config.sdk.SdkAnnotationReader;
import nhcm.bytecodevm.enums.VMStructure;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Builder(toBuilder = true)
public class BytecodeVMConfig
{
    private static final Set<String> CONFIG_KEYS = java.util.Set.of(
            "input", "output", "createMode", "location", "renameMode", "interpretMode",
            "vmStructure","vmCount",
            "protectCodePool", "dynamicConstantDecrypt", "virtualizeInstructionAddresses", "encryptOperands",
            "perMethodOpcodeMap", "shuffleConstants", "bindConstantsToOperands", "splitCodeStreams",
            "shuffleInstructionBlocks", "obfuscateDispatch", "dynamicCodePoolBuild", "dynamicStateKey", "virtualControlFlowGraph",
            "constantFix", "preEncryptStrings", "preEncryptNumbers", "removeAnnotations",
            "inlineFields", "inlineCalledProtectedMethods", "inlineStaticFinals", "virtualizeConstructors",
            "annotationOnly", "privateFieldOnly", "ignorePublicCalls", "includeReferencedMethods",
            "watermark",
            "includeMethodsCalledWithin", "excludeMethodsCalledWithin", "virtualizeInvocationBridges",
            "vmIntegrityCheck", "vmIntegrityCheckRatio", "vmIntegrityRecheckInterval", "superInstruction",
            "superinstrcution", "superInstructionCombineRange", "superinstrcutioncombinerange",
            "superInstructionMode", "superInstructionMaxHandlers", "superInstructionMinFrequency",
            "obfuscateInterpretBranch", "interpretBranchCases",
            "includes", "excludes", "exclusions", "mutateMode");
    private static final Set<String> MATCH_GROUP_KEYS = java.util.Set.of(
            "all", "protectCodePool", "dynamicConstantDecrypt", "virtualizeInstructionAddresses", "encryptOperands",
            "perMethodOpcodeMap", "shuffleConstants", "bindConstantsToOperands", "splitCodeStreams",
            "shuffleInstructionBlocks", "obfuscateDispatch", "dynamicCodePoolBuild", "dynamicStateKey",
            "virtualControlFlowGraph",
            "constantFix", "preEncryptStrings", "preEncryptNumbers",
            "inlineFields", "inlineCalledProtectedMethods", "inlineStaticFinals", "virtualizeConstructors",
            "superInstruction", "obfuscateInterpretBranch");

    public final Path inputFile;
    public final Path outputFile;
    public final VMCreateMode createMode;
    public final VMLocation location;
    public final RenameMode renameMode;
    public final InterpretMode interpretMode;
    public final VMStructure vmStructure;
    public final int vmCount;
    public final boolean protectCodePool;
    public final boolean dynamicConstantDecrypt;
    public final boolean virtualizeInstructionAddresses;
    public final boolean encryptOperands;
    public final boolean perMethodOpcodeMap;
    public final boolean shuffleConstants;
    public final boolean bindConstantsToOperands;
    public final boolean splitCodeStreams;
    public final boolean shuffleInstructionBlocks;
    public final boolean obfuscateDispatch;
    public final boolean dynamicCodePoolBuild;
    public final boolean dynamicStateKey;
    public final boolean virtualControlFlowGraph;

    public final boolean constantFix;
    public final boolean preEncryptStrings;
    public final boolean preEncryptNumbers;
    public final boolean inlineFields;
    public final boolean inlineCalledProtectedMethods;
    public final boolean inlineStaticFinals;
    public final boolean virtualizeConstructors;
    public final boolean annotationOnly;
    public final boolean privateFieldOnly;
    public final boolean ignorePublicCalls;
    public final boolean includeReferencedMethods;
    public final boolean removeAnnotations;
    public final Map<String, String> watermark;

    public final boolean includeMethodsCalledWithin;
    public final boolean excludeMethodsCalledWithin;
    public final boolean virtualizeInvocationBridges;
    public final boolean vmIntegrityCheck;
    public final double vmIntegrityCheckRatio;
    public final int vmIntegrityRecheckInterval;
    public final boolean superInstruction;
    public final int superInstructionCombineMin;
    public final int superInstructionCombineMax;
    public final SuperInstructionMode superInstructionMode;
    public final int superInstructionMaxHandlers;
    public final int superInstructionMinFrequency;
    public final boolean obfuscateInterpretBranch;
    public final int interpretBranchCases;
    public final String[] includes;
    public final String[] exclusions;
    public final MatchRules matchRules;

    public enum VMCreateMode
    {
        ONE_FOR_ALL,
        PER_METHOD,
        PER_CLASS,
        PER_PACKAGE
    }

    public enum VMLocation
    {
        SAME_PACKAGE_AS_TARGET,
        NEW_PACKAGE,
        ONE_PACKAGE
    }

    public enum RenameMode
    {
        ENABLE,
        DISABLE
    }

    public enum InterpretMode
    {
        SAVE_ALL_INSTRUCTION,
        SAVE_ONLY_REQUIRED_INSTRUCTION
    }

    public enum SuperInstructionMode
    {
        RANDOM,
        PATTERN,
        HYBRID
    }

    public static BytecodeVMConfig parse(Path file) throws IOException
    {
        String fileStr = Files.readString(file);
        ResolvedDocument resolved = withDefaultConfig(ConfigDocumentParser.parseDocument(fileStr, file));
        return parse(
                resolved.values(),
                requiredString(resolved.values(), "input"),
                requiredString(resolved.values(), "output"),
                resolved.matchRules());
    }

    public static BytecodeVMConfig parse(String config)
    {
        ResolvedDocument resolved = withDefaultConfig(ConfigDocumentParser.parseDocument(config));
        return parse(
                resolved.values(),
                requiredString(resolved.values(), "input"),
                requiredString(resolved.values(), "output"),
                resolved.matchRules());
    }

    public static BytecodeVMConfig parse(String config, String input, String output)
    {
        ResolvedDocument resolved = withDefaultConfig(ConfigDocumentParser.parseDocument(config));
        return parse(resolved.values(), input, output, resolved.matchRules());
    }

    private record ResolvedDocument(Map<String, Object> values, MatchRules matchRules)
    {
    }

    private static ResolvedDocument withDefaultConfig(ConfigDocumentParser.Document overrides)
    {
        validateConfigKeys(overrides.values());
        Map<String, Object> normalized = new LinkedHashMap<>(overrides.values());
        copyLegacyAlias(normalized, "superinstrcution", "superInstruction");
        copyLegacyAlias(normalized, "superinstrcutioncombinerange", "superInstructionCombineRange");

        ConfigDocumentParser.Document defaults =
                ConfigDocumentParser.parseDocument(BytecodeVM.defaultConfig());
        Map<String, Object> merged = new LinkedHashMap<>(defaults.values());
        for (Map.Entry<String, Object> entry : normalized.entrySet())
        {
            if (entry.getValue() != null)
            {
                merged.put(entry.getKey(), entry.getValue());
            }
        }
        List<ConfigDocumentParser.MatchBlock> blocks = overrides.matchBlocks().isEmpty()
                ? defaults.matchBlocks()
                : overrides.matchBlocks();
        return new ResolvedDocument(merged, MatchRules.parse(blocks));
    }

    private static void copyLegacyAlias(Map<String, Object> values, String alias, String canonical)
    {
        if (!values.containsKey(canonical) && values.containsKey(alias))
        {
            values.put(canonical, values.get(alias));
        }
    }

    private static BytecodeVMConfig parse(
            Map<String, Object> yaml,
            String input,
            String output,
            MatchRules matchRules)
    {
        validateConfigKeys(yaml);
        String[] includes = matchRules.includes("all");
        String[] exclusions = matchRules.exclusions("all");
        int[] superInstructionRange = optionalIntRange(
                yaml,
                "superInstructionCombineRange",
                "superinstrcutioncombinerange",
                2,
                5,
                2,
                32);
        BytecodeVMConfig parsed = BytecodeVMConfig
                .builder()
                .inputFile(Path.of(input))
                .outputFile(Path.of(output))
                .createMode(VMCreateMode.valueOf(requiredString(yaml, "createMode")))
                .location(VMLocation.valueOf(requiredString(yaml, "location")))
                .interpretMode(InterpretMode.valueOf(requiredString(yaml, "interpretMode")))
                .vmStructure(optionalVMStructure(yaml, "vmStructure", VMStructure.MEDIUM))
                .vmCount(optionalInt(yaml, "vmCount", 5, 1, 1024))
                .renameMode(RenameMode.valueOf(requiredString(yaml, "renameMode")))
                .protectCodePool(optionalBoolean(yaml, "protectCodePool", true))
                .dynamicConstantDecrypt(optionalBoolean(yaml, "dynamicConstantDecrypt", true))
                .virtualizeInstructionAddresses(optionalBoolean(yaml, "virtualizeInstructionAddresses", true))
                .encryptOperands(optionalBoolean(yaml, "encryptOperands", true))
                .perMethodOpcodeMap(optionalBoolean(yaml, "perMethodOpcodeMap", true))
                .shuffleConstants(optionalBoolean(yaml, "shuffleConstants", true))
                .bindConstantsToOperands(optionalBoolean(yaml, "bindConstantsToOperands", true))
                .splitCodeStreams(optionalBoolean(yaml, "splitCodeStreams", true))
                .shuffleInstructionBlocks(optionalBoolean(yaml, "shuffleInstructionBlocks", true))
                .obfuscateDispatch(optionalBoolean(yaml, "obfuscateDispatch", true))
                .dynamicCodePoolBuild(optionalBoolean(yaml, "dynamicCodePoolBuild", true))
                .dynamicStateKey(optionalBoolean(yaml, "dynamicStateKey", true))
                .virtualControlFlowGraph(optionalBoolean(yaml, "virtualControlFlowGraph", true))
                .constantFix(optionalBoolean(yaml, "constantFix", true))
                .preEncryptStrings(optionalBoolean(yaml, "preEncryptStrings", true))
                .preEncryptNumbers(optionalBoolean(yaml, "preEncryptNumbers", true))
                .inlineFields(optionalBoolean(yaml, "inlineFields", true))
                .inlineCalledProtectedMethods(optionalBoolean(yaml, "inlineCalledProtectedMethods", true))
                .inlineStaticFinals(optionalBoolean(yaml, "inlineStaticFinals", true))
                .virtualizeConstructors(optionalBoolean(yaml, "virtualizeConstructors", true))
                .annotationOnly(optionalBoolean(yaml, "annotationOnly", false))
                .privateFieldOnly(optionalBoolean(yaml, "privateFieldOnly", false))
                .ignorePublicCalls(optionalBoolean(yaml, "ignorePublicCalls", false))
                .includeReferencedMethods(optionalBoolean(yaml, "includeReferencedMethods", true))
                .removeAnnotations(optionalBoolean(yaml, "removeAnnotations", true))
                .watermark(optionalStringMap(yaml, "watermark"))
                .includeMethodsCalledWithin(optionalBoolean(yaml, "includeMethodsCalledWithin", false))
                .excludeMethodsCalledWithin(optionalBoolean(yaml, "excludeMethodsCalledWithin", false))
                .virtualizeInvocationBridges(optionalBoolean(yaml, "virtualizeInvocationBridges", false))
                .vmIntegrityCheck(optionalBoolean(yaml, "vmIntegrityCheck", false))
                .vmIntegrityCheckRatio(optionalDouble(yaml, "vmIntegrityCheckRatio", 1.0D, 0.0D, 1.0D))
                .vmIntegrityRecheckInterval(optionalInt(
                        yaml,
                        "vmIntegrityRecheckInterval",
                        65_536,
                        0,
                        16_777_216))
                .superInstruction(optionalBoolean(yaml, "superInstruction", false, "superinstrcution"))
                .superInstructionCombineMin(superInstructionRange[0])
                .superInstructionCombineMax(superInstructionRange[1])
                .superInstructionMode(optionalEnum(yaml, "superInstructionMode", SuperInstructionMode.HYBRID))
                .superInstructionMaxHandlers(optionalInt(yaml, "superInstructionMaxHandlers", 128, 1, 4096))
                .superInstructionMinFrequency(optionalInt(yaml, "superInstructionMinFrequency", 2, 1, 1_000_000))
                .obfuscateInterpretBranch(optionalBoolean(yaml, "obfuscateInterpretBranch", true))
                .interpretBranchCases(optionalInt(yaml, "interpretBranchCases", 3, 1, 8))
                .includes(includes)
                .exclusions(exclusions)
                .matchRules(matchRules)
                .build();
        if (parsed.includeMethodsCalledWithin && parsed.excludeMethodsCalledWithin)
        {
            throw new IllegalArgumentException(
                    "includeMethodsCalledWithin and excludeMethodsCalledWithin cannot both be true");
        }
        return parsed;
    }

    private static void validateConfigKeys(Map<String, Object> yaml)
    {
        for (String key : yaml.keySet())
        {
            if (!CONFIG_KEYS.contains(key))
            {
                throw new IllegalArgumentException("Unknown config value: " + key);
            }
        }
    }

    public BytecodeVMConfig withPaths(Path input, Path output)
    {
        return toBuilder()
                .inputFile(input == null ? inputFile : input)
                .outputFile(output == null ? outputFile : output)
                .build();
    }

    /** Returns the fully resolved configuration in stable YAML field order. */
    public Map<String, Object> toMap()
    {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("input", inputFile.toString());
        values.put("output", outputFile.toString());
        values.put("createMode", createMode.name());
        values.put("location", location.name());
        values.put("renameMode", renameMode.name());
        values.put("interpretMode", interpretMode.name());
        values.put("vmStructure", vmStructure.name());
        values.put("vmCount", vmCount);
        values.put("protectCodePool", protectCodePool);
        values.put("dynamicConstantDecrypt", dynamicConstantDecrypt);
        values.put("virtualizeInstructionAddresses", virtualizeInstructionAddresses);
        values.put("encryptOperands", encryptOperands);
        values.put("perMethodOpcodeMap", perMethodOpcodeMap);
        values.put("shuffleConstants", shuffleConstants);
        values.put("bindConstantsToOperands", bindConstantsToOperands);
        values.put("splitCodeStreams", splitCodeStreams);
        values.put("shuffleInstructionBlocks", shuffleInstructionBlocks);
        values.put("obfuscateDispatch", obfuscateDispatch);
        values.put("dynamicCodePoolBuild", dynamicCodePoolBuild);
        values.put("dynamicStateKey", dynamicStateKey);
        values.put("virtualControlFlowGraph", virtualControlFlowGraph);
        values.put("constantFix", constantFix);
        values.put("preEncryptStrings", preEncryptStrings);
        values.put("preEncryptNumbers", preEncryptNumbers);
        values.put("inlineFields", inlineFields);
        values.put("inlineCalledProtectedMethods", inlineCalledProtectedMethods);
        values.put("inlineStaticFinals", inlineStaticFinals);
        values.put("virtualizeConstructors", virtualizeConstructors);
        values.put("annotationOnly", annotationOnly);
        values.put("privateFieldOnly", privateFieldOnly);
        values.put("ignorePublicCalls", ignorePublicCalls);
        values.put("includeReferencedMethods", includeReferencedMethods);
        values.put("removeAnnotations", removeAnnotations);
        values.put("watermark", new LinkedHashMap<>(watermark));
        values.put("includeMethodsCalledWithin", includeMethodsCalledWithin);
        values.put("excludeMethodsCalledWithin", excludeMethodsCalledWithin);
        values.put("virtualizeInvocationBridges", virtualizeInvocationBridges);
        values.put("vmIntegrityCheck", vmIntegrityCheck);
        values.put("vmIntegrityCheckRatio", vmIntegrityCheckRatio);
        values.put("vmIntegrityRecheckInterval", vmIntegrityRecheckInterval);
        values.put("superInstruction", superInstruction);
        values.put("superInstructionCombineRange", List.of(
                superInstructionCombineMin,
                superInstructionCombineMax));
        values.put("superInstructionMode", superInstructionMode.name());
        values.put("superInstructionMaxHandlers", superInstructionMaxHandlers);
        values.put("superInstructionMinFrequency", superInstructionMinFrequency);
        values.put("obfuscateInterpretBranch", obfuscateInterpretBranch);
        values.put("interpretBranchCases", interpretBranchCases);
        values.put("includes", ruleDocument(matchRules.includes));
        values.put("excludes", ruleDocument(matchRules.exclusions));
        return Collections.unmodifiableMap(values);
    }

    public String toYaml()
    {
        return DocumentedConfigRenderer.render(this);
    }

    private static Map<String, List<String>> ruleDocument(Map<String, String[]> groups)
    {
        Map<String, List<String>> result = new LinkedHashMap<>();
        groups.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.put(
                        entry.getKey(),
                        List.copyOf(Arrays.asList(entry.getValue()))));
        result.putIfAbsent("all", List.of());
        return Collections.unmodifiableMap(result);
    }

    public BytecodeVMConfig forMethod(ClassNode owner, MethodNode method)
    {
        BytecodeVMConfig yamlConfig = BytecodeVMConfig
                .builder()
                .inputFile(inputFile)
                .outputFile(outputFile)
                .createMode(createMode)
                .location(location)
                .interpretMode(interpretMode)
                .vmStructure(vmStructure)
                .renameMode(renameMode)
                .protectCodePool(statementEnabled("protectCodePool", protectCodePool, owner, method))
                .dynamicConstantDecrypt(statementEnabled("dynamicConstantDecrypt", dynamicConstantDecrypt, owner, method))
                .virtualizeInstructionAddresses(statementEnabled("virtualizeInstructionAddresses", virtualizeInstructionAddresses, owner, method))
                .encryptOperands(statementEnabled("encryptOperands", encryptOperands, owner, method))
                .perMethodOpcodeMap(statementEnabled("perMethodOpcodeMap", perMethodOpcodeMap, owner, method))
                .shuffleConstants(statementEnabled("shuffleConstants", shuffleConstants, owner, method))
                .bindConstantsToOperands(statementEnabled("bindConstantsToOperands", bindConstantsToOperands, owner, method))
                .splitCodeStreams(statementEnabled("splitCodeStreams", splitCodeStreams, owner, method))
                .shuffleInstructionBlocks(statementEnabled("shuffleInstructionBlocks", shuffleInstructionBlocks, owner, method))
                .obfuscateDispatch(statementEnabled("obfuscateDispatch", obfuscateDispatch, owner, method))
                .dynamicCodePoolBuild(statementEnabled("dynamicCodePoolBuild", dynamicCodePoolBuild, owner, method))
                .dynamicStateKey(statementEnabled("dynamicStateKey", dynamicStateKey, owner, method))
                .virtualControlFlowGraph(statementEnabled("virtualControlFlowGraph", virtualControlFlowGraph, owner, method))
                .obfuscateInterpretBranch(statementEnabled(
                        "obfuscateInterpretBranch",
                        obfuscateInterpretBranch,
                        owner,
                        method))
                .interpretBranchCases(interpretBranchCases)
                .constantFix(constantFix)
                .preEncryptStrings(statementEnabled("preEncryptStrings", preEncryptStrings, owner, method))
                .preEncryptNumbers(statementEnabled("preEncryptNumbers", preEncryptNumbers, owner, method))
                .inlineFields(inlineFields)
                .inlineCalledProtectedMethods(inlineCalledProtectedMethods)
                .inlineStaticFinals(inlineStaticFinals)
                .virtualizeConstructors(statementEnabled(
                        "virtualizeConstructors",
                        virtualizeConstructors,
                        owner,
                        method))
                .annotationOnly(annotationOnly)
                .privateFieldOnly(privateFieldOnly)
                .ignorePublicCalls(ignorePublicCalls)
                .includeReferencedMethods(includeReferencedMethods)
                .removeAnnotations(removeAnnotations)
                .watermark(watermark)
                .includeMethodsCalledWithin(includeMethodsCalledWithin)
                .excludeMethodsCalledWithin(excludeMethodsCalledWithin)
                .virtualizeInvocationBridges(virtualizeInvocationBridges)
                .vmIntegrityCheck(vmIntegrityCheck)
                .vmIntegrityCheckRatio(vmIntegrityCheckRatio)
                .vmIntegrityRecheckInterval(vmIntegrityRecheckInterval)
                .superInstruction(statementEnabled("superInstruction", superInstruction, owner, method))
                .superInstructionCombineMin(superInstructionCombineMin)
                .superInstructionCombineMax(superInstructionCombineMax)
                .superInstructionMode(superInstructionMode)
                .superInstructionMaxHandlers(superInstructionMaxHandlers)
                .superInstructionMinFrequency(superInstructionMinFrequency)
                .vmCount(vmCount)
                .includes(includes)
                .exclusions(exclusions)
                .matchRules(matchRules)
                .build();
        return SdkAnnotationReader.applyMethodOverrides(yamlConfig, owner, method);
    }

    public BytecodeVMConfig integrityConfig()
    {
        return BytecodeVMConfig
                .builder()
                .inputFile(inputFile)
                .outputFile(outputFile)
                .createMode(VMCreateMode.PER_METHOD)
                .location(location)
                .interpretMode(InterpretMode.SAVE_ALL_INSTRUCTION)
                .vmStructure(VMStructure.HIGH)
                .renameMode(renameMode)
                .protectCodePool(true)
                .dynamicConstantDecrypt(true)
                .virtualizeInstructionAddresses(true)
                .encryptOperands(true)
                .perMethodOpcodeMap(true)
                .shuffleConstants(true)
                .bindConstantsToOperands(true)
                .splitCodeStreams(true)
                .shuffleInstructionBlocks(true)
                .obfuscateDispatch(true)
                .dynamicCodePoolBuild(true)
                .dynamicStateKey(true)
                .virtualControlFlowGraph(true)
                .obfuscateInterpretBranch(obfuscateInterpretBranch)
                .interpretBranchCases(Math.min(interpretBranchCases, 3))
                .constantFix(false)
                .preEncryptStrings(false)
                .preEncryptNumbers(false)
                .inlineFields(false)
                .inlineCalledProtectedMethods(false)
                .inlineStaticFinals(false)
                .virtualizeConstructors(false)
                .annotationOnly(true)
                .privateFieldOnly(true)
                .ignorePublicCalls(true)
                .includeReferencedMethods(false)
                .removeAnnotations(removeAnnotations)
                .watermark(watermark)
                .includeMethodsCalledWithin(false)
                .excludeMethodsCalledWithin(false)
                .virtualizeInvocationBridges(false)
                .vmIntegrityCheck(false)
                .vmIntegrityCheckRatio(0.0D)
                .vmIntegrityRecheckInterval(0)
                .superInstruction(superInstruction)
                .superInstructionCombineMin(superInstructionCombineMin)
                .superInstructionCombineMax(superInstructionCombineMax)
                .superInstructionMode(superInstructionMode)
                .superInstructionMaxHandlers(superInstructionMaxHandlers)
                .superInstructionMinFrequency(superInstructionMinFrequency)
                .vmCount(1)
                .includes(new String[]{"*"})
                .exclusions(new String[0])
                .matchRules(MatchRules.empty())
                .build();
    }

    public BytecodeVMConfig resolveVMStructure()
    {
        return vmStructure.isAutomatic()
                ? toBuilder().vmStructure(vmStructure.resolveAuto()).build()
                : this;
    }

    private boolean statementEnabled(String key, boolean baseValue, ClassNode owner, MethodNode method)
    {
        return baseValue && matchRules.statementMatches(key, owner, method);
    }

    private static boolean optionalBoolean(Map<String, Object> yaml, String key, boolean defaultValue)
    {
        return optionalBoolean(yaml, key, defaultValue, new String[0]);
    }

    private static boolean optionalBoolean(
            Map<String, Object> yaml,
            String key,
            boolean defaultValue,
            String... aliases)
    {
        Object value = yaml.get(key);
        if (value == null)
        {
            for (String alias : aliases)
            {
                value = yaml.get(alias);
                if (value != null)
                {
                    break;
                }
            }
        }
        if (value == null)
        {
            return defaultValue;
        }
        if (!(value instanceof Boolean result))
        {
            throw typeError(key, "a boolean");
        }
        return result;
    }

    private static int optionalInt(
            Map<String, Object> yaml,
            String key,
            int defaultValue,
            int minValue,
            int maxValue)
    {
        Object value = yaml.get(key);
        if (value == null)
        {
            return defaultValue;
        }
        int result = integer(value, key);
        if(result < minValue || result > maxValue)
        {
            throw new IllegalArgumentException(
                    "Config value " + key + " must be between " + minValue + " and " + maxValue);
        }
        return result;
    }

    private static double optionalDouble(
            Map<String, Object> yaml,
            String key,
            double defaultValue,
            double minValue,
            double maxValue)
    {
        Object value = yaml.get(key);
        if (value == null)
        {
            return defaultValue;
        }
        if (!(value instanceof Number number))
        {
            throw typeError(key, "a number");
        }
        double result = number.doubleValue();
        if(result < minValue || result > maxValue)
        {
            throw new IllegalArgumentException(
                    "Config value " + key + " must be between " + minValue + " and " + maxValue);
        }
        return result;
    }

    private static Map<String, String> optionalStringMap(
            Map<String, Object> yaml,
            String key)
    {
        Object value = yaml.get(key);
        if (value == null)
        {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> source))
        {
            throw typeError(key, "a key/value map");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet())
        {
            if (!(entry.getKey() instanceof String name) || name.isBlank())
            {
                throw new IllegalArgumentException("Config value " + key + " contains an empty or non-string key");
            }
            Object fieldValue = entry.getValue();
            if (!(fieldValue instanceof String || fieldValue instanceof Number || fieldValue instanceof Boolean))
            {
                throw new IllegalArgumentException(
                        "Config value " + key + "." + name + " must be a string, number, or boolean");
            }
            result.put(name, String.valueOf(fieldValue));
        }
        return Collections.unmodifiableMap(result);
    }

    private static <T extends Enum<T>> T optionalEnum(
            Map<String, Object> yaml,
            String key,
            T defaultValue)
    {
        Object value = yaml.get(key);
        if (value == null)
        {
            return defaultValue;
        }
        if (!(value instanceof String text))
        {
            throw typeError(key, "a string");
        }
        return Enum.valueOf(defaultValue.getDeclaringClass(), text);
    }

    private static VMStructure optionalVMStructure(
            Map<String, Object> yaml,
            String key,
            VMStructure defaultValue)
    {
        Object value = yaml.get(key);
        if (value == null)
        {
            return defaultValue;
        }
        if (!(value instanceof String text))
        {
            throw typeError(key, "a string");
        }
        return VMStructure.parse(text);
    }

    private static int[] optionalIntRange(
            Map<String, Object> yaml,
            String key,
            String alias,
            int defaultMin,
            int defaultMax,
            int minValue,
            int maxValue)
    {
        Object value = yaml.get(key);
        if (value == null)
        {
            value = yaml.get(alias);
        }
        if (value == null)
        {
            return new int[]{defaultMin, defaultMax};
        }
        if (!(value instanceof List<?> range) || range.size() != 2)
        {
            throw new IllegalArgumentException("Config value must be a two-item array: " + key);
        }
        int min = integer(range.get(0), key + "[0]");
        int max = integer(range.get(1), key + "[1]");
        if(min < minValue || max > maxValue || min > max)
        {
            throw new IllegalArgumentException(
                    "Config value " + key + " must be between " + minValue + " and " + maxValue + " with min <= max");
        }
        return new int[]{min, max};
    }

    private static String requiredString(Map<String, Object> yaml, String key)
    {
        Object value = yaml.get(key);
        if (value == null)
        {
            throw new IllegalArgumentException("Missing required config value: " + key);
        }
        if (!(value instanceof String result))
        {
            throw typeError(key, "a string");
        }
        return result;
    }

    private static int integer(Object value, String key)
    {
        if (!(value instanceof Number number))
        {
            throw typeError(key, "an integer");
        }
        long result = number.longValue();
        if (number.doubleValue() != result || result < Integer.MIN_VALUE || result > Integer.MAX_VALUE)
        {
            throw typeError(key, "a 32-bit integer");
        }
        return (int) result;
    }

    private static IllegalArgumentException typeError(String key, String expected)
    {
        return new IllegalArgumentException("Config value " + key + " must be " + expected);
    }

    public static final class MatchRules
    {
        public record RuleBlock(boolean included, Map<String, List<String>> groups)
        {
            public RuleBlock
            {
                groups = Collections.unmodifiableMap(new LinkedHashMap<>(groups));
            }
        }

        private final List<RuleBlock> blocks;
        private final Map<String, String[]> includes;
        private final Map<String, String[]> exclusions;
        private final Map<String, TargetMatcher> matchers;
        private final Map<String, Boolean> defaults;

        private MatchRules(List<RuleBlock> blocks)
        {
            this.blocks = List.copyOf(blocks);
            Map<String, List<String>> includedRules = new LinkedHashMap<>();
            Map<String, List<String>> excludedRules = new LinkedHashMap<>();
            Map<String, TargetMatcher> compiled = new HashMap<>();
            Map<String, Boolean> initialStates = new HashMap<>();
            for (RuleBlock block : blocks)
            {
                for (Map.Entry<String, List<String>> entry : block.groups().entrySet())
                {
                    String group = entry.getKey();
                    if (!MATCH_GROUP_KEYS.contains(group))
                    {
                        throw new IllegalArgumentException("Unknown match group: " + group);
                    }
                    initialStates.putIfAbsent(group, !block.included());
                    TargetMatcher matcher = compiled.computeIfAbsent(group, ignored -> new TargetMatcher());
                    for (String rule : entry.getValue())
                    {
                        matcher.add(rule, block.included());
                    }
                    (block.included() ? includedRules : excludedRules)
                            .computeIfAbsent(group, ignored -> new ArrayList<>())
                            .addAll(entry.getValue());
                }
            }
            this.includes = arrays(includedRules);
            this.exclusions = arrays(excludedRules);
            this.matchers = Map.copyOf(compiled);
            this.defaults = Map.copyOf(initialStates);
        }

        private static MatchRules parse(List<ConfigDocumentParser.MatchBlock> parsed)
        {
            List<RuleBlock> blocks = parsed.stream()
                    .map(block -> new RuleBlock(block.included(), block.groups()))
                    .toList();
            return new MatchRules(blocks);
        }

        private static MatchRules empty()
        {
            return new MatchRules(List.of());
        }

        public static MatchRules of(String[] includes, String[] exclusions)
        {
            List<RuleBlock> blocks = new ArrayList<>();
            blocks.add(new RuleBlock(true, Map.of(
                    "all",
                    List.copyOf(Arrays.asList(includes == null ? new String[0] : includes.clone())))));
            if (exclusions != null && exclusions.length != 0)
            {
                blocks.add(new RuleBlock(false, Map.of(
                        "all",
                        List.copyOf(Arrays.asList(exclusions.clone())))));
            }
            return new MatchRules(blocks);
        }

        public List<RuleBlock> blocks()
        {
            return blocks;
        }

        public String[] includes(String key)
        {
            return includes.getOrDefault(key, new String[0]);
        }

        public String[] exclusions(String key)
        {
            return exclusions.getOrDefault(key, new String[0]);
        }

        public boolean statementMatches(String key, ClassNode owner, MethodNode method)
        {
            TargetMatcher matcher = matchers.get(key);
            return matcher == null || matcher.methodDecision(owner, method)
                    .orElse(defaults.getOrDefault(key, true));
        }

        public boolean statementMatches(String key, ClassNode owner, org.objectweb.asm.tree.FieldNode field)
        {
            TargetMatcher matcher = matchers.get(key);
            return matcher == null || matcher.fieldDecision(owner, field)
                    .orElse(defaults.getOrDefault(key, true));
        }

        public boolean statementMatches(String key, ClassNode owner)
        {
            TargetMatcher matcher = matchers.get(key);
            return matcher == null || matcher.classDecision(owner)
                    .orElse(defaults.getOrDefault(key, true));
        }

        public boolean fieldExcluded(String key, ClassNode owner, org.objectweb.asm.tree.FieldNode field)
        {
            TargetMatcher matcher = matchers.get(key);
            if (matcher == null)
            {
                return false;
            }
            TargetMatcher.MatchResult decision = matcher.fieldDecision(owner, field);
            return decision.matched() && !decision.included();
        }

        public boolean classExcluded(String key, ClassNode owner)
        {
            TargetMatcher matcher = matchers.get(key);
            if (matcher == null)
            {
                return false;
            }
            TargetMatcher.MatchResult decision = matcher.classDecision(owner);
            return decision.matched() && !decision.included();
        }

        public boolean methodExcluded(String key, ClassNode owner, MethodNode method)
        {
            TargetMatcher matcher = matchers.get(key);
            if (matcher == null)
            {
                return false;
            }
            TargetMatcher.MatchResult decision = matcher.methodDecision(owner, method);
            return decision.matched() && !decision.included();
        }

        private static Map<String, String[]> arrays(Map<String, List<String>> source)
        {
            Map<String, String[]> result = new HashMap<>();
            for (Map.Entry<String, List<String>> entry : source.entrySet())
            {
                result.put(entry.getKey(), entry.getValue().toArray(String[]::new));
            }
            return Map.copyOf(result);
        }
    }
}
