package nhcm.bytecodevm.generator;

import lombok.Getter;
import nhcm.bytecodevm.config.BytecodeVMConfig;
import nhcm.bytecodevm.enums.VMStructure;
import nhcm.bytecodevm.data.CompiledMethod;
import nhcm.bytecodevm.data.VMIntegrityPlan;
import nhcm.bytecodevm.data.vminsn.VMInstruction;
import nhcm.bytecodevm.data.vminsn.VMMethod;
import nhcm.bytecodevm.data.VirtualizationResult;
import nhcm.bytecodevm.generator.integrity.VMIntegrityGenerator;
import nhcm.bytecodevm.generator.integrity.IntegrityEntryTransformer;
import nhcm.bytecodevm.generator.globalclass.MethodFrameGenerator;
import nhcm.bytecodevm.generator.globalclass.VMCodePoolGenerator;
import nhcm.bytecodevm.generator.globalclass.VMProgramGenerator;
import nhcm.bytecodevm.generator.editor.InvocationBridgeGenerator;
import nhcm.bytecodevm.generator.editor.MethodsReplacer;
import nhcm.bytecodevm.generator.virtualization.CodePoolGenerator;
import nhcm.bytecodevm.generator.virtualization.CodePoolFootprintEstimator;
import nhcm.bytecodevm.generator.virtualization.superinstruction.SuperInstructionRegistry;
import nhcm.bytecodevm.generator.virtualization.VMGenerator;
import nhcm.bytecodevm.generator.virtualization.VMMethodSegmenter;
import nhcm.bytecodevm.generator.virtualization.VMObfProfile;
import nhcm.bytecodevm.generator.watermark.WatermarkPlan;
import nhcm.bytecodevm.progress.ProgressStage;
import nhcm.bytecodevm.progress.VirtualizationProgress;
import nhcm.bytecodevm.tools.JarTransformer;
import nhcm.bytecodevm.tools.OpcMutator;
import nhcm.bytecodevm.tools.VMMethodCompiler;
import nhcm.bytecodevm.utils.MethodUtils;
import nhcm.bytecodevm.utils.RandomUtils;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

public class VMSetGenerator
{
    private static final int CODE_POOL_METHOD_SIZE_LIMIT = 32_000;
    private static final long CODE_POOL_SEGMENT_PAYLOAD_LIMIT = 48L * 1024L;

    private final Map<MethodNode, ClassNode> methodsToObfuscate = new LinkedHashMap<>();
    private final Map<MethodNode, BytecodeVMConfig> methodConfigOverrides = new IdentityHashMap<>();
    private final Set<Integer> uniqueCodeIds = new LinkedHashSet<>();

    public final String vmClassName;
    public final String codePoolClassName;
    public final OpcMutator opcMutator;

    @Getter
    private final MethodFrameGenerator methodFrameGenerator;
    @Getter
    private final VMProgramGenerator vmProgramGenerator;
    @Getter
    private final VMCodePoolGenerator vmCodePoolGenerator;
    private final VMMethodCompiler compiler;
    private final InvocationBridgeGenerator invocationBridgeGenerator;
    private final List<CompiledMethod> compiledMethods = new ArrayList<>();
    private final Map<MethodNode, CompiledMethod> compiledBySource = new IdentityHashMap<>();
    private final Map<MethodNode, IntegrityEntryPoint> integrityEntryBySource = new IdentityHashMap<>();
    private final List<CompiledMethod> codePoolMethods = new ArrayList<>();
    private final Map<CompiledMethod, Boolean> singleMethodCodePoolFits = new IdentityHashMap<>();
    @Getter
    private final List<CodePoolGenerator> codePoolGenerators = new ArrayList<>();
    private final BytecodeVMConfig config;
    public final VMStructure vmStructure;
    private final GeneratedMemberNamer namer;
    private final VMObfProfile protectionProfile;
    private final SuperInstructionRegistry superInstructions;
    private final int integrityCapability;
    private final WatermarkPlan watermarkPlan;

