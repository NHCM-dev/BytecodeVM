package nhcm.bytecodevm.config;

import nhcm.bytecodevm.BytecodeVM;
import nhcm.bytecodevm.enums.VMStructure;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Creates complete, independent configurations for common protection levels. */
public final class PresetConfigGallery
{
    private PresetConfigGallery()
    {
    }

    public static final Preset DISABLED = Preset.DISABLED;
    public static final Preset SIMPLE = Preset.SIMPLE;
    public static final Preset CODE_POOL_ONLY = Preset.CODE_POOL_ONLY;
    public static final Preset FAST = Preset.FAST;
    public static final Preset LIGHT = Preset.LIGHT;
    public static final Preset BALANCED = Preset.BALANCED;
    public static final Preset INTEGRITY_FOCUSED = Preset.INTEGRITY_FOCUSED;
    public static final Preset STRONG = Preset.STRONG;
    public static final Preset EXTREME = Preset.EXTREME;
    public static final Preset RANDOMIZED = Preset.RANDOMIZED;

    public enum Preset
    {
        DISABLED("Disables every optional transform and protection layer."),
        SIMPLE("Uses one SIMPLE_DISPATCH VM with the normal encoding protections."),
        CODE_POOL_ONLY("Focuses on CodePool, operand, opcode, and constant protection."),
        FAST("Lowest runtime overhead for frequently executed code."),
        LIGHT("Moderate protection with low-to-medium VM structures."),
        BALANCED("General-purpose protection and the recommended default preset."),
        INTEGRITY_FOCUSED("Prioritizes VM integrity checks without maximum code expansion."),
        STRONG("High analysis resistance with integrity sampling."),
        EXTREME("Maximum available protection; expect substantial runtime and output-size cost."),
        RANDOMIZED("Selects from every concrete VM structure with balanced protection layers.");

        private final String description;

        Preset(String description)
        {
            this.description = description;
        }

        public String description()
        {
            return description;
        }
    }

    public static List<Preset> presets()
    {
        return List.of(Preset.values());
    }

    public static String names()
    {
        return Arrays.stream(Preset.values())
                .map(Preset::name)
                .collect(Collectors.joining(", "));
    }

