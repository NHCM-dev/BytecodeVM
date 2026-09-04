package nhcm.bytecodevm.config.sdk;

import nhcm.bytecodevm.config.BytecodeVMConfig;
import nhcm.bytecodevm.enums.VMStructure;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static nhcm.bytecodevm.config.sdk.SdkAnnotationOptions.SdkCallPolicy;

/** Reads BytecodeVM SDK annotations without introducing a runtime SDK dependency. */
public final class SdkAnnotationReader
{
    public static final String SDK_PREFIX = "Lnhcm/bytecodevm/sdk/";
    public static final String VIRTUALIZE = SDK_PREFIX + "annotation/Virtualize;";
    public static final String PROTECT_CLASS = SDK_PREFIX + "annotation/ProtectClass;";
    public static final String DO_NOT_VIRTUALIZE = SDK_PREFIX + "annotation/DoNotVirtualize;";
    public static final String INLINE_FIELD = SDK_PREFIX + "annotation/InlineField;";
    public static final String INLINE_FINAL = SDK_PREFIX + "annotation/InlineFinal;";

    private SdkAnnotationReader()
    {
    }

    public static ClassDirectives classDirectives(ClassNode owner)
    {
        AnnotationNode virtualize = find(owner.visibleAnnotations, owner.invisibleAnnotations, VIRTUALIZE);
        AnnotationNode protectClass = find(owner.visibleAnnotations, owner.invisibleAnnotations, PROTECT_CLASS);
        boolean excluded = find(
                owner.visibleAnnotations,
                owner.invisibleAnnotations,
                DO_NOT_VIRTUALIZE) != null;
        SdkAnnotationOptions options = parseVirtualize(virtualize, owner.name);
        Boolean protectConstantFix = protectClass == null
                ? null
                : toggle(values(protectClass).get("constantFix"), null, owner.name + " @ProtectClass.constantFix");
        return new ClassDirectives(protectClass != null, excluded, options, protectConstantFix);
    }

    public static MethodDirectives methodDirectives(ClassNode owner, MethodNode method)
    {
        ClassDirectives classDirectives = classDirectives(owner);
        AnnotationNode annotation = find(
                method.visibleAnnotations,
                method.invisibleAnnotations,
                VIRTUALIZE);
        boolean methodExcluded = find(
                method.visibleAnnotations,
                method.invisibleAnnotations,
                DO_NOT_VIRTUALIZE) != null;
        SdkAnnotationOptions methodOptions = parseVirtualize(
                annotation,
                owner.name + '.' + method.name + method.desc);
        SdkAnnotationOptions effective = classDirectives.options.overlay(methodOptions);
        boolean excluded = classDirectives.excluded || methodExcluded || Boolean.FALSE.equals(effective.enabled());
        boolean selected = !excluded && Boolean.TRUE.equals(effective.enabled());
        return new MethodDirectives(
                annotation != null,
                selected,
                excluded,
                effective);
    }

    public static FieldDirectives fieldDirectives(ClassNode owner, org.objectweb.asm.tree.FieldNode field)
    {
        String target = owner.name + '.' + field.name + field.desc;
        AnnotationNode inlineField = find(
                field.visibleAnnotations,
                field.invisibleAnnotations,
                INLINE_FIELD);
        AnnotationNode inlineFinal = find(
                field.visibleAnnotations,
                field.invisibleAnnotations,
                INLINE_FINAL);
        return new FieldDirectives(
                inlineField != null,
                toggle(values(inlineField).get("enabled"), inlineField == null ? null : true,
                        target + " @InlineField.enabled"),
                inlineFinal != null,
                toggle(values(inlineFinal).get("enabled"), inlineFinal == null ? null : true,
                        target + " @InlineFinal.enabled"));
    }