    public VMSetGenerator(
            String name, String location,
            OpcMutator opcMutator,
            MethodFrameGenerator methodFrameGenerator,
            VMProgramGenerator vmProgramGenerator,
            VMCodePoolGenerator vmCodePoolGenerator,
            BytecodeVMConfig config)
    {
        this(name, location, opcMutator, methodFrameGenerator, vmProgramGenerator, vmCodePoolGenerator, config, GeneratedMemberNamer.DISABLED);
    }

    public VMSetGenerator(
            String name, String location,
            OpcMutator opcMutator,
            MethodFrameGenerator methodFrameGenerator,
            VMProgramGenerator vmProgramGenerator,
            VMCodePoolGenerator vmCodePoolGenerator,
            BytecodeVMConfig config,
            GeneratedMemberNamer namer)
    {
        this(name, location, opcMutator, methodFrameGenerator, vmProgramGenerator,
                vmCodePoolGenerator, config, namer, null);
    }

    public VMSetGenerator(
            String name, String location,
            OpcMutator opcMutator,
            MethodFrameGenerator methodFrameGenerator,
            VMProgramGenerator vmProgramGenerator,
            VMCodePoolGenerator vmCodePoolGenerator,
            BytecodeVMConfig config,
            GeneratedMemberNamer namer,
            WatermarkPlan watermarkPlan)
    {
        this.vmClassName = namer.className(location, name);
        this.codePoolClassName = namer.className(classPackage(vmClassName), classSimpleName(vmClassName) + "$CodePool");
        this.opcMutator = opcMutator;
        this.methodFrameGenerator = methodFrameGenerator;
        this.vmProgramGenerator = vmProgramGenerator;
        this.vmCodePoolGenerator = vmCodePoolGenerator;
        this.config = config.resolveVMStructure();
        this.vmStructure = this.config.vmStructure;
        this.integrityCapability = nonZeroRandom();
        this.watermarkPlan = watermarkPlan;
        this.namer = namer;
        this.invocationBridgeGenerator = new InvocationBridgeGenerator(namer);
        this.protectionProfile = VMObfProfile.random();
        this.superInstructions = new SuperInstructionRegistry(this.config.superInstructionMaxHandlers);
        this.compiler = new VMMethodCompiler(opcMutator);
    }

    private static String classPackage(String className)
    {
        int slash = className.lastIndexOf('/');
        return slash < 0 ? "" : className.substring(0, slash);
    }

    private static String classSimpleName(String className)
    {
        int slash = className.lastIndexOf('/');
        return slash < 0 ? className : className.substring(slash + 1);
    }

    public VirtualizationResult compile()
    {
        return compile(null, VirtualizationProgress.silent());
    }

    public VirtualizationResult compile(VirtualizationProgress progress)
    {
        return compile(null, progress);
    }