    public static Preset parse(String name)
    {
        if (name == null || name.isBlank())
        {
            throw new IllegalArgumentException("Preset name is required");
        }
        try
        {
            return Preset.valueOf(name.trim().replace('-', '_').toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException exception)
        {
            throw new IllegalArgumentException(
                    "Unknown preset '" + name + "'. Available presets: " + names(),
                    exception);
        }
    }

    public static BytecodeVMConfig create(String preset, Path input, Path output)
    {
        Preset namedPreset = find(preset);
        if (namedPreset != null)
        {
            return create(namedPreset, input, output);
        }

        VMStructure structure;
        try
        {
            structure = VMStructure.parse(preset);
        }
        catch (RuntimeException exception)
        {
            throw unknownPreset(preset, exception);
        }
        if (structure.isAutomatic())
        {
            throw unknownPreset(preset, null);
        }
        return createForStructure(structure, input, output);
    }

    public static BytecodeVMConfig create(Preset preset, Path input, Path output)
    {
        return builder(preset, input, output).build();
    }

    public static BytecodeVMConfig createForStructure(VMStructure structure, Path input, Path output)
    {
        if (structure == null || structure.isAutomatic())
        {
            throw new IllegalArgumentException("A concrete VM structure is required");
        }
        return balanced(base(input, output))
                .vmStructure(structure)
                .vmCount(1)
                .build();
    }

    public static BytecodeVMConfig.BytecodeVMConfigBuilder builder(
            Preset preset,
            Path input,
            Path output)
    {
        if (preset == null)
        {
            throw new IllegalArgumentException("Preset is required");
        }
        if (input == null || output == null)
        {
            throw new IllegalArgumentException("Preset input and output paths are required");
        }

        BytecodeVMConfig.BytecodeVMConfigBuilder builder = base(input, output);
        return switch (preset)
        {
            case DISABLED -> disabled(builder);
            case SIMPLE -> simple(builder);
            case CODE_POOL_ONLY -> codePoolOnly(builder);
            case FAST -> fast(builder);
            case LIGHT -> light(builder);
            case BALANCED -> balanced(builder);
            case INTEGRITY_FOCUSED -> integrityFocused(builder);
            case STRONG -> strong(builder);
            case EXTREME -> extreme(builder);
            case RANDOMIZED -> randomized(builder);
        };
    }

    private static Preset find(String name)
    {
        if (name == null || name.isBlank())
        {
            throw new IllegalArgumentException("Preset name is required");
        }
        String normalized = name.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        for (Preset preset : Preset.values())
        {
            if (preset.name().equals(normalized))
            {
                return preset;
            }
        }
        return null;
    }

    private static IllegalArgumentException unknownPreset(String name, Throwable cause)
    {
        return new IllegalArgumentException(
                "Unknown preset '" + name + "'. Available presets: " + names() +
                ". Any concrete VMStructure name is also accepted.",
                cause);
    }

    private static BytecodeVMConfig.BytecodeVMConfigBuilder base(Path input, Path output)
    {
        if (input == null || output == null)
        {
            throw new IllegalArgumentException("Preset input and output paths are required");
        }
        return BytecodeVMConfig.parse(
                        BytecodeVM.defaultConfig(),
                        input.toString(),
                        output.toString())
                .toBuilder()
                .vmStructure(VMStructure.MEDIUM)
                .vmCount(7)
                .constantFix(false)
                .vmIntegrityCheck(false)
                .vmIntegrityCheckRatio(0.0D)
                .vmIntegrityRecheckInterval(0)
                .superInstructionCombineMax(4)
                .superInstructionMaxHandlers(96);
    }

    private static BytecodeVMConfig.BytecodeVMConfigBuilder disabled(
            BytecodeVMConfig.BytecodeVMConfigBuilder builder)
    {
        return builder
                .vmStructure(VMStructure.SIMPLE_DISPATCH)
                .vmCount(1)
                .protectCodePool(false)
                .dynamicConstantDecrypt(false)
                .virtualizeInstructionAddresses(false)
                .encryptOperands(false)
                .perMethodOpcodeMap(false)
                .shuffleConstants(false)
                .bindConstantsToOperands(false)
                .splitCodeStreams(false)
                .shuffleInstructionBlocks(false)
                .obfuscateDispatch(false)
                .dynamicCodePoolBuild(false)
                .dynamicStateKey(false)
                .virtualControlFlowGraph(false)
                .constantFix(false)
                .preEncryptStrings(false)
                .preEncryptNumbers(false)
                .inlineFields(false)
                .inlineCalledProtectedMethods(false)
                .inlineStaticFinals(false)
                .annotationOnly(false)
                .privateFieldOnly(false)
                .ignorePublicCalls(false)
                .includeReferencedMethods(false)
                .removeAnnotations(false)
                .includeMethodsCalledWithin(false)
                .excludeMethodsCalledWithin(false)
                .virtualizeInvocationBridges(false)
                .vmIntegrityCheck(false)
                .vmIntegrityCheckRatio(0.0D)
                .vmIntegrityRecheckInterval(0)
                .superInstruction(false)
                .superInstructionMaxHandlers(1)
                .obfuscateInterpretBranch(false)
                .interpretBranchCases(1);
    }

    private static BytecodeVMConfig.BytecodeVMConfigBuilder simple(
            BytecodeVMConfig.BytecodeVMConfigBuilder builder)
    {
        return balanced(builder)
                .vmStructure(VMStructure.SIMPLE_DISPATCH)
                .vmCount(1);
    }

    private static BytecodeVMConfig.BytecodeVMConfigBuilder codePoolOnly(
            BytecodeVMConfig.BytecodeVMConfigBuilder builder)
    {
        return builder
                .vmStructure(VMStructure.SIMPLE_DISPATCH)
                .vmCount(1)
                .shuffleInstructionBlocks(false)
                .obfuscateDispatch(false)
                .virtualControlFlowGraph(false)
                .virtualizeInvocationBridges(false)
                .superInstruction(false)
                .superInstructionMaxHandlers(1)
                .obfuscateInterpretBranch(false)
                .interpretBranchCases(1);
    }

    private static BytecodeVMConfig.BytecodeVMConfigBuilder fast(
            BytecodeVMConfig.BytecodeVMConfigBuilder builder)
    {
        return builder
                .vmStructure(VMStructure.LOW)
                .vmCount(5)
                .virtualizeInstructionAddresses(false)
                .bindConstantsToOperands(false)
                .splitCodeStreams(false)
                .shuffleInstructionBlocks(false)
                .obfuscateDispatch(false)
                .dynamicCodePoolBuild(false)
                .dynamicStateKey(false)
                .virtualControlFlowGraph(false)
                .virtualizeInvocationBridges(false)
                .superInstruction(false)
                .superInstructionMaxHandlers(32)
                .obfuscateInterpretBranch(false)
                .interpretBranchCases(1);
    }

    private static BytecodeVMConfig.BytecodeVMConfigBuilder light(
            BytecodeVMConfig.BytecodeVMConfigBuilder builder)
    {
        return builder
                .vmStructure(VMStructure.MEDIUM_LOW)
                .vmCount(12)
                .dynamicStateKey(false)
                .superInstructionMaxHandlers(64)
                .obfuscateInterpretBranch(false)
                .interpretBranchCases(1);
    }

    private static BytecodeVMConfig.BytecodeVMConfigBuilder balanced(
            BytecodeVMConfig.BytecodeVMConfigBuilder builder)
    {
        return builder
                .renameMode(BytecodeVMConfig.RenameMode.ENABLE)
                .vmStructure(VMStructure.MEDIUM)
                .vmCount(7);
    }

    private static BytecodeVMConfig.BytecodeVMConfigBuilder strong(
            BytecodeVMConfig.BytecodeVMConfigBuilder builder)
    {
        return builder
                .createMode(BytecodeVMConfig.VMCreateMode.PER_PACKAGE)
                .location(BytecodeVMConfig.VMLocation.NEW_PACKAGE)
                .renameMode(BytecodeVMConfig.RenameMode.ENABLE)
                .vmStructure(VMStructure.MEDIUM_HIGH)
                .vmCount(12)
                .constantFix(true)
                .vmIntegrityCheck(true)
                .vmIntegrityCheckRatio(0.5D)
                .vmIntegrityRecheckInterval(131_072)
                .superInstructionCombineMax(5)
                .superInstructionMaxHandlers(128)
                .interpretBranchCases(4);
    }

    private static BytecodeVMConfig.BytecodeVMConfigBuilder integrityFocused(
            BytecodeVMConfig.BytecodeVMConfigBuilder builder)
    {
        return balanced(builder)
                .vmStructure(VMStructure.MEDIUM_HIGH)
                .vmCount(12)
                .vmIntegrityCheck(true)
                .vmIntegrityCheckRatio(1.0D)
                .vmIntegrityRecheckInterval(65_536)
                .superInstructionMaxHandlers(64)
                .interpretBranchCases(3);
    }

    private static BytecodeVMConfig.BytecodeVMConfigBuilder extreme(
            BytecodeVMConfig.BytecodeVMConfigBuilder builder)
    {
        return builder
                .createMode(BytecodeVMConfig.VMCreateMode.PER_CLASS)
                .location(BytecodeVMConfig.VMLocation.NEW_PACKAGE)
                .renameMode(BytecodeVMConfig.RenameMode.ENABLE)
                .interpretMode(BytecodeVMConfig.InterpretMode.SAVE_ALL_INSTRUCTION)
                .vmStructure(VMStructure.HIGH)
                .vmCount(5)
                .constantFix(true)
                .vmIntegrityCheck(true)
                .vmIntegrityCheckRatio(1.0D)
                .vmIntegrityRecheckInterval(32_768)
                .superInstructionCombineMax(6)
                .superInstructionMaxHandlers(192)
                .interpretBranchCases(5);
    }

    private static BytecodeVMConfig.BytecodeVMConfigBuilder randomized(
            BytecodeVMConfig.BytecodeVMConfigBuilder builder)
    {
        return balanced(builder)
                .vmStructure(VMStructure.ANY)
                .vmCount(12);
    }
}