    public static BytecodeVMConfig applyMethodOverrides(
            BytecodeVMConfig yamlConfig,
            ClassNode owner,
            MethodNode method)
    {
        SdkAnnotationOptions options = methodDirectives(owner, method).options;
        BytecodeVMConfig.BytecodeVMConfigBuilder builder = yamlConfig.toBuilder();
        if (options.preEncryptStrings() != null)
        {
            builder.preEncryptStrings(options.preEncryptStrings());
        }
        if (options.preEncryptNumbers() != null)
        {
            builder.preEncryptNumbers(options.preEncryptNumbers());
        }
        if (options.vmStructure() != null)
        {
            builder.vmStructure(options.vmStructure());
        }
        applyEncrypt(builder, options.encrypt());
        applyShuffle(builder, options.shuffle());
        applyObfuscate(builder, options.obfuscate());
        if (options.integrityCheck() != null)
        {
            builder.vmIntegrityCheck(options.integrityCheck());
        }
        if (options.callPolicy() != null)
        {
            builder.includeMethodsCalledWithin(options.callPolicy() == SdkCallPolicy.INCLUDE);
            builder.excludeMethodsCalledWithin(options.callPolicy() == SdkCallPolicy.EXCLUDE);
        }
        if (options.superInstruction() != null)
        {
            builder.superInstruction(options.superInstruction());
        }
        if (options.superInstructionMode() != null)
        {
            builder.superInstructionMode(options.superInstructionMode());
        }
        if (options.superInstructionCombineMin() != null)
        {
            builder.superInstructionCombineMin(options.superInstructionCombineMin());
        }
        if (options.superInstructionCombineMax() != null)
        {
            builder.superInstructionCombineMax(options.superInstructionCombineMax());
        }
        if (options.superInstructionMinFrequency() != null)
        {
            builder.superInstructionMinFrequency(options.superInstructionMinFrequency());
        }
        BytecodeVMConfig result = builder.build();
        validate(result, owner.name + '.' + method.name + method.desc);
        return result;
    }

    private static SdkAnnotationOptions parseVirtualize(AnnotationNode annotation, String target)
    {
        if (annotation == null)
        {
            return SdkAnnotationOptions.empty();
        }
        Map<String, Object> root = values(annotation);
        AnnotationNode vm = nested(root.get("vm"), target + " @Virtualize.vm");
        AnnotationNode superInstructions = nested(
                root.get("superInstructions"),
                target + " @Virtualize.superInstructions");
        Map<String, Object> vmValues = values(vm);
        Map<String, Object> superValues = values(superInstructions);

        return new SdkAnnotationOptions(
                true,
                toggle(root.get("enabled"), true, target + " @Virtualize.enabled"),
                toggle(root.get("preEncryptStrings"), true, target + " @Virtualize.preEncryptStrings"),
                toggle(root.get("preEncryptNumbers"), true, target + " @Virtualize.preEncryptNumbers"),
                vmStructure(vmValues.get("structure"), target),
                toggle(vmValues.get("encrypt"), null, target + " @VMOptions.encrypt"),
                toggle(vmValues.get("shuffle"), null, target + " @VMOptions.shuffle"),
                toggle(vmValues.get("obfuscate"), null, target + " @VMOptions.obfuscate"),
                toggle(root.get("integrityCheck"), null, target + " @Virtualize.integrityCheck"),
                callPolicy(root.get("calls"), target),
                toggle(superValues.get("enabled"), null, target),
                superInstructionMode(superValues.get("mode"), target),
                integer(superValues.get("combineMin"), target),
                integer(superValues.get("combineMax"), target),
                integer(superValues.get("minFrequency"), target));
    }

    private static void applyEncrypt(
            BytecodeVMConfig.BytecodeVMConfigBuilder builder,
            Boolean enabled)
    {
        if (enabled == null)
        {
            return;
        }
        if (enabled)
        {
            builder.protectCodePool(true);
        }
        builder.virtualizeInstructionAddresses(enabled)
                .dynamicConstantDecrypt(enabled)
                .encryptOperands(enabled)
                .perMethodOpcodeMap(enabled)
                .bindConstantsToOperands(enabled)
                .dynamicStateKey(enabled);
    }

    private static void applyShuffle(
            BytecodeVMConfig.BytecodeVMConfigBuilder builder,
            Boolean enabled)
    {
        if (enabled == null)
        {
            return;
        }
        if (enabled)
        {
            builder.protectCodePool(true);
        }
        builder.shuffleConstants(enabled)
                .splitCodeStreams(enabled)
                .shuffleInstructionBlocks(enabled)
                .virtualControlFlowGraph(enabled);
    }

    private static void applyObfuscate(
            BytecodeVMConfig.BytecodeVMConfigBuilder builder,
            Boolean enabled)
    {
        if (enabled == null)
        {
            return;
        }
        if (enabled)
        {
            builder.protectCodePool(true);
        }
        builder.obfuscateDispatch(enabled)
                .dynamicCodePoolBuild(enabled);
    }