    public VirtualizationResult compile(
            JarTransformer.JarContext serializationContext,
            VirtualizationProgress progress)
    {
        Objects.requireNonNull(progress, "progress");
        compiledMethods.clear();
        compiledBySource.clear();
        integrityEntryBySource.clear();
        codePoolMethods.clear();
        singleMethodCodePoolFits.clear();
        codePoolGenerators.clear();
        superInstructions.clear();

        List<PendingMethod> invocationBridges = new ArrayList<>();
        try (ProgressStage stage = progress.compilingMethods(methodsToObfuscate.size()))
        {
            for (Map.Entry<MethodNode, ClassNode> entry : methodsToObfuscate.entrySet())
            {
                ClassNode owner = entry.getValue();
                MethodNode method = entry.getKey();
                for (MethodNode bridge : invocationBridgeGenerator.rewrite(owner, method))
                {
                    if (config.virtualizeInvocationBridges && InvocationBridgeGenerator.canVirtualizeBridge(bridge))
                    {
                        invocationBridges.add(new PendingMethod(owner, bridge));
                        stage.addWork(1);
                    }
                }
                compileMethod(owner, method);
                stage.advance(describeMethod(owner, method));
            }
            for (PendingMethod bridge : invocationBridges)
            {
                compileMethod(bridge.owner(), bridge.method());
                stage.advance(describeMethod(bridge.owner(), bridge.method()));
            }
        }

        List<List<CompiledMethod>> codePoolPartitions;
        try (ProgressStage stage = progress.packingCodePools(codePoolMethods.size()))
        {
            codePoolPartitions = partitionCompiledMethods(stage);
        }
        try (ProgressStage stage = progress.generatingCodePools(codePoolPartitions.size()))
        {
            generateCodePools(codePoolPartitions, stage);
        }
        validateCodePoolCoverage();

        ClassNode vmClass;
        List<ClassNode> vmAuxiliaryClasses;
        try (ProgressStage stage = progress.generatingVmRuntime())
        {
            stage.setDetail("Generating runtime class");
            VMGenerator vmGenerator = new VMGenerator(
                    vmClassName,
                    codePoolGenerators,
                    opcMutator,
                    methodFrameGenerator,
                    vmProgramGenerator,
                    vmCodePoolGenerator,
                    config,
                    namer,
                    protectionProfile,
                    superInstructions,
                    integrityCapability,
                    watermarkPlan);
            stage.setDetail("Collecting runtime classes");
            vmClass = vmGenerator.getClassNode();
            vmAuxiliaryClasses = vmGenerator.getAuxiliaryClasses();
            stage.advance("Runtime classes generated");
        }

        List<ClassNode> codePoolClasses = new ArrayList<>();
        for (CodePoolGenerator codePoolGenerator : codePoolGenerators)
        {
            codePoolClasses.add(codePoolGenerator.getClassNode());
        }

        IntegrityBuild integrityBuild = buildIntegrity(
                serializationContext,
                vmClass,
                codePoolClasses,
                vmAuxiliaryClasses);

        Set<MethodNode> integrityProtectedMethods = selectIntegrityProtectedMethods(integrityBuild.plan());
        Map<String, ClassNode> transformedTargets;
        try (ProgressStage stage = progress.replacingMethods(compiledMethods.size()))
        {
            transformedTargets = new MethodsReplacer(
                    compiledMethods,
                    vmClassName,
                    integrityBuild.plan(),
                    integrityProtectedMethods).transform(method ->
                            stage.advance(describeMethod(method.owner, method.source)));
        }
        IntegrityEntryTransformer.Result integrityEntries = prepareIntegrityEntries(
                integrityBuild,
                integrityProtectedMethods);
        if (integrityBuild.plan() != null)
        {
            integrityEntries.entryNames().forEach((source, name) ->
                    integrityEntryBySource.put(
                            source,
                            new IntegrityEntryPoint(integrityBuild.plan().owner(), name)));
        }
        VirtualizationResult integrityResult = virtualizeIntegrityDerivation(
                integrityBuild,
                integrityEntries,
                progress);
        if (integrityResult != null)
        {
            transformedTargets.putAll(integrityResult.transformedTarget);
        }
        List<ClassNode> generatedClasses = new ArrayList<>(codePoolClasses);
        generatedClasses.add(methodFrameGenerator.getClassNode());
        generatedClasses.add(vmProgramGenerator.getClassNode());
        generatedClasses.add(vmCodePoolGenerator.getClassNode());
        generatedClasses.addAll(vmAuxiliaryClasses);
        if (integrityResult != null)
        {
            generatedClasses.add(integrityResult.vmClass);
            generatedClasses.addAll(integrityResult.codePoolClass);
        }
        return new VirtualizationResult(transformedTargets, vmClass, generatedClasses);
    }

