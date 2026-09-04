package nhcm.bytecodevm.generator;

import nhcm.bytecodevm.config.BytecodeVMConfig;
import nhcm.bytecodevm.config.TargetMatcher;
import nhcm.bytecodevm.config.sdk.SdkAnnotationOptions.SdkCallPolicy;
import nhcm.bytecodevm.config.sdk.SdkAnnotationReader;
import nhcm.bytecodevm.config.sdk.SdkAnnotationRemover;
import nhcm.bytecodevm.data.VirtualizationResult;
import nhcm.bytecodevm.enums.VMStructure;
import nhcm.bytecodevm.generator.editor.transformers.ConstantFixTransformer;
import nhcm.bytecodevm.generator.editor.transformers.NumberTransformer;
import nhcm.bytecodevm.generator.editor.transformers.StringTransformer;
import nhcm.bytecodevm.generator.editor.transformers.ConstantEncryptionStats;
import nhcm.bytecodevm.generator.editor.inlining.InlineFieldCompatibility;
import nhcm.bytecodevm.generator.editor.inlining.ClassHierarchyResolver;
import nhcm.bytecodevm.generator.editor.inlining.InlineFieldTransformer;
import nhcm.bytecodevm.generator.editor.inlining.InlineFieldRuntimeGenerator;
import nhcm.bytecodevm.generator.editor.inlining.InlineProtectedMethodTransformer;
import nhcm.bytecodevm.generator.watermark.WatermarkGenerator;
import nhcm.bytecodevm.generator.watermark.WatermarkPlan;
import nhcm.bytecodevm.generator.globalclass.MethodFrameGenerator;
import nhcm.bytecodevm.generator.globalclass.VMCodePoolGenerator;
import nhcm.bytecodevm.generator.globalclass.VMProgramGenerator;
import nhcm.bytecodevm.progress.ConsoleVirtualizationProgress;
import nhcm.bytecodevm.progress.VirtualizationProgress;
import nhcm.bytecodevm.tools.JarTransformer;
import nhcm.bytecodevm.tools.OpcMutator;
import nhcm.bytecodevm.utils.ClassUtils;
import nhcm.bytecodevm.utils.LogColors;
import nhcm.bytecodevm.utils.MethodUtils;
import nhcm.bytecodevm.utils.RandomUtils;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class Obfuscator
{
    private static final Logger logger = LoggerFactory.getLogger(Obfuscator.class);

    private final BytecodeVMConfig config;
    private final TargetMatcher targetInclude;
    private final TargetMatcher targetExclude;

    private final List<VMSetGenerator> VMSetGenerators = new ArrayList<>();
    private final GeneratedMemberNamer namer;
    private final Long seed;
    private PlanningStats planningStats = PlanningStats.empty();
    private final List<ObfuscationReport.MethodPlan> plannedMethods = new ArrayList<>();
    private final List<ObfuscationReport.Diagnostic> planningDiagnostics = new ArrayList<>();
    private final Map<String, Integer> skippedMethods = new LinkedHashMap<>();
    private final Map<String, Integer> vmProfileOrdinals = new HashMap<>();
    private int inputClassCount;
    private int inputResourceCount;
    private int outputClassCount;
    private int outputResourceCount;
    private WatermarkPlan watermarkPlan;
    private InlineFieldTransformer.Result inlineFieldResult;
    private InlineProtectedMethodTransformer inlineMethodTransformer;
    private InlineProtectedMethodTransformer.Prepared inlineMethodPrepared;

    public Obfuscator(BytecodeVMConfig config)
    {
        this(config, null);
    }

    public Obfuscator(BytecodeVMConfig config, Long seed)
    {
        this.config = config;
        this.seed = seed;
        this.namer = new GeneratedMemberNamer(config);
        this.targetExclude = new TargetMatcher();
        for(String exclusion : config.exclusions)
        {
            targetExclude.add(exclusion);
        }
        this.targetInclude = new TargetMatcher();
        for(String include : config.includes)
        {
            targetInclude.add(include);
        }
    }

    public ObfuscationReport obfuscate() throws IOException
    {
        requireInput();
        resetPlanning();
        long started = System.nanoTime();
        logger.info("{}", LogColors.lifecycle(
                "Obfuscating " +
                        LogColors.path(config.inputFile.toAbsolutePath()) +
                        " -> " +
                        LogColors.path(config.outputFile.toAbsolutePath())));
        JarTransformer.transformJar(
                config.inputFile.toFile(),
                config.outputFile.toFile(),
                this::obfuscateProcess);
        return createReport("protect")
                .withElapsedMillis((System.nanoTime() - started) / 1_000_000L);
    }

    public ObfuscationReport inspect() throws IOException
    {
        requireInput();
        resetPlanning();
        long started = System.nanoTime();
        JarTransformer.JarContext context = JarTransformer.readJar(config.inputFile.toFile());
        inputClassCount = context.classes.size();
        inputResourceCount = context.resources.size();
        namer.reserveClassNames(context.classes.keySet());
        watermarkPlan = createWatermark(context);
        processJar(context);
        outputClassCount = inputClassCount;
        outputResourceCount = inputResourceCount;
        return createReport("inspect")
                .withElapsedMillis((System.nanoTime() - started) / 1_000_000L);
    }

    private void requireInput() throws NoSuchFileException
    {
        if (!Files.isRegularFile(config.inputFile))
        {
            throw new NoSuchFileException(config.inputFile.toAbsolutePath().toString());
        }
    }

    private void resetPlanning()
    {
        VMSetGenerators.clear();
        plannedMethods.clear();
        planningDiagnostics.clear();
        skippedMethods.clear();
        vmProfileOrdinals.clear();
        planningStats = PlanningStats.empty();
        inputClassCount = 0;
        inputResourceCount = 0;
        outputClassCount = 0;
        outputResourceCount = 0;
        watermarkPlan = null;
        inlineFieldResult = null;
        inlineMethodTransformer = null;
        inlineMethodPrepared = new InlineProtectedMethodTransformer.Prepared(0, 0, 0);
    }

    private void obfuscateProcess(JarTransformer.JarContext context)
    {
        inputClassCount = context.classes.size();
        inputResourceCount = context.resources.size();
        namer.reserveClassNames(context.classes.keySet());
        watermarkPlan = createWatermark(context);
        processJar(context);
        context.addClass(watermarkPlan.runtimeClassNode());
        logger.info("{}", LogColors.lifecycle("Adding required VM support classes"));
        logger.debug("{}", LogColors.success("VM support classes are isolated per VM set"));
        Set<String> finalCodeIdValidationClasses = new LinkedHashSet<>(context.classes.keySet());
        List<VMSetGenerator> generators = new ArrayList<>(this.VMSetGenerators);
        for(VMSetGenerator generator : generators)
        {
            logger.info("{}", LogColors.virtualize(
                    "Virtualizing VM: " +
                            LogColors.strong(generator.vmClassName) +
                            " [" + generator.vmStructure + "]" +
                            " (" + generator.methodCount() + " method(s))"));
            VirtualizationResult result;
            try (VirtualizationProgress progress =
                         new ConsoleVirtualizationProgress(generator.vmClassName))
            {
                result = generator.compile(context, progress);
            }
            context.classes.putAll(result.transformedTarget);
            context.addClass(result.vmClass);
            for(ClassNode codePoolClass : result.codePoolClass)
            {
                context.addClass(codePoolClass);
            }
            logger.debug(
                    "VM {} generated {} support class(es) and transformed {} target class(es)",
                    generator.vmClassName,
                    result.codePoolClass.size() + 1,
                    result.transformedTarget.size());
            logger.info("{}", LogColors.success("Done virtualizing VM: " + LogColors.strong(generator.vmClassName)));
        }
        if (inlineMethodTransformer != null)
        {
            InlineProtectedMethodTransformer.Finished finished = inlineMethodTransformer.finish();
            if (finished.directEntries() != 0)
            {
                logger.info("{}", LogColors.success(
                        "Installed " + LogColors.strong(finished.directEntries()) +
                                " direct VM entry/entries in their original method slots"));
            }
        }
        validateFinalCodeIds(
                finalCodeIdValidationClasses.stream()
                        .map(context.classes::get)
                        .filter(Objects::nonNull)
                        .toList(),
                generators);
        logger.info("{}", LogColors.success("Done virtualizing all classes"));
        if (config.removeAnnotations)
        {
            int removed = SdkAnnotationRemover.remove(context.classes.values());
            logger.debug("Removed {} BytecodeVM SDK annotation(s)", removed);
        }
        outputClassCount = context.classes.size();
        outputResourceCount = context.resources.size();
    }

    private void validateFinalCodeIds(
            Collection<ClassNode> classes,
            Collection<VMSetGenerator> generators)
    {
        Map<String, Set<Integer>> registeredByRuntime = new HashMap<>();
        for (VMSetGenerator generator : generators)
        {
            registeredByRuntime.put(generator.vmClassName, generator.registeredCodeIds());
        }

        for (ClassNode owner : classes)
        {
            boolean generatedVmClass = registeredByRuntime.keySet().stream().anyMatch(
                    runtime -> owner.name.equals(runtime) || owner.name.startsWith(runtime + '$'));
            if (generatedVmClass)
            {
                continue;
            }
            for (MethodNode method : owner.methods)
            {
                for (AbstractInsnNode instruction = method.instructions.getFirst();
                     instruction != null;
                     instruction = instruction.getNext())
                {
                    if (!(instruction instanceof MethodInsnNode))
                    {
                        continue;
                    }
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    Set<Integer> registered = registeredByRuntime.get(call.owner);
                    if (registered == null || !"execute".equals(call.name) || !call.desc.startsWith("(I"))
                    {
                        continue;
                    }

                    Integer codeId = nearestIntegerConstant(call.getPrevious());
                    if (codeId != null && !registered.contains(codeId))
                    {
                        throw new IllegalStateException(
                                "Generated VM wrapper references an unknown code id: " + codeId +
                                " in " + owner.name + '.' + method.name + method.desc +
                                " (runtime " + call.owner + ')');
                    }
                }
            }
        }
    }

    private static Integer nearestIntegerConstant(AbstractInsnNode instruction)
    {
        for (int remaining = 16; instruction != null && remaining-- > 0; instruction = instruction.getPrevious())
        {
            if (instruction instanceof LdcInsnNode)
            {
                Object constant = ((LdcInsnNode) instruction).cst;
                if (constant instanceof Integer)
                {
                    return (Integer) constant;
                }
            }
            else if (instruction instanceof IntInsnNode)
            {
                return ((IntInsnNode) instruction).operand;
            }
            else
            {
                int opcode = instruction.getOpcode();
                if (opcode >= org.objectweb.asm.Opcodes.ICONST_M1 &&
                        opcode <= org.objectweb.asm.Opcodes.ICONST_5)
                {
                    return opcode - org.objectweb.asm.Opcodes.ICONST_0;
                }
            }
        }
        return null;
    }

    private PreTransformStats runPreTransformers(Collection<ClassNode> classNodes)
    {
        int fixedConstants = new ConstantFixTransformer(config).transform(classNodes);
        if (fixedConstants != 0)
        {
            logger.info("{}", LogColors.scan("Moved " + LogColors.strong(fixedConstants) + " static final constant(s) into <clinit>"));
        }
        // Numbers run first so they only see source constants, not the integer
        // material emitted by the string decoder pass.
        NumberTransformer numberTransformer = new NumberTransformer(config);
        int encryptedNumbers = numberTransformer.transform(classNodes);
        ConstantEncryptionStats numberStats = numberTransformer.stats();
        if(numberStats.candidates() != 0)
        {
            logger.info("{}", LogColors.scan(
                    "Pre-encrypted " + LogColors.strong(encryptedNumbers) + "/" +
                            LogColors.strong(numberStats.candidates()) + " number(s) before virtualization" +
                            adaptiveSuffix(numberStats)));
        }
        StringTransformer stringTransformer = new StringTransformer(config);
        int encryptedStrings = stringTransformer.transform(classNodes);
        ConstantEncryptionStats stringStats = stringTransformer.stats();
        if(stringStats.candidates() != 0)
        {
            logger.info("{}", LogColors.scan(
                    "Pre-encrypted " + LogColors.strong(encryptedStrings) + "/" +
                            LogColors.strong(stringStats.candidates()) + " string(s) before virtualization" +
                            adaptiveSuffix(stringStats)));
        }
        return new PreTransformStats(fixedConstants, encryptedStrings, encryptedNumbers);
    }

    private static String adaptiveSuffix(ConstantEncryptionStats stats)
    {
        String growth = "estimated +" + LogColors.strong(stats.estimatedGrowth()) + " byte(s)";
        if (stats.skipped() == 0)
        {
            return " (" + growth + ')';
        }
        return " (" + growth + ", skipped " + LogColors.strong(stats.skipped()) +
                " to keep generated methods within the adaptive size budget)";
    }

    private void processJar(JarTransformer.JarContext context)
    {
        logger.info("{}", LogColors.scan("Scanning input file for methods to obfuscate"));

        InlineFieldCompatibility fieldCompatibility =
                InlineFieldCompatibility.analyze(context.classes.values());
        PreTransformStats transforms = runPreTransformers(context.classes.values());

        String fieldRuntimeName = uniqueSupportClassName(context, "FieldStore");
        String weakFieldKeyName = uniqueSupportClassName(context, "WeakIdentityKey");
        InlineFieldRuntimeGenerator.GeneratedRuntime fieldRuntime =
                InlineFieldRuntimeGenerator.generate(fieldRuntimeName, weakFieldKeyName, namer);
        InlineFieldTransformer fieldTransformer = new InlineFieldTransformer(
                config,
                context.classes.values(),
                fieldRuntime,
                fieldCompatibility,
                namer);
        Set<MethodNode> fieldReferencedMethods = config.includeReferencedMethods
                ? fieldTransformer.referencedMethods()
                : Set.of();
        Set<MethodNode> protectedMethods = Collections.newSetFromMap(new IdentityHashMap<>());

        String globalLocation = "BytecodeVM";

        Map<GeneratorProfile, List<VMSetGenerator>> allInOneVms = new LinkedHashMap<>();
        Map<String, Map<GeneratorProfile, List<VMSetGenerator>>> perClassVms = new LinkedHashMap<>();
        List<VMSetGenerator> perMethods = new ArrayList<>();
        Map<String, Map<GeneratorProfile, List<VMSetGenerator>>> perPackage = new LinkedHashMap<>();
        Map<GeneratorGroupKey, Integer> generatorOrdinals = new HashMap<>();
        Map<MethodNode, VMSetGenerator> methodAssignments = new IdentityHashMap<>();

        Set<String> securityManagerClasses = securityManagerClasses(context.classes.values());
        List<MethodCandidate> candidates = collectMethodCandidates(context.classes.values(), securityManagerClasses);
        Map<MethodId, MethodCandidate> candidateById = indexCandidates(candidates);
        Map<MethodId, Set<MethodId>> callsByMethod = collectInternalCalls(
                candidates,
                candidateById,
                new ClassHierarchyResolver(context.classes.values()));
        Set<MethodId> rootMethods = rootMethods(candidates);
        Set<MethodId> includeRoots = rootsWithPolicy(rootMethods, candidateById, SdkCallPolicy.INCLUDE);
        Set<MethodId> excludeRoots = rootsWithPolicy(rootMethods, candidateById, SdkCallPolicy.EXCLUDE);
        Set<MethodId> includedCalls = collectCalledWithin(includeRoots, callsByMethod, candidateById);
        Set<MethodId> excludedCalls = collectCalledWithin(excludeRoots, callsByMethod, candidateById);
        Set<MethodId> referencedCallers = referencedCallers(
                candidates,
                candidateById,
                callsByMethod,
                includedCalls,
                excludedCalls);

        int matchedMethods = 0;
        int calledMethodsIncluded = 0;
        int calledMethodsExcluded = 0;

        for(ClassNode classNode : context.classes.values())
        {
            for(MethodNode methodNode : classNode.methods)
            {
                MethodCandidate candidate = candidateById.get(MethodId.of(classNode, methodNode));
                if(candidate == null)
                {
                    continue;
                }
                boolean includedByCall = includedCalls.contains(candidate.id) && !candidate.explicitIncluded;
                boolean includedByField = fieldReferencedMethods.contains(methodNode) && !candidate.explicitIncluded;
                boolean includedByReference = referencedCallers.contains(candidate.id) && !candidate.explicitIncluded;
                boolean excludedByCall = excludedCalls.contains(candidate.id) && !rootMethods.contains(candidate.id);
                if(!selected(
                        candidate,
                        includedByCall || includedByField || includedByReference,
                        excludedByCall))
                {
                    if (excludedByCall && candidate.eligible && !candidate.explicitExcluded)
                    {
                        calledMethodsExcluded++;
                    }
                    continue;
                }
                if (includedByCall)
                {
                    calledMethodsIncluded++;
                }

                matchedMethods++;
                protectedMethods.add(methodNode);
                VMSetGenerator assignedGenerator = assignMethodToVM(
                        classNode,
                        methodNode,
                        candidate.methodConfig,
                        globalLocation,
                        allInOneVms,
                        perClassVms,
                        perMethods,
                        perPackage,
                        generatorOrdinals);
                methodAssignments.put(methodNode, assignedGenerator);
                plannedMethods.add(new ObfuscationReport.MethodPlan(
                        candidate.id.owner,
                        candidate.id.name,
                        candidate.id.desc,
                        includedByField
                                ? "FIELD_REFERENCE"
                                : includedByReference
                                        ? "METHOD_REFERENCE"
                                        : selectionSource(candidate, includedByCall),
                        assignedGenerator.vmClassName,
                        assignedGenerator.vmStructure.name()));
            }
        }

        inlineFieldResult = fieldTransformer.transform(protectedMethods);
        for (InlineFieldTransformer.ConstructorHelper helper : inlineFieldResult.constructorHelpers())
        {
            protectedMethods.add(helper.method());
            BytecodeVMConfig helperConfig = config.forMethod(helper.owner(), helper.method());
            VMSetGenerator assignedGenerator = assignMethodToVM(
                    helper.owner(),
                    helper.method(),
                    helperConfig,
                    globalLocation,
                    allInOneVms,
                    perClassVms,
                    perMethods,
                    perPackage,
                    generatorOrdinals);
            methodAssignments.put(helper.method(), assignedGenerator);
        }

        switch(config.createMode)
        {
            case PER_CLASS -> perClassVms.values().forEach(profiles ->
                    profiles.values().forEach(generators -> addNonEmpty(VMSetGenerators, generators)));
            case PER_METHOD -> VMSetGenerators.addAll(perMethods);
            case PER_PACKAGE -> perPackage.values().forEach(profiles ->
                    profiles.values().forEach(generators -> addNonEmpty(VMSetGenerators, generators)));
            case ONE_FOR_ALL ->
                    allInOneVms.values().forEach(generators ->
                            addNonEmpty(VMSetGenerators, generators));
        }

        if (!inlineFieldResult.runtimeClasses().isEmpty())
        {
            inlineFieldResult.runtimeClasses().forEach(context::addClass);
            logger.info("{}", LogColors.scan(
                    "Inlined " + LogColors.strong(inlineFieldResult.fields()) +
                            " field(s) and rewrote " + LogColors.strong(inlineFieldResult.accesses()) +
                            " access site(s)" +
                            (inlineFieldResult.constructorHelpers().isEmpty()
                                    ? ""
                                    : " through " +
                                      LogColors.strong(inlineFieldResult.constructorHelpers().size()) +
                                      " virtualized constructor helper(s)") +
                            (inlineFieldResult.skipped() == 0
                                    ? ""
                                    : " (skipped " + LogColors.strong(inlineFieldResult.skipped()) +
                                      " unsafe or unsupported field(s))")));
        }

        inlineMethodTransformer = new InlineProtectedMethodTransformer(
                config,
                context.classes.values(),
                protectedMethods,
                methodAssignments);
        InlineProtectedMethodTransformer.Prepared inlineMethods = inlineMethodTransformer.prepare();
        inlineMethodPrepared = inlineMethods;
        if (inlineMethods.methods() != 0 || inlineMethods.skipped() != 0)
        {
            logger.info("{}", LogColors.scan(
                    "Prepared " + LogColors.strong(inlineMethods.methods()) +
                            " original-slot VM entry/entries for " +
                            LogColors.strong(inlineMethods.calls()) + " call site(s)" +
                            (inlineMethods.skipped() == 0
                                    ? ""
                                    : " (skipped " + LogColors.strong(inlineMethods.skipped()) +
                                      " unsafe target(s))")));
        }

        int eligibleMethods = (int) candidates.stream().filter(MethodCandidate::eligible).count();
        int explicitlyIncludedMethods = (int) candidates.stream()
                .filter(candidate -> candidate.eligible && candidate.explicitIncluded)
                .count();
        int explicitlyExcludedMethods = (int) candidates.stream()
                .filter(candidate -> candidate.eligible && candidate.explicitExcluded)
                .count();
        for (MethodCandidate candidate : candidates)
        {
            if (!candidate.eligible)
            {
                skippedMethods.merge(candidate.ineligibleReason, 1, Integer::sum);
            }
        }
        planningStats = new PlanningStats(
                candidates.size(),
                eligibleMethods,
                explicitlyIncludedMethods,
                explicitlyExcludedMethods,
                matchedMethods,
                calledMethodsIncluded,
                calledMethodsExcluded,
                transforms.fixedConstants,
                transforms.encryptedStrings,
                transforms.encryptedNumbers);
        createPlanningDiagnostics(explicitlyIncludedMethods, matchedMethods);

        logger.info("{}", LogColors.scan(
                "Scanned input file, found " +
                LogColors.strong(matchedMethods) +
                " method(s) across " +
                LogColors.strong(VMSetGenerators.size()) +
                " VM set(s)"
        ));
        if (!includeRoots.isEmpty() || !excludeRoots.isEmpty())
        {
            logger.info("{}", LogColors.scan(
                    "Call expansion included " +
                    LogColors.strong(calledMethodsIncluded) +
                    " and excluded " +
                    LogColors.strong(calledMethodsExcluded) +
                    " target method(s)"));
        }
        logger.debug(
                "Method planning: {} total, {} eligible, {} explicitly included, {} explicitly excluded",
                candidates.size(),
                eligibleMethods,
                explicitlyIncludedMethods,
                explicitlyExcludedMethods);
        for (VMSetGenerator generator : VMSetGenerators)
        {
            logger.debug(
                    "Planned VM {} [{}] with {} method(s)",
                    generator.vmClassName,
                    generator.vmStructure,
                    generator.methodCount());
        }
    }

    private ObfuscationReport createReport(String mode) throws IOException
    {
        List<ObfuscationReport.VMSet> vmSets = VMSetGenerators.stream()
                .map(generator -> new ObfuscationReport.VMSet(
                        generator.vmClassName,
                        generator.vmStructure.name(),
                        generator.methodCount()))
                .toList();
        return new ObfuscationReport(
                mode,
                ObfuscationReport.currentVersion(),
                config.inputFile.toAbsolutePath().normalize().toString(),
                "protect".equals(mode) ? config.outputFile.toAbsolutePath().normalize().toString() : null,
                seed,
                sha256(config.inputFile),
                "protect".equals(mode) ? sha256(config.outputFile) : null,
                0L,
                config.toMap(),
                watermarkPlan == null ? Map.of() : watermarkPlan.metadata(),
                inputClassCount,
                inputResourceCount,
                planningStats.totalMethods,
                planningStats.eligibleMethods,
                planningStats.explicitlyIncludedMethods,
                planningStats.explicitlyExcludedMethods,
                planningStats.matchedMethods,
                planningStats.calledMethodsIncluded,
                planningStats.calledMethodsExcluded,
                planningStats.fixedConstants,
                planningStats.preEncryptedStrings,
                planningStats.preEncryptedNumbers,
                inlineFieldResult == null ? 0 : inlineFieldResult.fields(),
                inlineFieldResult == null ? 0 : inlineFieldResult.accesses(),
                inlineMethodPrepared == null ? 0 : inlineMethodPrepared.methods(),
                inlineMethodPrepared == null ? 0 : inlineMethodPrepared.calls(),
                skippedMethods,
                vmSets.size(),
                vmSets,
                plannedMethods,
                planningDiagnostics,
                outputClassCount,
                outputResourceCount,
                Math.max(0, outputClassCount - inputClassCount),
                false);
    }

    private static String sha256(java.nio.file.Path path) throws IOException
    {
        MessageDigest digest;
        try
        {
            digest = MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        try (InputStream input = Files.newInputStream(path))
        {
            byte[] buffer = new byte[16_384];
            int count;
            while ((count = input.read(buffer)) >= 0)
            {
                digest.update(buffer, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private VMSetGenerator newVMSetGenerator(
            String name,
            String location,
            BytecodeVMConfig generatorConfig)
    {
        BytecodeVMConfig resolvedConfig = generatorConfig.resolveVMStructure();
        MethodFrameGenerator methodFrameGenerator = new MethodFrameGenerator(
                namer.className(location, name + "$Frame"),
                namer,
                resolvedConfig.vmStructure);
        VMProgramGenerator vmProgramGenerator = new VMProgramGenerator(
                namer.className(location, name + "$Program"),
                namer);
        VMCodePoolGenerator vmCodePoolGenerator = new VMCodePoolGenerator(
                namer.className(location, name + "$PoolRecord"),
                vmProgramGenerator,
                namer);
        return new VMSetGenerator(
                name,
                location,
                OpcMutator.MutateStrategy.RANDOM_INT.getMutator(),
                methodFrameGenerator,
                vmProgramGenerator,
                vmCodePoolGenerator,
                resolvedConfig,
                namer,
                watermarkPlan);
    }

    private WatermarkPlan createWatermark(JarTransformer.JarContext context)
    {
        GeneratedMemberNamer watermarkNamer = namer;
        if (!namer.enabled())
        {
            watermarkNamer = new GeneratedMemberNamer(config.toBuilder()
                    .renameMode(BytecodeVMConfig.RenameMode.ENABLE)
                    .build());
            watermarkNamer.reserveClassNames(context.classes.keySet());
        }

        String className = watermarkNamer.className("BytecodeVM", "Watermark");
        try
        {
            return WatermarkGenerator.generate(
                    className,
                    config.watermark,
                    sha256(config.inputFile),
                    watermarkNamer);
        }
        catch (IOException exception)
        {
            throw new IllegalStateException("Cannot create BytecodeVM watermark", exception);
        }
    }

    private List<VMSetGenerator> newVMSetGenerators(
            String name,
            String location,
            BytecodeVMConfig generatorConfig)
    {
        int count = generatorConfig.createMode == BytecodeVMConfig.VMCreateMode.PER_METHOD
                ? 1
                : generatorConfig.vmCount;
        List<VMSetGenerator> generators = new ArrayList<>(count);
        for (int index = 0; index < count; index++)
        {
            String vmName = count == 1 ? name : name + "$" + index;
            generators.add(newVMSetGenerator(vmName, location, generatorConfig));
        }
        return generators;
    }

    private VMSetGenerator assignMethodToVM(
            ClassNode owner,
            MethodNode method,
            BytecodeVMConfig methodConfig,
            String globalLocation,
            Map<GeneratorProfile, List<VMSetGenerator>> allInOneVms,
            Map<String, Map<GeneratorProfile, List<VMSetGenerator>>> perClassVms,
            List<VMSetGenerator> perMethods,
            Map<String, Map<GeneratorProfile, List<VMSetGenerator>>> perPackage,
            Map<GeneratorGroupKey, Integer> generatorOrdinals)
    {
        String classPackage = ClassUtils.getPackageName(owner);
        String vmLocation = getVMLocation(globalLocation, classPackage, owner);
        GeneratorProfile profile = GeneratorProfile.of(methodConfig);
        VMSetGenerator assignedGenerator;
        switch(config.createMode)
        {
            case PER_CLASS ->
            {
                Map<GeneratorProfile, List<VMSetGenerator>> classProfiles =
                        perClassVms.computeIfAbsent(owner.name, ignored -> new LinkedHashMap<>());
                List<VMSetGenerator> generators = classProfiles.computeIfAbsent(
                        profile,
                        ignored -> newVMSetGenerators(
                                profileName(ClassUtils.getSimpleName(owner) + "$VM", vmLocation),
                                vmLocation,
                                profile.apply(config)));
                GeneratorGroupKey key = new GeneratorGroupKey(owner.name, profile);
                int ordinal = generatorOrdinals.merge(key, 1, Integer::sum) - 1;
                assignedGenerator = pickGenerator(generators, ordinal);
            }
            case PER_METHOD ->
            {
                assignedGenerator = newVMSetGenerator(
                        ClassUtils.getSimpleName(owner) + "$" + method.name + "$VM",
                        vmLocation,
                        profile.apply(config));
                perMethods.add(assignedGenerator);
            }
            case PER_PACKAGE ->
            {
                Map<GeneratorProfile, List<VMSetGenerator>> packageProfiles =
                        perPackage.computeIfAbsent(classPackage, ignored -> new LinkedHashMap<>());
                List<VMSetGenerator> generators = packageProfiles.computeIfAbsent(
                        profile,
                        ignored -> newVMSetGenerators(
                                profileName(classPackage + "$VM", classPackage),
                                classPackage,
                                profile.apply(config)));
                GeneratorGroupKey key = new GeneratorGroupKey(classPackage, profile);
                int ordinal = generatorOrdinals.merge(key, 1, Integer::sum) - 1;
                assignedGenerator = pickGenerator(generators, ordinal);
            }
            case ONE_FOR_ALL ->
            {
                List<VMSetGenerator> generators = allInOneVms.computeIfAbsent(
                        profile,
                        ignored -> newVMSetGenerators(
                                profileName("BytecodeVM", "BytecodeVM"),
                                "BytecodeVM",
                                profile.apply(config)));
                GeneratorGroupKey key = new GeneratorGroupKey("", profile);
                int ordinal = generatorOrdinals.merge(key, 1, Integer::sum) - 1;
                assignedGenerator = pickGenerator(generators, ordinal);
            }
            default -> throw new IllegalStateException("Unknown VM create mode: " + config.createMode);
        }
        assignedGenerator.addMethod(method, owner);
        return assignedGenerator;
    }

    private static VMSetGenerator pickGenerator(List<VMSetGenerator> generators, int ordinal)
    {
        if (generators.isEmpty())
        {
            throw new IllegalArgumentException("No VM generator is available");
        }
        return generators.get(Math.floorMod(ordinal, generators.size()));
    }

    private String profileName(String base, String location)
    {
        String key = location + '/' + base;
        int ordinal = vmProfileOrdinals.merge(key, 1, Integer::sum) - 1;
        return ordinal == 0 ? base : base + "$Profile" + ordinal;
    }

    private static void addNonEmpty(List<VMSetGenerator> target, List<VMSetGenerator> candidates)
    {
        for (VMSetGenerator generator : candidates)
        {
            if (generator.hasMethods())
            {
                target.add(generator);
            }
        }
    }

    private List<MethodCandidate> collectMethodCandidates(
            Collection<ClassNode> classes,
            Set<String> securityManagerClasses)
    {
        List<MethodCandidate> candidates = new ArrayList<>();
        for (ClassNode classNode : classes)
        {
            Set<String> stackTraceSensitiveMethods = stackTraceSensitiveMethods(classNode);
            SdkAnnotationReader.ClassDirectives classDirectives =
                    SdkAnnotationReader.classDirectives(classNode);
            boolean securityManagerClass = securityManagerClasses.contains(classNode.name);
            for (MethodNode methodNode : classNode.methods)
            {
                SdkAnnotationReader.MethodDirectives sdkMethod =
                        SdkAnnotationReader.methodDirectives(classNode, methodNode);
                String ineligibleReason = ineligibleReason(
                        methodNode,
                        securityManagerClass,
                        stackTraceSensitiveMethods.contains(methodKey(methodNode)));
                boolean explicitIncluded = sdkMethod.selected() ||
                        targetInclude.isMethodContextMatched(classNode, methodNode);
                boolean explicitExcluded = classDirectives.excluded() ||
                        sdkMethod.excluded() ||
                        targetExclude.isMethodContextMatched(classNode, methodNode);
                BytecodeVMConfig methodConfig = config.forMethod(classNode, methodNode);
                candidates.add(new MethodCandidate(
                        classNode,
                        methodNode,
                        MethodId.of(classNode, methodNode),
                        ineligibleReason == null,
                        ineligibleReason,
                        explicitIncluded,
                        explicitExcluded,
                        sdkMethod.selected(),
                        methodConfig,
                        callPolicy(methodConfig)));
            }
        }
        return candidates;
    }

    private static Map<MethodId, MethodCandidate> indexCandidates(List<MethodCandidate> candidates)
    {
        Map<MethodId, MethodCandidate> byId = new LinkedHashMap<>();
        for (MethodCandidate candidate : candidates)
        {
            byId.put(candidate.id, candidate);
        }
        return byId;
    }

    private static Set<MethodId> rootMethods(List<MethodCandidate> candidates)
    {
        Set<MethodId> roots = new LinkedHashSet<>();
        for (MethodCandidate candidate : candidates)
        {
            if (candidate.eligible && candidate.explicitIncluded && !candidate.explicitExcluded)
            {
                roots.add(candidate.id);
            }
        }
        return roots;
    }

    private static Set<MethodId> rootsWithPolicy(
            Set<MethodId> roots,
            Map<MethodId, MethodCandidate> candidates,
            SdkCallPolicy policy)
    {
        Set<MethodId> selected = new LinkedHashSet<>();
        for (MethodId root : roots)
        {
            MethodCandidate candidate = candidates.get(root);
            if (candidate != null && candidate.callPolicy == policy)
            {
                selected.add(root);
            }
        }
        return Set.copyOf(selected);
    }

    private static SdkCallPolicy callPolicy(BytecodeVMConfig methodConfig)
    {
        if (methodConfig.excludeMethodsCalledWithin)
        {
            return SdkCallPolicy.EXCLUDE;
        }
        if (methodConfig.includeMethodsCalledWithin)
        {
            return SdkCallPolicy.INCLUDE;
        }
        return SdkCallPolicy.NONE;
    }

    private static Map<MethodId, Set<MethodId>> collectInternalCalls(
            List<MethodCandidate> candidates,
            Map<MethodId, MethodCandidate> candidateById,
            ClassHierarchyResolver hierarchy)
    {
        Map<MethodId, Set<MethodId>> calls = new LinkedHashMap<>();
        for (MethodCandidate candidate : candidates)
        {
            Set<MethodId> targets = new LinkedHashSet<>();
            for (AbstractInsnNode instruction : candidate.method.instructions)
            {
                if (!(instruction instanceof MethodInsnNode methodInsn))
                {
                    continue;
                }
                ClassHierarchyResolver.MethodDeclaration declaration = hierarchy.resolveMethod(
                        methodInsn.owner,
                        methodInsn.name,
                        methodInsn.desc);
                MethodId target = declaration == null
                        ? new MethodId(methodInsn.owner, methodInsn.name, methodInsn.desc)
                        : new MethodId(declaration.owner().name, methodInsn.name, methodInsn.desc);
                MethodCandidate targetCandidate = candidateById.get(target);
                if (targetCandidate != null && targetCandidate.eligible)
                {
                    targets.add(target);
                }
            }
            calls.put(candidate.id, Set.copyOf(targets));
        }
        return Map.copyOf(calls);
    }

    private static Set<MethodId> collectCalledWithin(
            Set<MethodId> roots,
            Map<MethodId, Set<MethodId>> callsByMethod,
            Map<MethodId, MethodCandidate> candidateById)
    {
        Set<MethodId> called = new LinkedHashSet<>();
        Deque<MethodId> work = new ArrayDeque<>(roots);
        while (!work.isEmpty())
        {
            MethodId current = work.removeFirst();
            for (MethodId target : callsByMethod.getOrDefault(current, Set.of()))
            {
                if (roots.contains(target) || !called.add(target))
                {
                    continue;
                }
                MethodCandidate candidate = candidateById.get(target);
                if (candidate != null && candidate.eligible && !candidate.explicitExcluded)
                {
                    work.addLast(target);
                }
            }
        }
        return Set.copyOf(called);
    }

    private Set<MethodId> referencedCallers(
            List<MethodCandidate> candidates,
            Map<MethodId, MethodCandidate> candidateById,
            Map<MethodId, Set<MethodId>> callsByMethod,
            Set<MethodId> includedCalls,
            Set<MethodId> excludedCalls)
    {
        if (!config.inlineCalledProtectedMethods || !config.includeReferencedMethods)
        {
            return Set.of();
        }
        Set<MethodId> selected = new LinkedHashSet<>();
        for (MethodCandidate candidate : candidates)
        {
            boolean includedByCall = includedCalls.contains(candidate.id);
            boolean excludedByCall = excludedCalls.contains(candidate.id);
            if (selected(candidate, includedByCall, excludedByCall))
            {
                selected.add(candidate.id);
            }
        }

        Set<MethodId> referenced = new LinkedHashSet<>();
        boolean changed;
        do
        {
            changed = false;
            for (Map.Entry<MethodId, Set<MethodId>> entry : callsByMethod.entrySet())
            {
                MethodCandidate caller = candidateById.get(entry.getKey());
                if (caller == null || !caller.eligible || caller.explicitExcluded ||
                    excludedCalls.contains(caller.id) || selected.contains(caller.id))
                {
                    continue;
                }
                boolean callsInlineTarget = entry.getValue().stream().anyMatch(targetId ->
                {
                    MethodCandidate target = candidateById.get(targetId);
                    return selected.contains(targetId) && target != null && inlineMethodCandidate(target);
                });
                if (callsInlineTarget)
                {
                    selected.add(caller.id);
                    referenced.add(caller.id);
                    changed = true;
                }
            }
        } while (changed);
        return Set.copyOf(referenced);
    }

    private boolean inlineMethodCandidate(MethodCandidate candidate)
    {
        MethodNode method = candidate.method;
        if (method.name.startsWith("<") ||
            (method.access & (org.objectweb.asm.Opcodes.ACC_ABSTRACT |
                              org.objectweb.asm.Opcodes.ACC_NATIVE |
                              org.objectweb.asm.Opcodes.ACC_SYNCHRONIZED)) != 0)
        {
            return false;
        }
        return (!config.ignorePublicCalls || (method.access & org.objectweb.asm.Opcodes.ACC_PRIVATE) != 0) &&
               (!config.annotationOnly ||
                SdkAnnotationReader.methodDirectives(candidate.owner, method).methodAnnotation()) &&
               config.matchRules.statementMatches("inlineCalledProtectedMethods", candidate.owner, method);
    }

    private boolean selected(MethodCandidate candidate, boolean includedByCall, boolean excludedByCall)
    {
        return candidate.eligible &&
               (candidate.explicitIncluded || includedByCall) &&
               !candidate.explicitExcluded &&
               !excludedByCall;
    }

    private static String selectionSource(MethodCandidate candidate, boolean includedByCall)
    {
        if (includedByCall)
        {
            return "CALL_GRAPH";
        }
        return candidate.sdkIncluded ? "SDK_ANNOTATION" : "CONFIG_MATCH";
    }

    private void createPlanningDiagnostics(int explicitlyIncludedMethods, int matchedMethods)
    {
        if (matchedMethods == 0)
        {
            planningDiagnostics.add(new ObfuscationReport.Diagnostic(
                    "WARN",
                    "NO_METHODS_SELECTED",
                    "No eligible methods will be virtualized; check includes, exclusions, and SDK annotations."));
        }
        else if (explicitlyIncludedMethods == 0)
        {
            planningDiagnostics.add(new ObfuscationReport.Diagnostic(
                    "INFO",
                    "CALL_GRAPH_ONLY",
                    "All selected methods came from call-graph expansion."));
        }
        if (planningStats.explicitlyExcludedMethods > 0)
        {
            planningDiagnostics.add(new ObfuscationReport.Diagnostic(
                    "INFO",
                    "EXPLICIT_EXCLUSIONS",
                    planningStats.explicitlyExcludedMethods +
                            " eligible method(s) were excluded by configuration or SDK annotations."));
        }
        if (!skippedMethods.isEmpty())
        {
            planningDiagnostics.add(new ObfuscationReport.Diagnostic(
                    "INFO",
                    "INELIGIBLE_METHODS",
                    "Methods skipped for VM compatibility: " + skippedMethods));
        }
        for (ObfuscationReport.Diagnostic diagnostic : planningDiagnostics)
        {
            if ("WARN".equals(diagnostic.level()))
            {
                logger.warn("Inspect {}: {}", diagnostic.code(), diagnostic.message());
            }
            else
            {
                logger.debug("Inspect {}: {}", diagnostic.code(), diagnostic.message());
            }
        }
    }

    private String getVMLocation(String globalLocation, String classPackage, ClassNode classNode)
    {
        return switch(config.location)
        {
            case ONE_PACKAGE -> globalLocation;
            case NEW_PACKAGE -> classPackage + "/" + classNode.name + "VM";
            case SAME_PACKAGE_AS_TARGET -> classPackage;
        };
    }

    private boolean shouldSkipMethod(
            ClassNode classNode,
            MethodNode methodNode,
            Set<String> securityManagerClasses,
            Set<String> stackTraceSensitiveMethods)
    {
        return securityManagerClasses.contains(classNode.name) ||
               shouldIgnoreMethod(methodNode) ||
               stackTraceSensitiveMethods.contains(methodKey(methodNode)) ||
               !targetInclude.isMethodMatched(classNode, methodNode) ||
               targetExclude.isMethodMatched(classNode, methodNode);
    }

    private static boolean shouldIgnoreMethod(MethodNode methodNode)
    {
        return "<init>".equals(methodNode.name) ||
               MethodUtils.isAbstract(methodNode) ||
               MethodUtils.isNative(methodNode) ||
               usesStackTraceIntrospection(methodNode);
    }

    private static String ineligibleReason(
            MethodNode methodNode,
            boolean securityManagerClass,
            boolean stackTraceSensitive)
    {
        if (securityManagerClass)
        {
            return "SECURITY_MANAGER_CLASS";
        }
        if ("<init>".equals(methodNode.name))
        {
            return "CONSTRUCTOR";
        }
        if (MethodUtils.isAbstract(methodNode))
        {
            return "ABSTRACT";
        }
        if (MethodUtils.isNative(methodNode))
        {
            return "NATIVE";
        }
        if (stackTraceSensitive)
        {
            return "STACK_TRACE_SENSITIVE";
        }
        return null;
    }

    private static String methodKey(MethodNode methodNode)
    {
        return methodNode.name + methodNode.desc;
    }

    private static Set<String> stackTraceSensitiveMethods(ClassNode classNode)
    {
        Set<String> sensitiveMethods = new HashSet<>();
        for (MethodNode methodNode : classNode.methods)
        {
            if (usesStackTraceIntrospection(methodNode))
            {
                sensitiveMethods.add(methodKey(methodNode));
            }
        }

        boolean changed;
        do
        {
            changed = false;
            for (MethodNode methodNode : classNode.methods)
            {
                String key = methodKey(methodNode);
                if (sensitiveMethods.contains(key))
                {
                    continue;
                }
                if (callsSensitiveMethod(classNode, methodNode, sensitiveMethods))
                {
                    sensitiveMethods.add(key);
                    changed = true;
                }
            }
        } while (changed);

        return sensitiveMethods;
    }

    private static boolean callsSensitiveMethod(ClassNode classNode, MethodNode methodNode, Set<String> sensitiveMethods)
    {
        for (AbstractInsnNode insn : methodNode.instructions)
        {
            if (!(insn instanceof MethodInsnNode methodInsn))
            {
                continue;
            }
            if (classNode.name.equals(methodInsn.owner) &&
                sensitiveMethods.contains(methodInsn.name + methodInsn.desc))
            {
                return true;
            }
        }
        return false;
    }

    private static Set<String> securityManagerClasses(Collection<ClassNode> classNodes)
    {
        Map<String, String> superNames = new HashMap<>();
        for (ClassNode classNode : classNodes)
        {
            superNames.put(classNode.name, classNode.superName);
        }

        Set<String> securityManagers = new HashSet<>();
        boolean changed;
        do
        {
            changed = false;
            for (ClassNode classNode : classNodes)
            {
                if (securityManagers.contains(classNode.name))
                {
                    continue;
                }
                String superName = classNode.superName;
                if ("java/lang/SecurityManager".equals(superName) || securityManagers.contains(superName))
                {
                    securityManagers.add(classNode.name);
                    changed = true;
                    continue;
                }
                while (superNames.containsKey(superName))
                {
                    superName = superNames.get(superName);
                    if ("java/lang/SecurityManager".equals(superName) || securityManagers.contains(superName))
                    {
                        securityManagers.add(classNode.name);
                        changed = true;
                        break;
                    }
                }
            }
        } while (changed);

        return securityManagers;
    }

    private static boolean usesStackTraceIntrospection(MethodNode methodNode)
    {
        for (AbstractInsnNode insn : methodNode.instructions)
        {
            if (!(insn instanceof MethodInsnNode methodInsn))
            {
                continue;
            }
            if ("java/lang/Throwable".equals(methodInsn.owner) &&
                "getStackTrace".equals(methodInsn.name) &&
                "()[Ljava/lang/StackTraceElement;".equals(methodInsn.desc))
            {
                return true;
            }
            if ("java/lang/Thread".equals(methodInsn.owner) &&
                "getStackTrace".equals(methodInsn.name) &&
                "()[Ljava/lang/StackTraceElement;".equals(methodInsn.desc))
            {
                return true;
            }
            if (methodInsn.owner.startsWith("java/lang/StackWalker"))
            {
                return true;
            }
        }
        return false;
    }

    private record MethodId(String owner, String name, String desc)
    {
        private static MethodId of(ClassNode owner, MethodNode method)
        {
            return new MethodId(owner.name, method.name, method.desc);
        }
    }

    private record MethodCandidate(
            ClassNode owner,
            MethodNode method,
            MethodId id,
            boolean eligible,
            String ineligibleReason,
            boolean explicitIncluded,
            boolean explicitExcluded,
            boolean sdkIncluded,
            BytecodeVMConfig methodConfig,
            SdkCallPolicy callPolicy)
    {
    }

    private record GeneratorProfile(
            VMStructure structure,
            int superInstructionMaxHandlers,
            int superInstructionMinFrequency)
    {
        private static GeneratorProfile of(BytecodeVMConfig methodConfig)
        {
            return new GeneratorProfile(
                    methodConfig.vmStructure,
                    methodConfig.superInstructionMaxHandlers,
                    methodConfig.superInstructionMinFrequency);
        }

        private BytecodeVMConfig apply(BytecodeVMConfig base)
        {
            return base.toBuilder()
                    .vmStructure(structure)
                    .superInstructionMaxHandlers(superInstructionMaxHandlers)
                    .superInstructionMinFrequency(superInstructionMinFrequency)
                    .build();
        }
    }

    private record GeneratorGroupKey(String scope, GeneratorProfile profile)
    {
    }

    private String uniqueSupportClassName(JarTransformer.JarContext context, String base)
    {
        if (namer.enabled())
        {
            String name;
            do
            {
                name = namer.className("BytecodeVM", base);
            } while (context.classes.containsKey(name));
            return name;
        }
        String stem = "BytecodeVM/" + base;
        String name = stem;
        int suffix = 1;
        while (context.classes.containsKey(name))
        {
            name = stem + '$' + suffix++;
        }
        return name;
    }

    private record PreTransformStats(
            int fixedConstants,
            int encryptedStrings,
            int encryptedNumbers)
    {
    }

    private record PlanningStats(
            int totalMethods,
            int eligibleMethods,
            int explicitlyIncludedMethods,
            int explicitlyExcludedMethods,
            int matchedMethods,
            int calledMethodsIncluded,
            int calledMethodsExcluded,
            int fixedConstants,
            int preEncryptedStrings,
            int preEncryptedNumbers)
    {
        private static PlanningStats empty()
        {
            return new PlanningStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

}