    private static void validate(BytecodeVMConfig config, String target)
    {
        if (config.superInstructionCombineMin < 2 || config.superInstructionCombineMin > 32 ||
            config.superInstructionCombineMax < 2 || config.superInstructionCombineMax > 32 ||
            config.superInstructionCombineMin > config.superInstructionCombineMax)
        {
            throw new IllegalArgumentException(
                    "Invalid SDK SuperInstruction combine range on " + target +
                            ": [" + config.superInstructionCombineMin + ", " +
                            config.superInstructionCombineMax + "]");
        }
        if (config.superInstructionMinFrequency < 1 || config.superInstructionMinFrequency > 1_000_000)
        {
            throw new IllegalArgumentException(
                    "Invalid SDK SuperInstruction minFrequency on " + target +
                            ": " + config.superInstructionMinFrequency);
        }
    }

    private static VMStructure vmStructure(Object value, String target)
    {
        String name = enumName(value, target + " VM structure");
        return name == null || "CONFIG".equals(name) || "INHERIT".equals(name)
                ? null
                : VMStructure.parse(name);
    }

    private static BytecodeVMConfig.SuperInstructionMode superInstructionMode(Object value, String target)
    {
        String name = enumName(value, target + " SuperInstruction mode");
        return name == null || "CONFIG".equals(name) || "INHERIT".equals(name)
                ? null
                : BytecodeVMConfig.SuperInstructionMode.valueOf(name);
    }

    private static SdkCallPolicy callPolicy(Object value, String target)
    {
        String name = enumName(value, target + " call policy");
        return name == null || "CONFIG".equals(name) || "INHERIT".equals(name)
                ? null
                : SdkCallPolicy.valueOf(name);
    }

    private static Boolean toggle(Object value, Boolean absentDefault, String target)
    {
        String name = enumName(value, target);
        if (name == null)
        {
            return absentDefault;
        }
        return switch (name)
        {
            case "CONFIG", "INHERIT" -> null;
            case "ENABLE", "ENABLED" -> true;
            case "DISABLE", "DISABLED" -> false;
            default -> throw new IllegalArgumentException("Unknown SDK toggle " + name + " on " + target);
        };
    }

    private static Integer integer(Object value, String target)
    {
        if (value == null)
        {
            return null;
        }
        if (!(value instanceof Integer number))
        {
            throw new IllegalArgumentException("Expected an integer SDK option on " + target);
        }
        return number == -1 ? null : number;
    }

    private static String enumName(Object value, String target)
    {
        if (value == null)
        {
            return null;
        }
        if (!(value instanceof String[] enumValue) || enumValue.length != 2)
        {
            throw new IllegalArgumentException("Expected an enum SDK option on " + target);
        }
        return enumValue[1];
    }

    private static AnnotationNode nested(Object value, String target)
    {
        if (value == null)
        {
            return null;
        }
        if (!(value instanceof AnnotationNode annotation))
        {
            throw new IllegalArgumentException("Expected a nested SDK annotation on " + target);
        }
        return annotation;
    }

    private static Map<String, Object> values(AnnotationNode annotation)
    {
        Map<String, Object> result = new HashMap<>();
        if (annotation == null || annotation.values == null)
        {
            return result;
        }
        for (int index = 0; index < annotation.values.size(); index += 2)
        {
            result.put((String) annotation.values.get(index), annotation.values.get(index + 1));
        }
        return result;
    }

    private static AnnotationNode find(
            List<AnnotationNode> visible,
            List<AnnotationNode> invisible,
            String descriptor)
    {
        AnnotationNode found = find(visible, descriptor);
        return found != null ? found : find(invisible, descriptor);
    }

    private static AnnotationNode find(List<AnnotationNode> annotations, String descriptor)
    {
        if (annotations == null)
        {
            return null;
        }
        for (AnnotationNode annotation : annotations)
        {
            if (descriptor.equals(annotation.desc))
            {
                return annotation;
            }
        }
        return null;
    }

    public record ClassDirectives(
            boolean protectClass,
            boolean excluded,
            SdkAnnotationOptions options,
            Boolean constantFix)
    {
        public boolean included()
        {
            return protectClass || Boolean.TRUE.equals(options.enabled());
        }
    }

    public record MethodDirectives(
            boolean methodAnnotation,
            boolean selected,
            boolean excluded,
            SdkAnnotationOptions options)
    {
    }

    public record FieldDirectives(
            boolean inlineFieldAnnotation,
            Boolean inlineField,
            boolean inlineFinalAnnotation,
            Boolean inlineFinal)
    {
    }
}