    private IntegrityBuild buildIntegrity(
            JarTransformer.JarContext serializationContext,
            ClassNode vmClass,
            List<ClassNode> codePoolClasses,
            List<ClassNode> vmAuxiliaryClasses)
    {
        boolean hasProtectedMethod = compiledMethods.stream().anyMatch(method ->
                method.config != null &&
                method.config.vmIntegrityCheck &&
                method.config.vmIntegrityCheckRatio > 0.0D);
        if (!hasProtectedMethod || serializationContext == null || compiledMethods.isEmpty())
        {
            return IntegrityBuild.empty();
        }

        Map<String, ClassNode> hashClassesByName = new LinkedHashMap<>();
        addHashClass(hashClassesByName, methodFrameGenerator.getClassNode());
        addHashClass(hashClassesByName, vmProgramGenerator.getClassNode());
        addHashClass(hashClassesByName, vmCodePoolGenerator.getClassNode());
        addHashClass(hashClassesByName, vmClass);
        for (ClassNode auxiliaryClass : vmAuxiliaryClasses)
        {
            addHashClass(hashClassesByName, auxiliaryClass);
        }
        for (ClassNode codePoolClass : codePoolClasses)
        {
            addHashClass(hashClassesByName, codePoolClass);
        }
        List<ClassNode> hashClasses = new ArrayList<>(hashClassesByName.values());

        JarTransformer.JarContext snapshot = new JarTransformer.JarContext();
        snapshot.classes.putAll(serializationContext.classes);
        for (ClassNode hashClass : hashClasses)
        {
            snapshot.addClass(hashClass);
        }
        snapshot.resources.putAll(serializationContext.resources);

        List<VMIntegrityGenerator.HashTarget> targets = new ArrayList<>();
        for (ClassNode hashClass : hashClasses)
        {
            int seed = nonZeroRandom();
            int expected = VMIntegrityGenerator.hashBytes(JarTransformer.toBytes(hashClass, snapshot), seed);
            targets.add(new VMIntegrityGenerator.HashTarget(hashClass.name + ".class", seed, expected));
        }

        String carrierName = namer.className(
                classPackage(vmClassName),
                classSimpleName(vmClassName) + "$Integrity");
        VMIntegrityGenerator generator = new VMIntegrityGenerator(
                carrierName,
                targets,
                integrityCapability,
                config.vmIntegrityCheckRatio,
                config.vmIntegrityRecheckInterval,
                namer);

        return new IntegrityBuild(generator.plan(), generator.classNode(), generator.derivationMethods());
    }

    private Set<MethodNode> selectIntegrityProtectedMethods(VMIntegrityPlan plan)
    {
        Set<MethodNode> selected = Collections.newSetFromMap(new IdentityHashMap<>());
        if (plan == null)
        {
            return selected;
        }
        for (CompiledMethod method : compiledMethods)
        {
            BytecodeVMConfig methodConfig = method.config == null ? config : method.config;
            if (methodConfig.vmIntegrityCheck &&
                RandomUtils.randomDouble() <= methodConfig.vmIntegrityCheckRatio)
            {
                selected.add(method.source);
            }
        }
        return selected;
    }

    private IntegrityEntryTransformer.Result prepareIntegrityEntries(
            IntegrityBuild integrityBuild,
            Set<MethodNode> protectedMethods)
    {
        if (integrityBuild.plan() == null || protectedMethods.isEmpty())
        {
            return IntegrityEntryTransformer.Result.empty();
        }
        return new IntegrityEntryTransformer(
                integrityBuild.carrierClass(),
                integrityBuild.plan(),
                vmClassName,
                namer).transform(compiledMethods, protectedMethods);
    }

    private VirtualizationResult virtualizeIntegrityDerivation(
            IntegrityBuild integrityBuild,
            IntegrityEntryTransformer.Result integrityEntries,
            VirtualizationProgress progress)
    {
        if (integrityBuild.plan() == null)
        {
            return null;
        }

        VMSetGenerator integrityVm = new VMSetGenerator(
                classSimpleName(integrityBuild.plan().owner()) + "$VM",
                classPackage(integrityBuild.plan().owner()),
                OpcMutator.MutateStrategy.RANDOM_INT.getMutator(),
                methodFrameGenerator,
                vmProgramGenerator,
                vmCodePoolGenerator,
                config.integrityConfig(),
                namer,
                watermarkPlan);
        for (IntegrityEntryTransformer.GeneratedColdEntry coldEntry : integrityEntries.coldEntries())
        {
            integrityVm.addMethod(coldEntry.method(), coldEntry.owner());
        }
        for (MethodNode derivationMethod : integrityBuild.derivationMethods())
        {
            integrityVm.addMethod(derivationMethod, integrityBuild.carrierClass());
        }
        try (VirtualizationProgress integrityProgress = progress.forVm(integrityVm.vmClassName))
        {
            return integrityVm.compile(integrityProgress);
        }
    }

    private static void addHashClass(Map<String, ClassNode> classes, ClassNode classNode)
    {
        classes.putIfAbsent(classNode.name, classNode);
    }

    private void compileMethod(ClassNode owner, MethodNode method)
    {
        VMMethod vmMethod = compiler.compile(owner, method);
        BytecodeVMConfig methodConfig = resolveMethodConfig(owner, method);

        int codeId = generateUniqueCodeId();
        CompiledMethod compiledMethod = new CompiledMethod(
                owner,
                method,
                vmMethod,
                codeId,
                List.of(codeId),
                method.desc,
                MethodUtils.isStatic(method),
                true,
                methodConfig);
        List<CompiledMethod> codePoolParts = splitForCodePools(compiledMethod);
        codePoolMethods.addAll(codePoolParts);
        if (codePoolParts.size() == 1)
        {
            CompiledMethod publishedMethod = codePoolParts.getFirst();
            compiledMethods.add(publishedMethod);
            compiledBySource.put(method, publishedMethod);
            return;
        }

        CompiledMethod segmented = new CompiledMethod(
                owner,
                method,
                vmMethod,
                codePoolParts.getFirst().codeId,
                codePoolParts.stream().map(part -> part.codeId).toList(),
                method.desc,
                MethodUtils.isStatic(method),
                false,
                methodConfig);
        compiledMethods.add(segmented);
        compiledBySource.put(method, segmented);
    }

    private BytecodeVMConfig resolveMethodConfig(ClassNode owner, MethodNode method)
    {
        BytecodeVMConfig methodConfig = methodConfigOverrides.get(method);
        if (methodConfig == null)
        {
            methodConfig = config.forMethod(owner, method);
        }
        if (methodConfig.vmStructure == vmStructure)
        {
            return methodConfig;
        }

        // Automatic SDK tiers have already been assigned to this concrete VM set.
        // Keep per-method toggles, but encode its CodePool for the owning runtime.
        return methodConfig.toBuilder()
                .vmStructure(vmStructure)
                .build();
    }

    private void generateCodePools(
            List<List<CompiledMethod>> partitions,
            ProgressStage progress)
    {
        for (int index = 0; index < partitions.size(); index++)
        {
            String poolClassName = poolClassName(index, partitions.size());
            codePoolGenerators.add(new CodePoolGenerator(
                    poolClassName,
                    partitions.get(index),
                    vmProgramGenerator,
                    vmCodePoolGenerator,
                    config,
                    true,
                    namer,
                    protectionProfile,
                    superInstructions));
            progress.advance(poolClassName);
        }
    }

    private void validateCodePoolCoverage()
    {
        Set<Integer> registeredCodeIds = new HashSet<>();
        for (CodePoolGenerator generator : codePoolGenerators)
        {
            registeredCodeIds.addAll(generator.registeredCodeIds());
        }
        for (CompiledMethod method : compiledMethods)
        {
            for (int codeId : method.codeIds)
            {
                if (!registeredCodeIds.contains(codeId))
                {
                    throw new IllegalStateException(
                            "VM code id is not registered before replacement: " + codeId +
                            " for " + describeMethod(method.owner, method.source));
                }
            }
        }
    }

    Set<Integer> registeredCodeIds()
    {
        Set<Integer> registeredCodeIds = new HashSet<>();
        for (CodePoolGenerator generator : codePoolGenerators)
        {
            registeredCodeIds.addAll(generator.registeredCodeIds());
        }
        return Collections.unmodifiableSet(registeredCodeIds);
    }

    private String poolClassName(int index, int partitionCount)
    {
        if (partitionCount == 1 || !namer.enabled())
        {
            return partitionCount == 1 ? codePoolClassName : codePoolClassName + '$' + index;
        }
        return namer.className(classPackage(vmClassName), classSimpleName(codePoolClassName) + '$' + index);
    }

    private List<List<CompiledMethod>> partitionCompiledMethods(ProgressStage progress)
    {
        List<List<CompiledMethod>> partitions = new ArrayList<>();
        List<CompiledMethod> current = new ArrayList<>();

        for (int index = 0; index < codePoolMethods.size(); index++)
        {
            CompiledMethod method = codePoolMethods.get(index);
            current.add(method);
            if (!fitsInCodePool(current))
            {
                current.remove(current.size() - 1);
                if (current.isEmpty())
                {
                    throw methodTooLarge(method);
                }
                partitions.add(new ArrayList<>(current));
                current.clear();
                current.add(method);
                if (!fitsInCodePool(current))
                {
                    throw methodTooLarge(method);
                }
            }
            progress.advance(describeMethod(method.owner, method.source));
        }

        if (!current.isEmpty())
        {
            partitions.add(new ArrayList<>(current));
        }
        return partitions;
    }

    private boolean fitsInCodePool(List<CompiledMethod> methods)
    {
        if (methods.size() == 1)
        {
            Boolean cached = singleMethodCodePoolFits.get(methods.getFirst());
            if (cached != null)
            {
                return cached;
            }
        }
        BytecodeVMConfig sizingConfig = config.toBuilder()
                .superInstruction(false)
                .build();
        List<CompiledMethod> sizingMethods = methods.stream()
                .map(method -> withSizingConfig(method, sizingConfig))
                .toList();
        CodePoolGenerator candidate = new CodePoolGenerator(
                codePoolClassName,
                sizingMethods,
                vmProgramGenerator,
                vmCodePoolGenerator,
                sizingConfig,
                false,
                GeneratedMemberNamer.DISABLED,
                protectionProfile,
                new SuperInstructionRegistry(config.superInstructionMaxHandlers));
        boolean fits = candidate.getMaxGeneratedMethodSize() <= CODE_POOL_METHOD_SIZE_LIMIT;
        if (methods.size() == 1)
        {
            singleMethodCodePoolFits.put(methods.getFirst(), fits);
        }
        return fits;
    }

    private static CompiledMethod withSizingConfig(
            CompiledMethod method,
            BytecodeVMConfig sizingConfig)
    {
        BytecodeVMConfig methodConfig = method.config == null
                ? sizingConfig
                : method.config.toBuilder().superInstruction(false).build();
        return new CompiledMethod(
                method.owner,
                method.source,
                method.vmMethod,
                method.codeId,
                method.codeIds,
                method.descriptor,
                method.isStatic,
                method.virtualizeInstructionAddresses,
                methodConfig);
    }

    private List<CompiledMethod> splitForCodePools(CompiledMethod method)
    {
        long estimatedPayload = CodePoolFootprintEstimator.estimate(method.vmMethod);
        if (estimatedPayload <= CODE_POOL_SEGMENT_PAYLOAD_LIMIT &&
            fitsInCodePool(List.of(method)))
        {
            return List.of(method);
        }

        List<VMInstruction> instructions = method.vmMethod.getInstructions();
        if (instructions.size() <= 1)
        {
            if (fitsInCodePool(List.of(method)))
            {
                return List.of(method);
            }
            throw methodTooLarge(method);
        }

        // The unsplit method has already failed sizing. Start with two real ranges instead of
        // retrying the same range under a new random CodePool layout and a different code id.
        int mid = CodePoolFootprintEstimator.weightedMidpoint(
                method.vmMethod,
                instructions,
                0,
                instructions.size());
        List<CompiledMethod> parts = new ArrayList<>();
        parts.addAll(splitRange(method, instructions, 0, mid));
        parts.addAll(splitRange(method, instructions, mid, instructions.size()));
        return List.copyOf(parts);
    }

    private List<CompiledMethod> splitRange(
            CompiledMethod method,
            List<VMInstruction> instructions,
            int from,
            int to)
    {
        CompiledMethod segment = segment(method, instructions, from, to);
        long estimatedPayload = CodePoolFootprintEstimator.estimate(
                method.vmMethod,
                instructions,
                from,
                to);
        if (estimatedPayload <= CODE_POOL_SEGMENT_PAYLOAD_LIMIT &&
            fitsInCodePool(List.of(segment)))
        {
            return List.of(segment);
        }
        if (to - from <= 1)
        {
            if (fitsInCodePool(List.of(segment)))
            {
                return List.of(segment);
            }
            throw methodTooLarge(method, estimatedPayload);
        }

        int mid = CodePoolFootprintEstimator.weightedMidpoint(
                method.vmMethod,
                instructions,
                from,
                to);
        List<CompiledMethod> parts = new ArrayList<>();
        parts.addAll(splitRange(method, instructions, from, mid));
        parts.addAll(splitRange(method, instructions, mid, to));
        return parts;
    }

    private CompiledMethod segment(
            CompiledMethod method,
            List<VMInstruction> instructions,
            int from,
            int to)
    {
        VMMethod segmentMethod = VMMethodSegmenter.segment(
                method.vmMethod,
                instructions,
                from,
                to);
        int codeId = generateUniqueCodeId();
        return new CompiledMethod(
                method.owner,
                method.source,
                segmentMethod,
                codeId,
                List.of(codeId),
                method.descriptor,
                method.isStatic,
                false,
                method.config);
    }

    private static IllegalStateException methodTooLarge(CompiledMethod method)
    {
        return methodTooLarge(method, CodePoolFootprintEstimator.estimate(method.vmMethod));
    }

    private static IllegalStateException methodTooLarge(CompiledMethod method, long estimatedPayload)
    {
        return new IllegalStateException(
                "VM method cannot fit in a CodePool: " +
                        method.owner.name + '.' +
                        method.source.name + method.source.desc +
                        " (estimated payload " + estimatedPayload + " byte(s))");
    }

    private static String describeMethod(ClassNode owner, MethodNode method)
    {
        return owner.name.replace('/', '.') + '.' + method.name + method.desc;
    }

    public void addMethod(MethodNode methodNode, ClassNode classNode)
    {
        methodsToObfuscate.put(methodNode, classNode);
    }

    public void addMethod(
            MethodNode methodNode,
            ClassNode classNode,
            BytecodeVMConfig methodConfig)
    {
        methodsToObfuscate.put(methodNode, classNode);
        methodConfigOverrides.put(methodNode, methodConfig);
    }

    public int methodCount()
    {
        return methodsToObfuscate.size();
    }

    public CompiledMethod compiledMethod(MethodNode source)
    {
        return compiledBySource.get(source);
    }

    public IntegrityEntryPoint integrityEntry(MethodNode source)
    {
        return integrityEntryBySource.get(source);
    }

    private int generateUniqueCodeId()
    {
        int codeId;
        do
        {
            codeId = RandomUtils.randomInt();
        } while (uniqueCodeIds.contains(codeId));
        uniqueCodeIds.add(codeId);
        return codeId;
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

    public boolean hasMethods()
    {
        return !methodsToObfuscate.isEmpty();
    }

    private record IntegrityBuild(VMIntegrityPlan plan, ClassNode carrierClass, List<MethodNode> derivationMethods)
    {
        private static IntegrityBuild empty()
        {
            return new IntegrityBuild(null, null, List.of());
        }
    }

    private record PendingMethod(ClassNode owner, MethodNode method)
    {
    }

    public record IntegrityEntryPoint(String owner, String name)
    {
        public static final String DESCRIPTOR =
                "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;";
    }
}
