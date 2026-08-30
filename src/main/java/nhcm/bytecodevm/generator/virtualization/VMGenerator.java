package nhcm.bytecodevm.generator.virtualization;

import lombok.Getter;
import nhcm.bytecodevm.advInsn.AdvIBdr;
import nhcm.bytecodevm.advInsn.Condition;
import nhcm.bytecodevm.advInsn.Expr;
import nhcm.bytecodevm.advInsn.Local;
import nhcm.bytecodevm.advInsn.SwitchCase;
import nhcm.bytecodevm.config.BytecodeVMConfig;
import nhcm.bytecodevm.enums.VMStructure;
import nhcm.bytecodevm.enums.Acc;
import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.generator.abstracts.ClassObj;
import nhcm.bytecodevm.generator.GeneratedMemberNamer;
import nhcm.bytecodevm.generator.globalclass.*;
import nhcm.bytecodevm.generator.virtualization.superinstruction.SuperInstructionRegistry;
import nhcm.bytecodevm.generator.virtualization.structure.LoweredInstructionPlanner;
import nhcm.bytecodevm.generator.virtualization.structure.VMStructurePlan;
import nhcm.bytecodevm.generator.virtualization.structure.api.VMStructureGenerationContext;
import nhcm.bytecodevm.generator.virtualization.structure.api.VMStructureGenerator;
import nhcm.bytecodevm.generator.virtualization.structure.api.VMStructureGeneratorFactory;
import nhcm.bytecodevm.generator.virtualization.structure.api.VMKernelShape;
import nhcm.bytecodevm.generator.virtualization.structure.api.VMDispatchGenerationContext;
import nhcm.bytecodevm.generator.virtualization.structure.api.VMDispatchGenerator;
import nhcm.bytecodevm.generator.virtualization.structure.api.VMDispatchTarget;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.array.ArrayLengthBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.array.LoadArrayBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.array.NewArrayBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.array.StoreArrayBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.constant.*;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.control.*;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.conversion.CompareBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.conversion.ConvertBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.field.ReadFieldBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.field.WriteFieldBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.invoke.InvokeNormalBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.local.IncrementBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.local.LoadLocalBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.local.StoreLocalBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.lowered.DataFlowRegionBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.lowered.RegisterOperationBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.math.*;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.object.CastBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.object.NewObjectBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.stack.DuplicateBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.stack.PopBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.stack.SwapBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.lock.MonitorBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;
import nhcm.bytecodevm.generator.virtualization.vminterpret.NumericType;
import nhcm.bytecodevm.generator.watermark.WatermarkPlan;
import nhcm.bytecodevm.tools.OpcMutator;
import nhcm.bytecodevm.utils.*;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;
import java.util.function.Consumer;

public class VMGenerator extends ClassObj
{
    private static final int MAX_INTERPRET_CHUNK_OPCODES = 8;
    private static final int INTERPRET_CHUNK_CODE_SIZE_LIMIT = 48_000;
    private static final int MAX_SUPER_INSTRUCTION_CHUNK_RECIPES = 8;
    private static final int SUPER_INSTRUCTION_CHUNK_CODE_SIZE_LIMIT = 48_000;

    private static final Map<Opcs, InterpretBranch> branches = new EnumMap<>(Opcs.class);

    static
    {
        registerBranches();
        validateBranches();
    }

    private static void registerBranches()
    {
        List<InterpretBranch> array = List.of(
                new ArrayLengthBranch(),
                new LoadArrayBranch(),
                new NewArrayBranch(),
                new StoreArrayBranch()
        );
        List<InterpretBranch> constant = List.of(
                new LoadConstantBranch(),
                new NopBranch(),
                new PushDoubleBranch(),
                new PushFloatBranch(),
                new PushIntBranch(),
                new PushLongBranch(),
                new PushNullBranch()
        );
        List<InterpretBranch> control = List.of(
                new FlowBranch(),
                new GotoBranch(),
                new InstanceofBranch(),
                new MonitorBranch(),
                new ReturnBranch(),
                new SwitchBranch(),
                new ThrowBranch()
        );
        List<InterpretBranch> conversion = List.of(
                new CompareBranch(),
                new ConvertBranch()
        );
        List<InterpretBranch> field = List.of(
                new ReadFieldBranch(),
                new WriteFieldBranch()
        );
        List<InterpretBranch> invoke = List.of(
                //new InvokeDynamicBranch(),
                new InvokeNormalBranch()
        );
        List<InterpretBranch> local = List.of(
                new IncrementBranch(),
                new LoadLocalBranch(),
                new StoreLocalBranch()
        );
        List<InterpretBranch> lowered = List.of(
                new RegisterOperationBranch(),
                new DataFlowRegionBranch()
        );
        List<InterpretBranch> math = List.of(
                new AddBranch(),
                new BitwiseAndBranch(),
                new BitwiseOrBranch(),
                new BitwiseXorBranch(),
                new DivideBranch(),
                new MultiplyBranch(),
                new NegateBranch(),
                new RemainderBranch(),
                new ShiftLeftBranch(),
                new ShiftRightBranch(),
                new SubtractBranch(),
                new UnsignedShiftRightBranch()
        );
        List<InterpretBranch> object = List.of(
                new CastBranch(),
                new NewObjectBranch()
        );
        List<InterpretBranch> stack = List.of(
                new DuplicateBranch(),
                new PopBranch(),
                new SwapBranch()
        );
        array.forEach(VMGenerator::register);
        constant.forEach(VMGenerator::register);
        control.forEach(VMGenerator::register);
        conversion.forEach(VMGenerator::register);
        field.forEach(VMGenerator::register);
        invoke.forEach(VMGenerator::register);
        local.forEach(VMGenerator::register);
        lowered.forEach(VMGenerator::register);
        math.forEach(VMGenerator::register);
        object.forEach(VMGenerator::register);
        stack.forEach(VMGenerator::register);
    }

    @Getter
    public final ClassNode classNode;

    @Getter
    private final List<CodePoolGenerator> codePoolGenerators;
    private final OpcMutator opcMutator;

    private final MethodFrameGenerator methodFrameGenerator;
    private final VMProgramGenerator vmProgramGenerator;
    private final VMCodePoolGenerator vmCodePoolGenerator;
    private final MethodFrameLayout frameLayout;
    private final VMProgramLayout programLayout;
    private final VMRuntimeLayout vmLayout;
    private final BytecodeVMConfig config;
    private final GeneratedMemberNamer namer;
    private final VMObfProfile profile;
    private final SuperInstructionRegistry superInstructions;
    private final int integrityCapability;
    private final WatermarkPlan watermarkPlan;
    private final VMStructurePlan structurePlan;
    private final VMStructureGenerator structureGenerator;
    private final VMStructureGenerationContext structureGeneration;
    private final Map<Integer, String> interpretChunkNames = new HashMap<>();
    private final Map<Integer, String> superInstructionChunkNames = new HashMap<>();
    private List<SuperInstructionChunk> superInstructionChunks = List.of();
    @Getter
    private final List<ClassNode> auxiliaryClasses = new ArrayList<>();

    public VMGenerator(
            String className,
            List<CodePoolGenerator> codePoolGenerators,
            OpcMutator opcMutator,
            MethodFrameGenerator methodFrameGenerator,
            VMProgramGenerator vmProgramGenerator,
            VMCodePoolGenerator vmCodePoolGenerator,
            BytecodeVMConfig config)
    {
        this(className, codePoolGenerators, opcMutator, methodFrameGenerator, vmProgramGenerator, vmCodePoolGenerator, config, GeneratedMemberNamer.DISABLED);
    }

    public VMGenerator(
            String className,
            List<CodePoolGenerator> codePoolGenerators,
            OpcMutator opcMutator,
            MethodFrameGenerator methodFrameGenerator,
            VMProgramGenerator vmProgramGenerator,
            VMCodePoolGenerator vmCodePoolGenerator,
            BytecodeVMConfig config,
            GeneratedMemberNamer namer)
    {
        this(className, codePoolGenerators, opcMutator, methodFrameGenerator, vmProgramGenerator, vmCodePoolGenerator, config, namer, VMObfProfile.random());
    }

    public VMGenerator(
            String className,
            List<CodePoolGenerator> codePoolGenerators,
            OpcMutator opcMutator,
            MethodFrameGenerator methodFrameGenerator,
            VMProgramGenerator vmProgramGenerator,
            VMCodePoolGenerator vmCodePoolGenerator,
            BytecodeVMConfig config,
            GeneratedMemberNamer namer,
            VMObfProfile profile)
    {
        this(className, codePoolGenerators, opcMutator, methodFrameGenerator, vmProgramGenerator, vmCodePoolGenerator, config, namer, profile, new SuperInstructionRegistry(config.superInstructionMaxHandlers));
    }

    public VMGenerator(
            String className,
            List<CodePoolGenerator> codePoolGenerators,
            OpcMutator opcMutator,
            MethodFrameGenerator methodFrameGenerator,
            VMProgramGenerator vmProgramGenerator,
            VMCodePoolGenerator vmCodePoolGenerator,
            BytecodeVMConfig config,
            GeneratedMemberNamer namer,
            VMObfProfile profile,
            SuperInstructionRegistry superInstructions)
    {
        this(
                className,
                codePoolGenerators,
                opcMutator,
                methodFrameGenerator,
                vmProgramGenerator,
                vmCodePoolGenerator,
                config,
                namer,
                profile,
                superInstructions,
                0,
                null);
    }

    public VMGenerator(
            String className,
            List<CodePoolGenerator> codePoolGenerators,
            OpcMutator opcMutator,
            MethodFrameGenerator methodFrameGenerator,
            VMProgramGenerator vmProgramGenerator,
            VMCodePoolGenerator vmCodePoolGenerator,
            BytecodeVMConfig config,
            GeneratedMemberNamer namer,
            VMObfProfile profile,
            SuperInstructionRegistry superInstructions,
            int integrityCapability)
    {
        this(className, codePoolGenerators, opcMutator, methodFrameGenerator,
                vmProgramGenerator, vmCodePoolGenerator, config, namer, profile,
                superInstructions, integrityCapability, null);
    }

    public VMGenerator(
            String className,
            List<CodePoolGenerator> codePoolGenerators,
            OpcMutator opcMutator,
            MethodFrameGenerator methodFrameGenerator,
            VMProgramGenerator vmProgramGenerator,
            VMCodePoolGenerator vmCodePoolGenerator,
            BytecodeVMConfig config,
            GeneratedMemberNamer namer,
            VMObfProfile profile,
            SuperInstructionRegistry superInstructions,
            int integrityCapability,
            WatermarkPlan watermarkPlan)
    {
        super(className);
        this.codePoolGenerators = List.copyOf(codePoolGenerators);
        this.opcMutator = opcMutator;
        this.methodFrameGenerator = methodFrameGenerator;
        this.vmProgramGenerator = vmProgramGenerator;
        this.vmCodePoolGenerator = vmCodePoolGenerator;
        this.frameLayout = methodFrameGenerator.getLayout();
        this.programLayout = vmProgramGenerator.getLayout();
        this.vmLayout = new VMRuntimeLayout(
                className,
                methodFrameGenerator.descriptor(),
                vmProgramGenerator.descriptor(),
                namer,
                config.vmStructure);
        this.config = config;
        this.namer = namer;
        this.profile = Objects.requireNonNull(profile, "profile");
        this.superInstructions = Objects.requireNonNull(superInstructions, "superInstructions");
        this.integrityCapability = integrityCapability;
        this.watermarkPlan = watermarkPlan;
        this.structurePlan = VMStructurePlan.forStructure(config.vmStructure);
        this.structureGenerator = VMStructureGeneratorFactory.create(config.vmStructure);
        ClassNode cn = ClassUtils.newClassNode(new Acc[]{Acc.PUBLIC, Acc.FINAL}, className);
        InsnUtils.addPrivateInit(cn);
        this.classNode = cn;
        this.structureGeneration = new VMStructureGenerationContext(
                className(),
                cn,
                frameLayout,
                programLayout,
                vmLayout,
                profile,
                structurePlan,
                this::stepCall,
                this::interpretContext,
                this::structureClassName,
                this::structureMethodName,
                namer::field,
                this::schedulerDescriptor,
                ignored -> coroutineDescriptor(),
                this::mixCall,
                auxiliaryClasses);
        String vmCodePoolSign = vmCodePoolGenerator.descriptor();
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, vmLayout.codePools.name(), vmLayout.codePools.descriptor(), "Ljava/util/List<" + vmCodePoolSign + ">;"));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, vmLayout.fieldHandles.name(), vmLayout.fieldHandles.descriptor(), "Ljava/util/Map<Ljava/lang/String;Ljava/lang/invoke/MethodHandle;>;"));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, vmLayout.methodHandles.name(), vmLayout.methodHandles.descriptor(), "Ljava/util/Map<Ljava/lang/String;Ljava/lang/invoke/MethodHandle;>;"));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, vmLayout.methodTypes.name(), vmLayout.methodTypes.descriptor(), "Ljava/util/Map<Ljava/lang/String;Ljava/lang/invoke/MethodType;>;"));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, vmLayout.monitors.name(), vmLayout.monitors.descriptor(), "Ljava/util/Map<Ljava/lang/Object;Ljava/util/concurrent/locks/ReentrantLock;>;"));
        MethodNode interpretStepMethod = genInterpretStepMethod();
        cn.methods.add(genClInitMethod(codePoolGenerators));
        cn.methods.add(genExecuteMethod());
        cn.methods.add(genExecuteWithIntegrityMethod());
        cn.methods.add(genExecuteSegmentedMethod());
        cn.methods.add(genExecuteSegmentedWithIntegrityMethod());
        cn.methods.add(interpretStepMethod);
        cn.methods.add(genInterpretMethod());
        cn.methods.add(genInstructionIndexMethod());
        if (config.vmStructure == VMStructure.SIMPLE_DISPATCH)
        {
            cn.methods.add(genDecodeOpcodeMethod());
            cn.methods.add(genDecodeNextPcMethod());
            cn.methods.add(genDecodeOriginalPcMethod());
            cn.methods.add(genDecodeOperandMethod());
        }
        cn.methods.add(genMixMethod());
        cn.methods.add(genLayoutValueMethod());
        cn.methods.add(genBlockValueMethod());
        cn.methods.add(genStateKeyMethod());
        cn.methods.add(genInstructionIndexInBlockMethod());
        cn.methods.add(genSyncStateMethod());
        cn.methods.add(genDispatchKeyMethod());
        cn.methods.add(genResolveMethod());
        cn.methods.add(genConstantStringMethod());
        cn.methods.add(genMethodTypeMethod());
        cn.methods.add(genResolveConstantMethod());
        cn.methods.add(genFindExceptionHandlerMethod());
        cn.methods.add(genGetFieldMethod());
        cn.methods.add(genSetFieldMethod());
        cn.methods.add(genFieldHandleMethod());
        cn.methods.add(genAdaptFieldHandleMethod());
        cn.methods.add(genUnsafeMethod());
        cn.methods.add(genUnsafeSetStaticFieldMethod());
        cn.methods.add(genFindFieldMethod());
        cn.methods.add(genFindMethodMethod());
        cn.methods.add(genInvokeMethod());
        cn.methods.add(genConstructMethod());
        cn.methods.add(genAdaptMethodHandleMethod());
        cn.methods.add(genAdaptDirectMethodHandleMethod());
        cn.methods.add(genAdaptConstructorHandleMethod());
        cn.methods.add(genCoerceArgumentMethod());
        cn.methods.add(genCloneArrayMethod());
        cn.methods.add(genLoadOwnerMethod());
        cn.methods.add(genLoadOwnerWithLoaderMethod());
        cn.methods.add(genMonitorForMethod());
        cn.methods.add(genMonitorEnterMethod());
        cn.methods.add(genMonitorExitMethod());
        cn.methods.add(genRethrowMethod());
        if (structurePlan.schedulerKind() == VMStructurePlan.SchedulerKind.REGISTER ||
            structurePlan.schedulerKind() == VMStructurePlan.SchedulerKind.DATA_FLOW)
        {
            cn.methods.add(genRegisterReadMethod());
            cn.methods.add(genRegisterWriteMethod());
            cn.methods.add(genExecuteRegisterOpMethod());
            if (structurePlan.schedulerKind() == VMStructurePlan.SchedulerKind.DATA_FLOW)
            {
                cn.methods.add(genExecuteDataFlowMethod());
            }
        }
    }

    private MethodNode genInterpretStepMethod()
    {
        MethodNode methodNode = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.interpretStep.name(),
                vmLayout.interpretStep.descriptor());
        LabelNode unknownOpcode = new LabelNode();
        LabelNode afterDispatch = new LabelNode();
        LabelNode batchStart = new LabelNode();
        LabelNode batchComplete = new LabelNode();
        LabelNode tryStart = new LabelNode();
        LabelNode tryEnd = new LabelNode();
        LabelNode exceptionHandler = new LabelNode();
        LabelNode noHandler = new LabelNode();
        methodNode.tryCatchBlocks.add(new org.objectweb.asm.tree.TryCatchBlockNode(
                tryStart,
                tryEnd,
                exceptionHandler,
                "java/lang/Throwable"));
        AdvIBdr ib = new AdvIBdr(methodNode);
        InterpretContext context = interpretContext(afterDispatch);
        VMKernelShape kernelShape = structureGenerator.kernelShape();
        Local structureStateArgument = ib.getLocal("structureStateArgument", "I", 4);
        Local batchCount = context.intLocal("batchCount", InterpretContext.DISPATCH_SELECTOR + 8);
        ib.set(context.structureState(), structureStateArgument);
        if (kernelShape.exceptionTableMode() == VMKernelShape.ExceptionTableMode.EAGER)
        {
            emitLoadExceptionHandlers(ib, context);
        }
        ib.set(batchCount, AdvIBdr.constant(0));
        ib.mark(batchStart, "batchStart");
        if (kernelShape.exceptionTableMode() == VMKernelShape.ExceptionTableMode.PER_STEP)
        {
            emitLoadExceptionHandlers(ib, context);
        }
        ib.ifCondition(
                AdvIBdr.isTrue(context.frameReturned()),
                b -> b.returnValue(AdvIBdr.constant(1)));
        ib.set(context.instructionPc(), context.frameProgramCounter());
        ib.set(context.originalPc(), context.instructionPc());
        ib.set(context.instructionIndex(), AdvIBdr.constant(-1));
        ib.set(context.opcode(), AdvIBdr.constant(0));

        ib.mark(tryStart, "tryStart");
        ib.set(context.instructionIndex(), AdvIBdr.callStatic(
                vmLayout.owner,
                vmLayout.instructionIndex.name(),
                "I",
                context.program(),
                context.frame(),
                context.instructionPc()));
        ib.ifCondition(
                AdvIBdr.equal(context.instructionIndex(), AdvIBdr.constant(-1)),
                b -> b.returnValue(AdvIBdr.constant(1)));
        ib.set(context.operandIndex(), AdvIBdr.constant(0));
        emitKernelDecode(ib, context, kernelShape);

        generateDispatch(
                ib,
                afterDispatch,
                unknownOpcode
        );
        ib.mark(afterDispatch, "afterDispatch");
        if (structurePlan.schedulerKind() == VMStructurePlan.SchedulerKind.SELF_MODIFYING)
        {
            emitSelfMutation(ib, context);
        }
        ib.ifCondition(
                AdvIBdr.isFalse(context.frameReturned()),
                b -> b.directCall(AdvIBdr.callStatic(
                        vmLayout.owner,
                        vmLayout.syncState.name(),
                        "V",
                        context.program(),
                        context.frame(),
                        context.frameProgramCounter())));
        ib.gotoLabel(batchComplete);
        ib.mark(tryEnd, "tryEnd");

        ib.mark(unknownOpcode, "unknownOpcode");
        generateUnknownOpcode(ib);

        ib.mark(exceptionHandler, "exceptionHandler");
        ib.storeTop(context.thrown());
        if (kernelShape.exceptionTableMode() == VMKernelShape.ExceptionTableMode.ON_THROW)
        {
            emitLoadExceptionHandlers(ib, context);
        }
        ib.set(context.handlerPc(), AdvIBdr.callStatic(
                vmLayout.owner,
                vmLayout.findExceptionHandler.name(),
                "I",
                context.thrown(),
                context.exceptionHandlers(),
                context.originalPc(),
                context.instructionIndex(),
                context.opcode(),
                context.program(),
                context.frame(),
                context.constants()));
        ib.ifCondition(AdvIBdr.equal(context.handlerPc(), AdvIBdr.constant(-1)), b -> b.gotoLabel(noHandler));
        ib.set(context.frameField(frameLayout.stackPointer), AdvIBdr.constant(0));
        ib.directCall(AdvIBdr.callVirtual(
                context.frame(),
                frameLayout.owner,
                frameLayout.push.name(),
                "V",
                AdvIBdr.cast(context.thrown(), "java/lang/Object")));
        ib.set(context.frameProgramCounter(), context.handlerPc());
        ib.directCall(AdvIBdr.callStatic(
                vmLayout.owner,
                vmLayout.syncState.name(),
                "V",
                context.program(),
                context.frame(),
                context.handlerPc()));
        ib.gotoLabel(batchComplete);

        ib.mark(noHandler, "noHandler");
        ib.throwValue(AdvIBdr.callStatic(
                vmLayout.owner,
                vmLayout.rethrow.name(),
                "java/lang/RuntimeException",
                context.thrown()));

        ib.mark(batchComplete, "batchComplete");
        ib.ifCondition(
                AdvIBdr.isTrue(context.frameReturned()),
                b -> b.returnValue(AdvIBdr.constant(1)));
        ib.increment(batchCount, 1);
        ib.ifCondition(
                AdvIBdr.lessThan(batchCount, AdvIBdr.constant(structureGenerator.stepBatchSize())),
                b -> b.gotoLabel(batchStart));
        ib.returnValue(AdvIBdr.constant(0));
        return methodNode;
    }

    private void emitLoadExceptionHandlers(AdvIBdr ib, InterpretContext context)
    {
        ib.set(context.exceptionHandlers(), AdvIBdr.callVirtual(
                context.program(),
                programLayout.owner,
                programLayout.exceptionHandlers.name(),
                "[I"));
    }

    private void emitKernelDecode(
            AdvIBdr ib,
            InterpretContext context,
            VMKernelShape shape)
    {
        Local decodedNextPc = context.intLocal("decodedNextPc", 84);
        Consumer<AdvIBdr> opcode = code -> {
            if (config.vmStructure == VMStructure.SIMPLE_DISPATCH)
            {
                code.set(context.opcode(), AdvIBdr.callStatic(
                        vmLayout.owner,
                        vmLayout.decodeOpcode.name(),
                        "I",
                        context.program(),
                        context.frame(),
                        context.instructionIndex()));
            }
            else
            {
                emitDecodeOpcodeInline(code, context, context.opcode());
            }
        };
        Consumer<AdvIBdr> next = code -> {
            if (config.vmStructure == VMStructure.SIMPLE_DISPATCH)
            {
                code.set(decodedNextPc, AdvIBdr.callStatic(
                        vmLayout.owner,
                        vmLayout.decodeNextPc.name(),
                        "I",
                        context.program(),
                        context.frame(),
                        context.instructionIndex()));
            }
            else
            {
                emitLayoutValueInline(
                        code,
                        context,
                        decodedNextPc,
                        ProtectedVMMethod.LAYOUT_NEXT_PC,
                        90);
            }
            code.set(context.frameProgramCounter(), decodedNextPc);
        };
        Consumer<AdvIBdr> original = code -> {
            if (config.vmStructure == VMStructure.SIMPLE_DISPATCH)
            {
                code.set(context.originalPc(), AdvIBdr.callStatic(
                        vmLayout.owner,
                        vmLayout.decodeOriginalPc.name(),
                        "I",
                        context.program(),
                        context.frame(),
                        context.instructionIndex()));
            }
            else
            {
                emitLayoutValueInline(
                        code,
                        context,
                        context.originalPc(),
                        ProtectedVMMethod.LAYOUT_ORIGINAL_PC,
                        94);
            }
        };

        switch (shape.decodeOrder())
        {
            case OPCODE_NEXT_ORIGINAL -> emitDecodeOrder(ib, opcode, next, original);
            case OPCODE_ORIGINAL_NEXT -> emitDecodeOrder(ib, opcode, original, next);
            case NEXT_OPCODE_ORIGINAL -> emitDecodeOrder(ib, next, opcode, original);
            case NEXT_ORIGINAL_OPCODE -> emitDecodeOrder(ib, next, original, opcode);
            case ORIGINAL_OPCODE_NEXT -> emitDecodeOrder(ib, original, opcode, next);
            case ORIGINAL_NEXT_OPCODE -> emitDecodeOrder(ib, original, next, opcode);
        }
    }

    @SafeVarargs
    private static void emitDecodeOrder(
            AdvIBdr ib,
            Consumer<AdvIBdr>... decoders)
    {
        for (Consumer<AdvIBdr> decoder : decoders)
        {
            decoder.accept(ib);
        }
    }

    private MethodNode genInterpretMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.interpret.name(),
                vmLayout.interpret.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        InterpretContext context = interpretContext(null);
        ib.set(context.code(), AdvIBdr.callVirtual(
                context.program(),
                programLayout.owner,
                programLayout.opcodeStream.name(),
                "[I"));
        ib.set(context.constants(), AdvIBdr.callVirtual(
                context.program(),
                programLayout.owner,
                programLayout.constants.name(),
                "[Ljava/lang/Object;"));

        if (structurePlan.schedulerKind() == VMStructurePlan.SchedulerKind.SELF_MODIFYING)
        {
            prepareMutableCode(ib, context);
        }

        structureGenerator.emitScheduler(structureGeneration, ib, context);
        ib.returnVoid();
        return method;
    }

    private MethodNode genRegisterReadMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.registerRead.name(),
                vmLayout.registerRead.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local program = ib.getLocal("program", programLayout.owner, 0);
        Local frame = ib.getLocal("frame", frameLayout.owner, 1);
        Local baseStack = ib.getLocal("baseStack", "I", 2);
        Local token = ib.getLocal("token", "I", 3);
        Local encodedOffset = ib.getLocal("encodedOffset", "I", 4);
        Local offset = ib.getLocal("offset", "I", 5);
        Local slot = ib.getLocal("slot", "I", 6);
        Local type = ib.getLocal("type", "I", 7);

        ib.ifCondition(
                AdvIBdr.greaterOrEqual(token, AdvIBdr.constant(0)),
                local -> local.returnValue(AdvIBdr.arrayAt(
                        AdvIBdr.field(frame, frameLayout.locals),
                        token)));
        ib.set(encodedOffset, AdvIBdr.bitAnd(token, AdvIBdr.constant(Integer.MAX_VALUE)));
        ib.set(offset, AdvIBdr.bitXor(
                AdvIBdr.unsignedShiftRight(encodedOffset, AdvIBdr.constant(1)),
                AdvIBdr.minus(
                        AdvIBdr.constant(0),
                        AdvIBdr.bitAnd(encodedOffset, AdvIBdr.constant(1)))));
        ib.set(slot, AdvIBdr.plus(baseStack, offset));
        ib.set(type, AdvIBdr.arrayAt(AdvIBdr.field(frame, frameLayout.stackTypes), slot));

        @SuppressWarnings("unchecked")
        java.util.function.Consumer<AdvIBdr>[] cases = new java.util.function.Consumer[5];
        cases[0] = b -> b.returnValue(AdvIBdr.arrayAt(AdvIBdr.field(frame, frameLayout.stack), slot));
        cases[1] = b -> b.returnValue(NumericType.INT.box(AdvIBdr.cast(
                AdvIBdr.arrayAt(AdvIBdr.field(frame, frameLayout.stackWords), slot), "I")));
        cases[2] = b -> b.returnValue(NumericType.LONG.box(
                AdvIBdr.arrayAt(AdvIBdr.field(frame, frameLayout.stackWords), slot)));
        cases[3] = b -> b.returnValue(NumericType.FLOAT.box(AdvIBdr.callStatic(
                "java/lang/Float",
                "intBitsToFloat",
                "F",
                AdvIBdr.cast(
                        AdvIBdr.arrayAt(AdvIBdr.field(frame, frameLayout.stackWords), slot),
                        "I"))));
        cases[4] = b -> b.returnValue(NumericType.DOUBLE.box(AdvIBdr.callStatic(
                "java/lang/Double",
                "longBitsToDouble",
                "D",
                AdvIBdr.arrayAt(AdvIBdr.field(frame, frameLayout.stackWords), slot))));
        ib.switchTable(
                type,
                0,
                b -> b.returnValue(AdvIBdr.arrayAt(AdvIBdr.field(frame, frameLayout.stack), slot)),
                cases);
        ib.returnValue(AdvIBdr.constant(null));
        return method;
    }

    private MethodNode genRegisterWriteMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.registerWrite.name(),
                vmLayout.registerWrite.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local frame = ib.getLocal("frame", frameLayout.owner, 1);
        Local baseStack = ib.getLocal("baseStack", "I", 2);
        Local token = ib.getLocal("token", "I", 3);
        Local value = ib.getLocal("value", "java/lang/Object", 4);
        Local width = ib.getLocal("width", "I", 5);
        Local encodedOffset = ib.getLocal("encodedOffset", "I", 6);
        Local offset = ib.getLocal("offset", "I", 7);
        Local slot = ib.getLocal("slot", "I", 8);

        ib.ifCondition(
                AdvIBdr.greaterOrEqual(token, AdvIBdr.constant(0)),
                local -> {
                    local.setArray(AdvIBdr.field(frame, frameLayout.locals), token, value);
                    local.returnVoid();
                });
        ib.set(encodedOffset, AdvIBdr.bitAnd(token, AdvIBdr.constant(Integer.MAX_VALUE)));
        ib.set(offset, AdvIBdr.bitXor(
                AdvIBdr.unsignedShiftRight(encodedOffset, AdvIBdr.constant(1)),
                AdvIBdr.minus(
                        AdvIBdr.constant(0),
                        AdvIBdr.bitAnd(encodedOffset, AdvIBdr.constant(1)))));
        ib.set(slot, AdvIBdr.plus(baseStack, offset));
        ib.setArray(AdvIBdr.field(frame, frameLayout.stack), slot, value);
        ib.setArray(AdvIBdr.field(frame, frameLayout.stackTypes), slot, AdvIBdr.constant(0));
        ib.setArray(AdvIBdr.field(frame, frameLayout.stackWidths), slot, width);
        ib.returnVoid();
        return method;
    }

    private MethodNode genExecuteRegisterOpMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.executeRegisterOp.name(),
                vmLayout.executeRegisterOp.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        RegisterOperationContext operation = new RegisterOperationContext(
                ib.getLocal("program", programLayout.owner, 0),
                ib.getLocal("frame", frameLayout.owner, 1),
                ib.getLocal("constants", "[Ljava/lang/Object;", 2),
                ib.getLocal("semantic", "I", 3),
                ib.getLocal("baseStack", "I", 4),
                ib.getLocal("destination", "I", 5),
                ib.getLocal("sourceA", "I", 6),
                ib.getLocal("sourceB", "I", 7),
                ib.getLocal("auxiliary", "I", 8),
                ib.getLocal("width", "I", 9),
                ib.getLocal("instructionIndex", "I", 10),
                ib.getLocal("opcode", "I", 11));

        List<Opcs> operations = new ArrayList<>();
        for (Opcs opcode : Opcs.values())
        {
            if (isLoweredRegisterOpcode(opcode))
            {
                operations.add(opcode);
            }
        }
        emitRegisterDecisionTree(ib, operation, operations, 0, operations.size());
        return method;
    }

    private void emitRegisterDecisionTree(
            AdvIBdr ib,
            RegisterOperationContext operation,
            List<Opcs> operations,
            int from,
            int to)
    {
        if (from >= to)
        {
            ib.throwValue(AdvIBdr.newObject(
                    "java/lang/IllegalStateException",
                    stringConcat(
                            AdvIBdr.constant("Unknown register semantic "),
                            operation.semantic)));
            return;
        }
        int middle = (from + to) >>> 1;
        Opcs opcode = operations.get(middle);
        ib.ifElse(
                AdvIBdr.equal(
                        operation.semantic,
                        AdvIBdr.constant(opcode.ordinal())),
                match -> {
                    emitRegisterOperation(match, operation, opcode);
                    match.returnVoid();
                },
                mismatch -> mismatch.ifElse(
                        AdvIBdr.lessThan(
                                operation.semantic,
                                AdvIBdr.constant(opcode.ordinal())),
                        lower -> emitRegisterDecisionTree(
                                lower,
                                operation,
                                operations,
                                from,
                                middle),
                        higher -> emitRegisterDecisionTree(
                                higher,
                                operation,
                                operations,
                                middle + 1,
                                to)));
    }

    private MethodNode genExecuteDataFlowMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.executeDataFlow.name(),
                vmLayout.executeDataFlow.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local program = ib.getLocal("program", programLayout.owner, 0);
        Local frame = ib.getLocal("frame", frameLayout.owner, 1);
        Local constants = ib.getLocal("constants", "[Ljava/lang/Object;", 2);
        Local payload = ib.getLocal("payload", "[I", 3);
        Local instructionIndex = ib.getLocal("instructionIndex", "I", 4);
        Local opcode = ib.getLocal("opcode", "I", 5);
        Local nodeCount = ib.getLocal("nodeCount", "I", 6);
        Local finalDelta = ib.getLocal("finalDelta", "I", 7);
        Local baseStack = ib.getLocal("baseStack", "I", 8);
        Local doneMask = ib.getLocal("doneMask", "I", 9);
        Local targetMask = ib.getLocal("targetMask", "I", 10);
        Local progress = ib.getLocal("progress", "I", 11);
        Local node = ib.getLocal("node", "I", 12);
        Local offset = ib.getLocal("nodeOffset", "I", 13);
        Local dependencies = ib.getLocal("dependencies", "I", 14);
        Local bit = ib.getLocal("nodeBit", "I", 15);

        ib.set(nodeCount, AdvIBdr.arrayAt(payload, AdvIBdr.constant(0)));
        ib.set(finalDelta, AdvIBdr.arrayAt(payload, AdvIBdr.constant(1)));
        ib.set(baseStack, AdvIBdr.field(frame, frameLayout.stackPointer));
        ib.set(doneMask, AdvIBdr.constant(0));
        ib.set(targetMask, AdvIBdr.minus(
                AdvIBdr.shiftLeft(AdvIBdr.constant(1), nodeCount),
                AdvIBdr.constant(1)));
        ib.whileLoop(
                AdvIBdr.notEqual(doneMask, targetMask),
                schedule -> {
                    schedule.set(progress, AdvIBdr.constant(0));
                    schedule.forLoop(
                            b -> b.set(node, AdvIBdr.constant(0)),
                            AdvIBdr.lessThan(node, nodeCount),
                            b -> b.increment(node, 1),
                            ready -> {
                                ready.set(bit, AdvIBdr.shiftLeft(AdvIBdr.constant(1), node));
                                ready.ifCondition(
                                        AdvIBdr.notEqual(
                                                AdvIBdr.bitAnd(doneMask, bit),
                                                AdvIBdr.constant(0)),
                                        AdvIBdr::continueLoop);
                                ready.set(offset, AdvIBdr.plus(
                                        AdvIBdr.constant(LoweredInstructionPlanner.DATA_FLOW_HEADER_SIZE),
                                        AdvIBdr.multiply(
                                                node,
                                                AdvIBdr.constant(LoweredInstructionPlanner.DATA_FLOW_NODE_SIZE))));
                                ready.set(
                                        dependencies,
                                        AdvIBdr.arrayAt(
                                                payload,
                                                AdvIBdr.plus(
                                                        offset,
                                                        AdvIBdr.constant(LoweredInstructionPlanner.REGISTER_PLAN_SIZE))));
                                ready.ifCondition(
                                        AdvIBdr.notEqual(
                                                AdvIBdr.bitAnd(
                                                        dependencies,
                                                        AdvIBdr.bitXor(doneMask, AdvIBdr.constant(-1))),
                                                AdvIBdr.constant(0)),
                                        AdvIBdr::continueLoop);
                                ready.directCall(AdvIBdr.callStatic(
                                        vmLayout.owner,
                                        vmLayout.executeRegisterOp.name(),
                                        "V",
                                        program,
                                        frame,
                                        constants,
                                        AdvIBdr.arrayAt(payload, offset),
                                        AdvIBdr.plus(
                                                baseStack,
                                                AdvIBdr.arrayAt(
                                                        payload,
                                                        AdvIBdr.plus(offset, AdvIBdr.constant(7)))),
                                        AdvIBdr.arrayAt(payload, AdvIBdr.plus(offset, AdvIBdr.constant(1))),
                                        AdvIBdr.arrayAt(payload, AdvIBdr.plus(offset, AdvIBdr.constant(2))),
                                        AdvIBdr.arrayAt(payload, AdvIBdr.plus(offset, AdvIBdr.constant(3))),
                                        AdvIBdr.arrayAt(payload, AdvIBdr.plus(offset, AdvIBdr.constant(4))),
                                        AdvIBdr.arrayAt(payload, AdvIBdr.plus(offset, AdvIBdr.constant(6))),
                                        instructionIndex,
                                        opcode));
                                ready.set(doneMask, AdvIBdr.bitOr(doneMask, bit));
                                ready.increment(progress, 1);
                            });
                    schedule.ifCondition(
                            AdvIBdr.equal(progress, AdvIBdr.constant(0)),
                            blocked -> blocked.throwValue(AdvIBdr.newObject(
                                    "java/lang/IllegalStateException",
                                    AdvIBdr.constant("Cyclic data-flow region"))));
                });
        ib.set(
                AdvIBdr.field(frame, frameLayout.stackPointer),
                AdvIBdr.plus(baseStack, finalDelta));
        ib.returnVoid();
        return method;
    }

    private boolean isLoweredRegisterOpcode(Opcs opcode)
    {
        return switch (opcode)
        {
            case NOP, ACONST_NULL,
                 ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5,
                 LCONST_0, LCONST_1, FCONST_0, FCONST_1, FCONST_2, DCONST_0, DCONST_1,
                 BIPUSH, SIPUSH, LDC,
                 ILOAD, LLOAD, FLOAD, DLOAD, ALOAD,
                 ISTORE, LSTORE, FSTORE, DSTORE, ASTORE,
                 POP,
                 IADD, LADD, FADD, DADD, ISUB, LSUB, FSUB, DSUB,
                 IMUL, LMUL, FMUL, DMUL, IDIV, LDIV, FDIV, DDIV,
                 IREM, LREM, FREM, DREM,
                 INEG, LNEG, FNEG, DNEG,
                 ISHL, LSHL, ISHR, LSHR, IUSHR, LUSHR,
                 IAND, LAND, IOR, LOR, IXOR, LXOR,
                 IINC,
                 I2L, I2F, I2D, L2I, L2F, L2D, F2I, F2L, F2D,
                 D2I, D2L, D2F, I2B, I2C, I2S,
                 LCMP, FCMPL, FCMPG, DCMPL, DCMPG -> true;
            default -> false;
        };
    }

    private void emitRegisterOperation(AdvIBdr ib, RegisterOperationContext context, Opcs opcode)
    {
        switch (opcode)
        {
            case NOP, POP -> { }
            case ACONST_NULL -> registerWrite(ib, context, AdvIBdr.constant(null));
            case ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5 ->
                    registerWrite(ib, context, NumericType.INT.box(AdvIBdr.constant(opcode.opcode - org.objectweb.asm.Opcodes.ICONST_0)));
            case BIPUSH, SIPUSH -> registerWrite(ib, context, NumericType.INT.box(context.auxiliary));
            case LCONST_0, LCONST_1 -> registerWrite(
                    ib,
                    context,
                    NumericType.LONG.box(AdvIBdr.constant((long) (opcode.opcode - org.objectweb.asm.Opcodes.LCONST_0))));
            case FCONST_0, FCONST_1, FCONST_2 -> registerWrite(
                    ib,
                    context,
                    NumericType.FLOAT.box(AdvIBdr.constant((float) (opcode.opcode - org.objectweb.asm.Opcodes.FCONST_0))));
            case DCONST_0, DCONST_1 -> registerWrite(
                    ib,
                    context,
                    NumericType.DOUBLE.box(AdvIBdr.constant((double) (opcode.opcode - org.objectweb.asm.Opcodes.DCONST_0))));
            case LDC -> {
                Local constant = ib.var("registerConstant", "java/lang/Object");
                ib.set(constant, AdvIBdr.callStatic(
                        vmLayout.owner,
                        vmLayout.resolveConstant.name(),
                        "java/lang/Object",
                        context.program,
                        AdvIBdr.arrayAt(context.constants, context.auxiliary),
                        context.frame,
                        context.instructionIndex,
                        context.opcode));
                ib.ifElse(
                        AdvIBdr.or(
                                AdvIBdr.isInstanceOf(constant, "java/lang/Long"),
                                AdvIBdr.isInstanceOf(constant, "java/lang/Double")),
                        category2 -> registerWrite(category2, context, constant, AdvIBdr.constant(2)),
                        category1 -> registerWrite(category1, context, constant, AdvIBdr.constant(1)));
            }
            case ILOAD, LLOAD, FLOAD, DLOAD, ALOAD,
                 ISTORE, LSTORE, FSTORE, DSTORE, ASTORE -> registerWrite(
                    ib,
                    context,
                    registerRead(context, context.sourceA));
            case IADD, LADD, FADD, DADD, ISUB, LSUB, FSUB, DSUB,
                 IMUL, LMUL, FMUL, DMUL, IDIV, LDIV, FDIV, DDIV,
                 IREM, LREM, FREM, DREM,
                 IAND, LAND, IOR, LOR, IXOR, LXOR -> emitRegisterBinary(ib, context, opcode);
            case ISHL, LSHL, ISHR, LSHR, IUSHR, LUSHR -> emitRegisterShift(ib, context, opcode);
            case INEG, LNEG, FNEG, DNEG -> emitRegisterNegate(ib, context, opcode);
            case IINC -> registerWrite(
                    ib,
                    context,
                    NumericType.INT.box(AdvIBdr.plus(
                            NumericType.INT.unbox(registerRead(context, context.sourceA)),
                            context.auxiliary)));
            case I2L, I2F, I2D, L2I, L2F, L2D, F2I, F2L, F2D,
                 D2I, D2L, D2F, I2B, I2C, I2S -> emitRegisterConversion(ib, context, opcode);
            case LCMP, FCMPL, FCMPG, DCMPL, DCMPG -> emitRegisterCompare(ib, context, opcode);
            default -> throw new IllegalArgumentException("Not a lowered register opcode: " + opcode);
        }
    }

    private void emitRegisterBinary(AdvIBdr ib, RegisterOperationContext context, Opcs opcode)
    {
        NumericType type = NumericType.fromOpcode(opcode);
        Local left = ib.var("registerLeft" + opcode, type.descriptor());
        Local right = ib.var("registerRight" + opcode, type.descriptor());
        ib.set(left, type.unbox(registerRead(context, context.sourceA)));
        ib.set(right, type.unbox(registerRead(context, context.sourceB)));
        Expr value = switch (opcode)
        {
            case IADD, LADD, FADD, DADD -> AdvIBdr.plus(left, right);
            case ISUB, LSUB, FSUB, DSUB -> AdvIBdr.minus(left, right);
            case IMUL, LMUL, FMUL, DMUL -> AdvIBdr.multiply(left, right);
            case IDIV, LDIV, FDIV, DDIV -> AdvIBdr.divide(left, right);
            case IREM, LREM, FREM, DREM -> AdvIBdr.remainder(left, right);
            case IAND, LAND -> AdvIBdr.bitAnd(left, right);
            case IOR, LOR -> AdvIBdr.bitOr(left, right);
            case IXOR, LXOR -> AdvIBdr.bitXor(left, right);
            default -> throw new IllegalArgumentException("Not a register binary opcode: " + opcode);
        };
        registerWrite(ib, context, type.box(value));
    }

    private void emitRegisterShift(AdvIBdr ib, RegisterOperationContext context, Opcs opcode)
    {
        NumericType type = opcode.name().charAt(0) == 'L' ? NumericType.LONG : NumericType.INT;
        Local left = ib.var("registerShiftLeft" + opcode, type.descriptor());
        Local right = ib.var("registerShiftRight" + opcode, "I");
        ib.set(left, type.unbox(registerRead(context, context.sourceA)));
        ib.set(right, NumericType.INT.unbox(registerRead(context, context.sourceB)));
        Expr value = switch (opcode)
        {
            case ISHL, LSHL -> AdvIBdr.shiftLeft(left, right);
            case ISHR, LSHR -> AdvIBdr.shiftRight(left, right);
            case IUSHR, LUSHR -> AdvIBdr.unsignedShiftRight(left, right);
            default -> throw new IllegalArgumentException("Not a register shift opcode: " + opcode);
        };
        registerWrite(ib, context, type.box(value));
    }

    private void emitRegisterNegate(AdvIBdr ib, RegisterOperationContext context, Opcs opcode)
    {
        NumericType type = NumericType.fromOpcode(opcode);
        registerWrite(
                ib,
                context,
                type.box(AdvIBdr.minus(
                        AdvIBdr.constant(switch (type)
                        {
                            case INT -> 0;
                            case LONG -> 0L;
                            case FLOAT -> 0.0F;
                            case DOUBLE -> 0.0D;
                        }),
                        type.unbox(registerRead(context, context.sourceA)))));
    }

    private void emitRegisterConversion(AdvIBdr ib, RegisterOperationContext context, Opcs opcode)
    {
        NumericType source = switch (opcode.name().charAt(0))
        {
            case 'I' -> NumericType.INT;
            case 'L' -> NumericType.LONG;
            case 'F' -> NumericType.FLOAT;
            case 'D' -> NumericType.DOUBLE;
            default -> throw new IllegalArgumentException("Not a conversion opcode: " + opcode);
        };
        NumericType target = switch (opcode.name().charAt(2))
        {
            case 'L' -> NumericType.LONG;
            case 'F' -> NumericType.FLOAT;
            case 'D' -> NumericType.DOUBLE;
            default -> NumericType.INT;
        };
        Expr sourceValue = source.unbox(registerRead(context, context.sourceA));
        Expr converted = switch (opcode)
        {
            case I2L, F2L, D2L -> AdvIBdr.toLong(sourceValue);
            case I2F, L2F, D2F -> AdvIBdr.toFloat(sourceValue);
            case I2D, L2D, F2D -> AdvIBdr.toDouble(sourceValue);
            case L2I, F2I, D2I -> AdvIBdr.toInt(sourceValue);
            case I2B -> AdvIBdr.shiftRight(
                    AdvIBdr.shiftLeft(sourceValue, AdvIBdr.constant(24)),
                    AdvIBdr.constant(24));
            case I2C -> AdvIBdr.bitAnd(sourceValue, AdvIBdr.constant(0xffff));
            case I2S -> AdvIBdr.shiftRight(
                    AdvIBdr.shiftLeft(sourceValue, AdvIBdr.constant(16)),
                    AdvIBdr.constant(16));
            default -> throw new IllegalArgumentException("Not a conversion opcode: " + opcode);
        };
        registerWrite(
                ib,
                context,
                target.box(target == NumericType.INT ? AdvIBdr.cast(converted, "I") : converted));
    }

    private void emitRegisterCompare(AdvIBdr ib, RegisterOperationContext context, Opcs opcode)
    {
        NumericType type = NumericType.fromOpcode(opcode);
        Local left = ib.var("registerCompareLeft" + opcode, type.descriptor());
        Local right = ib.var("registerCompareRight" + opcode, type.descriptor());
        Local result = ib.var("registerCompareResult" + opcode, "I");
        ib.set(left, type.unbox(registerRead(context, context.sourceA)));
        ib.set(right, type.unbox(registerRead(context, context.sourceB)));
        if (opcode == Opcs.LCMP)
        {
            emitOrderedRegisterCompare(ib, left, right, result);
        }
        else
        {
            boolean greaterOnNaN = opcode == Opcs.FCMPG || opcode == Opcs.DCMPG;
            Expr leftNaN = AdvIBdr.callStatic(
                    type == NumericType.FLOAT ? "java/lang/Float" : "java/lang/Double",
                    "isNaN",
                    "Z",
                    left);
            Expr rightNaN = AdvIBdr.callStatic(
                    type == NumericType.FLOAT ? "java/lang/Float" : "java/lang/Double",
                    "isNaN",
                    "Z",
                    right);
            ib.ifElse(
                    AdvIBdr.or(AdvIBdr.isTrue(leftNaN), AdvIBdr.isTrue(rightNaN)),
                    nan -> nan.set(result, AdvIBdr.constant(greaterOnNaN ? 1 : -1)),
                    ordered -> emitOrderedRegisterCompare(ordered, left, right, result));
        }
        registerWrite(ib, context, NumericType.INT.box(result));
    }

    private void emitOrderedRegisterCompare(AdvIBdr ib, Expr left, Expr right, Local result)
    {
        ib.ifElse(
                AdvIBdr.greaterThan(left, right),
                greater -> greater.set(result, AdvIBdr.constant(1)),
                other -> other.ifElse(
                        AdvIBdr.equal(left, right),
                        equal -> equal.set(result, AdvIBdr.constant(0)),
                        less -> less.set(result, AdvIBdr.constant(-1))));
    }

    private Expr registerRead(RegisterOperationContext context, Expr token)
    {
        return AdvIBdr.callStatic(
                vmLayout.owner,
                vmLayout.registerRead.name(),
                "java/lang/Object",
                context.program,
                context.frame,
                context.baseStack,
                token);
    }

    private void registerWrite(AdvIBdr ib, RegisterOperationContext context, Expr value)
    {
        registerWrite(ib, context, value, context.width);
    }

    private void registerWrite(AdvIBdr ib, RegisterOperationContext context, Expr value, Expr width)
    {
        ib.directCall(AdvIBdr.callStatic(
                vmLayout.owner,
                vmLayout.registerWrite.name(),
                "V",
                context.program,
                context.frame,
                context.baseStack,
                context.destination,
                AdvIBdr.cast(value, "java/lang/Object"),
                width));
    }

    private record RegisterOperationContext(
            Local program,
            Local frame,
            Local constants,
            Local semantic,
            Local baseStack,
            Local destination,
            Local sourceA,
            Local sourceB,
            Local auxiliary,
            Local width,
            Local instructionIndex,
            Local opcode)
    {
    }

    private void prepareMutableCode(AdvIBdr ib, InterpretContext context)
    {
        ib.ifCondition(
                AdvIBdr.notEqual(
                        context.frameField(frameLayout.mutableProgram),
                        context.program()),
                initialize -> {
                    initialize.set(
                            context.frameField(frameLayout.mutableCode),
                            AdvIBdr.callStatic(
                                    "java/util/Arrays",
                                    "copyOf",
                                    "[I",
                                    context.code(),
                                    AdvIBdr.arrayLength(context.code())));
                    initialize.set(
                            context.frameField(frameLayout.mutableMasks),
                            AdvIBdr.newArray("int", AdvIBdr.arrayLength(context.code())));
                    initialize.set(context.frameField(frameLayout.mutableProgram), context.program());
                });
    }

    private void emitSelfMutation(AdvIBdr ib, InterpretContext context)
    {
        Local mutation = context.intLocal("mutation", InterpretContext.DISPATCH_SELECTOR + 5);
        ib.set(mutation, mixCall(
                context.frameStateKey(),
                context.instructionIndex(),
                context.opcode(),
                AdvIBdr.constant(profile.saltHandler)));
        ib.setArray(
                context.frameField(frameLayout.mutableCode),
                context.instructionIndex(),
                AdvIBdr.bitXor(
                        AdvIBdr.arrayAt(context.frameField(frameLayout.mutableCode), context.instructionIndex()),
                        mutation));
        ib.setArray(
                context.frameField(frameLayout.mutableMasks),
                context.instructionIndex(),
                AdvIBdr.bitXor(
                        AdvIBdr.arrayAt(context.frameField(frameLayout.mutableMasks), context.instructionIndex()),
                        mutation));
    }

    private Expr stepCall(InterpretContext context, Expr structureState)
    {
        List<Expr> arguments = new ArrayList<>();
        arguments.add(context.program());
        arguments.add(context.frame());
        arguments.add(context.code());
        arguments.add(context.constants());
        arguments.add(structureState);
        if (config.vmStructure != VMStructure.SIMPLE_DISPATCH)
        {
            int shape = structurePlan.structure().ordinal();
            for (int bit = 0; bit < 5; bit++)
            {
                arguments.add((shape & 1 << bit) == 0
                        ? AdvIBdr.constant(profile.decodeVariant + bit)
                        : AdvIBdr.constant(
                                ((long) profile.decodeVariant << 32) ^
                                (0x94D049BB133111EBL + bit)));
            }
        }
        return AdvIBdr.callStatic(
                className(),
                vmLayout.interpretStep.name(),
                "I",
                arguments.toArray(Expr[]::new));
    }

    private Expr graphNode(InterpretContext context)
    {
        return mixCall(
                context.frameProgramCounter(),
                context.frameField(frameLayout.blockIndex),
                context.frameStateKey(),
                AdvIBdr.constant(profile.saltBlock));
    }

    private Expr fsmState(InterpretContext context, Expr previousState)
    {
        return AdvIBdr.bitXor(
                mixCall(
                        context.frameProgramCounter(),
                        context.frameField(frameLayout.blockIndex),
                        previousState,
                        AdvIBdr.constant(profile.saltState)),
                AdvIBdr.constant(profile.saltState));
    }

    private Expr eventToken(InterpretContext context)
    {
        return mixCall(
                context.frameStateKey(),
                context.frameProgramCounter(),
                context.frameField(frameLayout.blockIndex),
                AdvIBdr.constant(profile.saltHandler));
    }

    private String schedulerDescriptor(boolean depth)
    {
        return "(" +
                vmProgramGenerator.descriptor() +
                methodFrameGenerator.descriptor() +
                "[I[Ljava/lang/Object;" +
                (depth ? "I" : "") +
                ")I";
    }

    private String coroutineDescriptor()
    {
        return "(" +
                vmProgramGenerator.descriptor() +
                methodFrameGenerator.descriptor() +
                "[I[Ljava/lang/Object;[I)I";
    }

    private MethodNode genInstructionIndexMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, vmLayout.instructionIndex.name(), vmLayout.instructionIndex.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local program = ib.getLocal("program", programLayout.owner, 0);
        Local frame = ib.getLocal("frame", frameLayout.owner, 1);
        Local pc = ib.getLocal("pc", "I", 2);
        Local index = ib.getLocal("index", "I", 3);
        Local block = ib.getLocal("block", "I", 4);
        Local blockCount = ib.getLocal("blockCount", "I", 5);

        ib.set(block, AdvIBdr.field(frame, frameLayout.blockIndex));
        ib.set(index, AdvIBdr.callStatic(
                vmLayout.owner,
                vmLayout.instructionIndexInBlock.name(),
                "I",
                program,
                block,
                pc));
        ib.ifCondition(
                AdvIBdr.notEqual(index, AdvIBdr.constant(-1)),
                found -> found.returnValue(index));

        ib.set(blockCount, AdvIBdr.divide(
                AdvIBdr.arrayLength(callProgramArray(program, programLayout.blockStream.name())),
                AdvIBdr.constant(ProtectedVMMethod.BLOCK_SIZE)));
        ib.forLoop(
                b -> b.set(block, AdvIBdr.constant(0)),
                AdvIBdr.lessThan(block, blockCount),
                b -> b.increment(block, 1),
                b -> {
                    b.set(index, AdvIBdr.callStatic(
                            vmLayout.owner,
                            vmLayout.instructionIndexInBlock.name(),
                            "I",
                            program,
                            block,
                            pc));
                    b.ifCondition(
                            AdvIBdr.notEqual(index, AdvIBdr.constant(-1)),
                            found -> {
                                found.set(AdvIBdr.field(frame, frameLayout.blockIndex), block);
                                found.set(AdvIBdr.field(frame, frameLayout.stateKey), AdvIBdr.callStatic(
                                        vmLayout.owner,
                                        vmLayout.stateKey.name(),
                                        "I",
                                        program,
                                        index));
                                found.returnValue(index);
                            });
                });
        ib.returnValue(AdvIBdr.constant(-1));
        return method;
    }

    private MethodNode genDecodeOpcodeMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, vmLayout.decodeOpcode.name(), vmLayout.decodeOpcode.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local program = ib.getLocal("program", programLayout.owner, 0);
        Local frame = ib.getLocal("frame", frameLayout.owner, 1);
        Local index = ib.getLocal("instructionIndex", "I", 2);
        Local key = ib.getLocal("methodKey", "I", 3);
        Local stateKey = ib.getLocal("stateKey", "I", 4);
        Local virtualPc = ib.getLocal("virtualPc", "I", 5);
        Local virtualOpcode = ib.getLocal("virtualOpcode", "I", 6);
        Local mappedOpcode = ib.getLocal("mappedOpcode", "I", 7);

        ib.set(key, callProgramInt(program, programLayout.methodKey.name()));
        ib.set(stateKey, AdvIBdr.field(frame, frameLayout.stateKey));
        ib.set(virtualPc, layoutValue(program, index, ProtectedVMMethod.LAYOUT_PC, stateKey));
        if (structurePlan.schedulerKind() == VMStructurePlan.SchedulerKind.SELF_MODIFYING)
        {
            ib.set(virtualOpcode, AdvIBdr.bitXor(
                    AdvIBdr.arrayAt(AdvIBdr.field(frame, frameLayout.mutableCode), index),
                    AdvIBdr.arrayAt(AdvIBdr.field(frame, frameLayout.mutableMasks), index)));
        }
        else
        {
            ib.set(virtualOpcode, AdvIBdr.arrayAt(callProgramArray(program, programLayout.opcodeStream.name()), index));
        }
        ib.ifCondition(
                AdvIBdr.and(
                        AdvIBdr.notEqual(key, AdvIBdr.constant(0)),
                        featureEnabled(program, ProtectedVMMethod.FEATURE_PER_METHOD_OPCODE_MAP)),
                b -> b.set(virtualOpcode, AdvIBdr.bitXor(
                        virtualOpcode,
                        mixCall(
                                AdvIBdr.bitXor(key, stateKey),
                                virtualPc,
                                index,
                                AdvIBdr.constant(profile.saltOpcode)))));
        ib.set(mappedOpcode, AdvIBdr.arrayAt(callProgramArray(program, programLayout.opcodeMap.name()), virtualOpcode));
        ib.ifCondition(
                AdvIBdr.and(
                        AdvIBdr.notEqual(key, AdvIBdr.constant(0)),
                        featureEnabled(program, ProtectedVMMethod.FEATURE_PER_METHOD_OPCODE_MAP)),
                b -> b.set(mappedOpcode, AdvIBdr.bitXor(
                        mappedOpcode,
                        mixCall(
                                key,
                                virtualOpcode,
                                AdvIBdr.constant(profile.saltOpcodeMap),
                                AdvIBdr.constant(0)))));
        ib.returnValue(mappedOpcode);
        return method;
    }

    private MethodNode genDecodeNextPcMethod()
    {
        return genDecodeLayoutFieldMethod(vmLayout.decodeNextPc.name(), vmLayout.decodeNextPc.descriptor(), ProtectedVMMethod.LAYOUT_NEXT_PC);
    }

    private MethodNode genDecodeOriginalPcMethod()
    {
        return genDecodeLayoutFieldMethod(vmLayout.decodeOriginalPc.name(), vmLayout.decodeOriginalPc.descriptor(), ProtectedVMMethod.LAYOUT_ORIGINAL_PC);
    }

    private MethodNode genDecodeLayoutFieldMethod(String name, String descriptor, int field)
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, name, descriptor);
        AdvIBdr ib = new AdvIBdr(method);
        Local program = ib.getLocal("program", programLayout.owner, 0);
        Local frame = ib.getLocal("frame", frameLayout.owner, 1);
        Local index = ib.getLocal("instructionIndex", "I", 2);
        ib.returnValue(layoutValue(
                program,
                index,
                field,
                AdvIBdr.field(frame, frameLayout.stateKey)));
        return method;
    }

    private MethodNode genDecodeOperandMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, vmLayout.decodeOperand.name(), vmLayout.decodeOperand.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local program = ib.getLocal("program", programLayout.owner, 0);
        Local frame = ib.getLocal("frame", frameLayout.owner, 1);
        Local instructionIndex = ib.getLocal("instructionIndex", "I", 2);
        Local operandIndex = ib.getLocal("operandIndex", "I", 3);
        Local opcode = ib.getLocal("opcode", "I", 4);
        Local key = ib.getLocal("methodKey", "I", 5);
        Local stateKey = ib.getLocal("stateKey", "I", 6);
        Local virtualPc = ib.getLocal("virtualPc", "I", 7);
        Local operandStart = ib.getLocal("operandStart", "I", 8);
        Local operandCount = ib.getLocal("operandCount", "I", 9);
        Local operandPosition = ib.getLocal("operandPosition", "I", 10);
        Local constantMask = ib.getLocal("constantMask", "I", 11);
        Local value = ib.getLocal("value", "I", 12);

        ib.set(key, callProgramInt(program, programLayout.methodKey.name()));
        ib.set(stateKey, AdvIBdr.field(frame, frameLayout.stateKey));
        ib.set(virtualPc, layoutValue(program, instructionIndex, ProtectedVMMethod.LAYOUT_PC, stateKey));
        ib.set(operandStart, layoutValue(program, instructionIndex, ProtectedVMMethod.LAYOUT_OPERAND_START, stateKey));
        ib.set(operandCount, layoutValue(program, instructionIndex, ProtectedVMMethod.LAYOUT_OPERAND_COUNT, stateKey));
        ib.ifCondition(
                AdvIBdr.greaterOrEqual(operandIndex, operandCount),
                b -> b.throwValue(AdvIBdr.newObject(
                        "java/lang/IllegalStateException",
                        stringConcat(AdvIBdr.constant("Operand out of range "), operandIndex))));
        ib.set(operandPosition, AdvIBdr.plus(operandStart, operandIndex));
        ib.set(value, AdvIBdr.arrayAt(callProgramArray(program, programLayout.operandStream.name()), operandPosition));
        ib.ifCondition(
                AdvIBdr.and(
                        AdvIBdr.notEqual(key, AdvIBdr.constant(0)),
                        featureEnabled(program, ProtectedVMMethod.FEATURE_ENCRYPT_OPERANDS)),
                b -> b.set(value, AdvIBdr.bitXor(
                        value,
                        mixCall(
                                AdvIBdr.bitXor(AdvIBdr.bitXor(key, stateKey), opcode),
                                virtualPc,
                                operandIndex,
                                AdvIBdr.bitXor(AdvIBdr.constant(profile.saltOperand), operandPosition)))));
        ib.ifCondition(
                AdvIBdr.and(
                        AdvIBdr.notEqual(key, AdvIBdr.constant(0)),
                        featureEnabled(program, ProtectedVMMethod.FEATURE_BIND_CONSTANTS)),
                b -> {
                    b.set(constantMask, layoutValue(
                            program,
                            instructionIndex,
                            ProtectedVMMethod.LAYOUT_CONSTANT_MASK,
                            stateKey));
                    b.ifCondition(
                            AdvIBdr.notEqual(
                                    AdvIBdr.bitAnd(constantMask, AdvIBdr.shiftLeft(AdvIBdr.constant(1), operandIndex)),
                                    AdvIBdr.constant(0)),
                            constant -> constant.set(value, AdvIBdr.bitXor(
                                    value,
                                    mixCall(
                                            AdvIBdr.bitXor(AdvIBdr.bitXor(key, stateKey), opcode),
                                            virtualPc,
                                            operandIndex,
                                            AdvIBdr.constant(profile.saltConstant)))));
                });
        ib.returnValue(value);
        return method;
    }

    private void emitDecodeOpcodeInline(AdvIBdr ib, InterpretContext context, Local target)
    {
        Local key = context.intLocal("opcodeMethodKey", 100);
        Local stateKey = context.intLocal("opcodeStateKey", 101);
        Local virtualPc = context.intLocal("opcodeVirtualPc", 102);
        Local virtualOpcode = context.intLocal("encodedOpcode", 103);
        Local mappedOpcode = context.intLocal("mappedOpcode", 104);

        ib.set(key, callProgramInt(context.program(), programLayout.methodKey.name()));
        ib.set(stateKey, context.frameStateKey());
        emitLayoutValueInline(
                ib,
                context,
                virtualPc,
                ProtectedVMMethod.LAYOUT_PC,
                105);
        if (structurePlan.schedulerKind() == VMStructurePlan.SchedulerKind.SELF_MODIFYING)
        {
            ib.set(virtualOpcode, AdvIBdr.bitXor(
                    AdvIBdr.arrayAt(
                            AdvIBdr.field(context.frame(), frameLayout.mutableCode),
                            context.instructionIndex()),
                    AdvIBdr.arrayAt(
                            AdvIBdr.field(context.frame(), frameLayout.mutableMasks),
                            context.instructionIndex())));
        }
        else
        {
            ib.set(virtualOpcode, AdvIBdr.arrayAt(
                    callProgramArray(context.program(), programLayout.opcodeStream.name()),
                    context.instructionIndex()));
        }
        ib.ifCondition(
                AdvIBdr.and(
                        AdvIBdr.notEqual(key, AdvIBdr.constant(0)),
                        featureEnabled(context.program(), ProtectedVMMethod.FEATURE_PER_METHOD_OPCODE_MAP)),
                decode -> decode.set(virtualOpcode, structureXorDecode(
                        virtualOpcode,
                        mixCall(
                                AdvIBdr.bitXor(key, stateKey),
                                virtualPc,
                                context.instructionIndex(),
                                AdvIBdr.constant(profile.saltOpcode)),
                        profile.saltOpcode)));
        ib.set(mappedOpcode, AdvIBdr.arrayAt(
                callProgramArray(context.program(), programLayout.opcodeMap.name()),
                virtualOpcode));
        ib.ifCondition(
                AdvIBdr.and(
                        AdvIBdr.notEqual(key, AdvIBdr.constant(0)),
                        featureEnabled(context.program(), ProtectedVMMethod.FEATURE_PER_METHOD_OPCODE_MAP)),
                decode -> decode.set(mappedOpcode, structureXorDecode(
                        mappedOpcode,
                        mixCall(
                                key,
                                virtualOpcode,
                                AdvIBdr.constant(profile.saltOpcodeMap),
                                AdvIBdr.constant(0)),
                        profile.saltOpcodeMap)));
        ib.set(target, mappedOpcode);
    }

    private void emitDecodeOperandInline(
            AdvIBdr ib,
            InterpretContext context,
            Local target)
    {
        emitDecodeOperandInline(ib, context, target, 0);
    }

    private void emitDecodeOperandInline(
            AdvIBdr ib,
            InterpretContext context,
            Local target,
            int decoderSite)
    {
        Local key = context.intLocal("operandMethodKey", 120);
        Local stateKey = context.intLocal("operandStateKey", 121);
        Local virtualPc = context.intLocal("operandVirtualPc", 122);
        Local operandStart = context.intLocal("operandStart", 123);
        Local operandCount = context.intLocal("operandCount", 124);
        Local operandPosition = context.intLocal("operandPosition", 125);
        Local constantMask = context.intLocal("operandConstantMask", 126);
        Local value = context.intLocal("encodedOperand", 127);

        ib.set(key, callProgramInt(context.program(), programLayout.methodKey.name()));
        ib.set(stateKey, context.frameStateKey());
        emitLayoutValueInline(
                ib,
                context,
                virtualPc,
                ProtectedVMMethod.LAYOUT_PC,
                128);
        emitLayoutValueInline(
                ib,
                context,
                operandStart,
                ProtectedVMMethod.LAYOUT_OPERAND_START,
                132);
        emitLayoutValueInline(
                ib,
                context,
                operandCount,
                ProtectedVMMethod.LAYOUT_OPERAND_COUNT,
                136);
        ib.ifCondition(
                AdvIBdr.greaterOrEqual(context.operandIndex(), operandCount),
                outOfRange -> outOfRange.throwValue(AdvIBdr.newObject(
                        "java/lang/IllegalStateException",
                        stringConcat(
                                AdvIBdr.constant("Operand out of range "),
                                context.operandIndex()))));
        ib.set(operandPosition, AdvIBdr.plus(operandStart, context.operandIndex()));
        ib.set(value, AdvIBdr.arrayAt(
                callProgramArray(context.program(), programLayout.operandStream.name()),
                operandPosition));
        ib.ifCondition(
                AdvIBdr.and(
                        AdvIBdr.notEqual(key, AdvIBdr.constant(0)),
                        featureEnabled(context.program(), ProtectedVMMethod.FEATURE_ENCRYPT_OPERANDS)),
                decode -> decode.set(value, structureXorDecode(
                        value,
                        mixCall(
                                AdvIBdr.bitXor(
                                        AdvIBdr.bitXor(key, stateKey),
                                        context.opcode()),
                                virtualPc,
                                context.operandIndex(),
                                AdvIBdr.bitXor(
                                        AdvIBdr.constant(profile.saltOperand),
                                        operandPosition)),
                        profile.saltOperand ^ decoderSite)));
        ib.ifCondition(
                AdvIBdr.and(
                        AdvIBdr.notEqual(key, AdvIBdr.constant(0)),
                        featureEnabled(context.program(), ProtectedVMMethod.FEATURE_BIND_CONSTANTS)),
                bound -> {
                    emitLayoutValueInline(
                            bound,
                            context,
                            constantMask,
                            ProtectedVMMethod.LAYOUT_CONSTANT_MASK,
                            140);
                    bound.ifCondition(
                            AdvIBdr.notEqual(
                                    AdvIBdr.bitAnd(
                                            constantMask,
                                            AdvIBdr.shiftLeft(
                                                    AdvIBdr.constant(1),
                                                    context.operandIndex())),
                                    AdvIBdr.constant(0)),
                            decode -> decode.set(value, structureXorDecode(
                                    value,
                                    mixCall(
                                            AdvIBdr.bitXor(
                                                    AdvIBdr.bitXor(key, stateKey),
                                                    context.opcode()),
                                            virtualPc,
                                            context.operandIndex(),
                                            AdvIBdr.constant(profile.saltConstant)),
                                    profile.saltConstant ^ decoderSite)));
                });
        ib.set(target, value);
    }

    private void emitLayoutValueInline(
            AdvIBdr ib,
            InterpretContext context,
            Local target,
            int field,
            int scratchBase)
    {
        Local key = context.intLocal("layoutKey" + scratchBase, scratchBase);
        Local raw = context.intLocal("layoutRaw" + scratchBase, scratchBase + 1);
        ib.set(key, callProgramInt(context.program(), programLayout.methodKey.name()));
        ib.set(raw, AdvIBdr.arrayAt(
                callProgramArray(context.program(), programLayout.layoutStream.name()),
                AdvIBdr.plus(
                        AdvIBdr.multiply(
                                context.instructionIndex(),
                                AdvIBdr.constant(ProtectedVMMethod.RECORD_SIZE)),
                        AdvIBdr.constant(profile.layoutSlot(field)))));
        ib.set(target, raw);
        ib.ifCondition(
                AdvIBdr.notEqual(key, AdvIBdr.constant(0)),
                decode -> decode.set(target, structureXorDecode(
                        raw,
                        mixCall(
                                AdvIBdr.bitXor(key, context.frameStateKey()),
                                context.instructionIndex(),
                                AdvIBdr.constant(profile.layoutSlot(field)),
                                AdvIBdr.constant(profile.saltLayout)),
                        profile.saltLayout ^ profile.layoutSlot(field))));
    }

    private Expr structureXorDecode(Expr value, Expr mask, int siteSalt)
    {
        return switch ((profile.decodeVariant ^ structurePlan.structure().ordinal() ^ siteSalt) & 3)
        {
            case 0 -> AdvIBdr.bitXor(value, mask);
            case 1 -> AdvIBdr.bitXor(
                    AdvIBdr.bitXor(value, AdvIBdr.constant(siteSalt)),
                    AdvIBdr.bitXor(mask, AdvIBdr.constant(siteSalt)));
            case 2 -> AdvIBdr.bitNot(AdvIBdr.bitXor(
                    AdvIBdr.bitNot(value),
                    mask));
            default -> AdvIBdr.bitNot(AdvIBdr.bitXor(
                    value,
                    AdvIBdr.bitNot(mask)));
        };
    }

    private MethodNode genLayoutValueMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, vmLayout.layoutValue.name(), vmLayout.layoutValue.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local program = ib.getLocal("program", programLayout.owner, 0);
        Local instructionIndex = ib.getLocal("instructionIndex", "I", 1);
        Local field = ib.getLocal("field", "I", 2);
        Local stateKey = ib.getLocal("stateKey", "I", 3);
        Local key = ib.getLocal("methodKey", "I", 4);
        Local raw = ib.getLocal("raw", "I", 5);

        ib.set(key, callProgramInt(program, programLayout.methodKey.name()));
        ib.ifCondition(
                AdvIBdr.equal(
                        field,
                        AdvIBdr.constant(profile.layoutSlot(ProtectedVMMethod.LAYOUT_STATE_KEY))),
                b -> b.returnValue(AdvIBdr.callStatic(
                        vmLayout.owner,
                        vmLayout.stateKey.name(),
                        "I",
                        program,
                        instructionIndex)));
        ib.set(raw, AdvIBdr.arrayAt(
                callProgramArray(program, programLayout.layoutStream.name()),
                AdvIBdr.plus(
                        AdvIBdr.multiply(instructionIndex, AdvIBdr.constant(ProtectedVMMethod.RECORD_SIZE)),
                        field)));
        ib.ifCondition(AdvIBdr.equal(key, AdvIBdr.constant(0)), b -> b.returnValue(raw));
        ib.returnValue(AdvIBdr.bitXor(
                raw,
                mixCall(
                        AdvIBdr.bitXor(key, stateKey),
                        instructionIndex,
                        field,
                        AdvIBdr.constant(profile.saltLayout))));
        return method;
    }

    private MethodNode genBlockValueMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, vmLayout.blockValue.name(), vmLayout.blockValue.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local program = ib.getLocal("program", programLayout.owner, 0);
        Local blockIndex = ib.getLocal("blockIndex", "I", 1);
        Local field = ib.getLocal("field", "I", 2);
        Local key = ib.getLocal("methodKey", "I", 3);
        Local raw = ib.getLocal("raw", "I", 4);

        ib.set(key, callProgramInt(program, programLayout.methodKey.name()));
        ib.set(raw, AdvIBdr.arrayAt(
                callProgramArray(program, programLayout.blockStream.name()),
                AdvIBdr.plus(
                        AdvIBdr.multiply(blockIndex, AdvIBdr.constant(ProtectedVMMethod.BLOCK_SIZE)),
                        field)));
        ib.ifCondition(AdvIBdr.equal(key, AdvIBdr.constant(0)), b -> b.returnValue(raw));
        ib.returnValue(AdvIBdr.bitXor(
                raw,
                mixCall(
                        key,
                        blockIndex,
                        field,
                        AdvIBdr.constant(profile.saltBlock))));
        return method;
    }

    private MethodNode genStateKeyMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, vmLayout.stateKey.name(), vmLayout.stateKey.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local program = ib.getLocal("program", programLayout.owner, 0);
        Local instructionIndex = ib.getLocal("instructionIndex", "I", 1);
        Local key = ib.getLocal("methodKey", "I", 2);
        Local raw = ib.getLocal("raw", "I", 3);
        Local blockCount = ib.getLocal("blockCount", "I", 4);
        Local blockIndex = ib.getLocal("blockIndex", "I", 5);
        Local firstSlot = ib.getLocal("firstSlot", "I", 6);
        Local slotCount = ib.getLocal("slotCount", "I", 7);
        Local slot = ib.getLocal("stateSlot", "I", 8);
        Local current = ib.getLocal("currentStateKey", "I", 9);

        ib.set(key, callProgramInt(program, programLayout.methodKey.name()));
        ib.set(raw, stateKeyCipherAt(program, instructionIndex));
        ib.ifCondition(
                AdvIBdr.equal(key, AdvIBdr.constant(0)),
                plain -> plain.returnValue(raw));
        ib.set(blockCount, AdvIBdr.divide(
                AdvIBdr.arrayLength(callProgramArray(program, programLayout.blockStream.name())),
                AdvIBdr.constant(ProtectedVMMethod.BLOCK_SIZE)));
        ib.forLoop(
                loop -> loop.set(blockIndex, AdvIBdr.constant(0)),
                AdvIBdr.lessThan(blockIndex, blockCount),
                loop -> loop.increment(blockIndex, 1),
                loop -> {
                    loop.set(firstSlot, blockValue(
                            program,
                            blockIndex,
                            AdvIBdr.constant(ProtectedVMMethod.BLOCK_START_SLOT)));
                    loop.set(slotCount, blockValue(
                            program,
                            blockIndex,
                            AdvIBdr.constant(ProtectedVMMethod.BLOCK_SLOT_COUNT)));
                    loop.ifCondition(
                            AdvIBdr.and(
                                    AdvIBdr.greaterOrEqual(instructionIndex, firstSlot),
                                    AdvIBdr.lessThan(
                                            instructionIndex,
                                            AdvIBdr.plus(firstSlot, slotCount))),
                            containing -> {
                                containing.set(current, AdvIBdr.constant(0));
                                containing.forLoop(
                                        chain -> chain.set(slot, firstSlot),
                                        AdvIBdr.lessOrEqual(slot, instructionIndex),
                                        chain -> chain.increment(slot, 1),
                                        chain -> {
                                            chain.set(raw, stateKeyCipherAt(program, slot));
                                            chain.ifElse(
                                                    AdvIBdr.equal(slot, firstSlot),
                                                    entry -> entry.set(current, AdvIBdr.bitXor(
                                                            raw,
                                                            mixCall(
                                                                    key,
                                                                    slot,
                                                                    AdvIBdr.constant(profile.saltState),
                                                                    AdvIBdr.constant(0)))),
                                                    linked -> linked.set(current, AdvIBdr.bitXor(
                                                            raw,
                                                            mixCall(
                                                                    AdvIBdr.bitXor(key, current),
                                                                    slot,
                                                                    blockIndex,
                                                                    AdvIBdr.constant(profile.saltState)))));
                                        });
                                containing.returnValue(current);
                            });
                });
        ib.returnValue(AdvIBdr.constant(0));
        return method;
    }

    private Expr stateKeyCipherAt(Expr program, Expr instructionIndex)
    {
        return AdvIBdr.arrayAt(
                callProgramArray(program, programLayout.layoutStream.name()),
                AdvIBdr.plus(
                        AdvIBdr.multiply(
                                instructionIndex,
                                AdvIBdr.constant(ProtectedVMMethod.RECORD_SIZE)),
                        AdvIBdr.constant(
                                profile.layoutSlot(ProtectedVMMethod.LAYOUT_STATE_KEY))));
    }

    private MethodNode genInstructionIndexInBlockMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, vmLayout.instructionIndexInBlock.name(), vmLayout.instructionIndexInBlock.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local program = ib.getLocal("program", programLayout.owner, 0);
        Local blockIndex = ib.getLocal("blockIndex", "I", 1);
        Local pc = ib.getLocal("pc", "I", 2);
        Local blockCount = ib.getLocal("blockCount", "I", 3);
        Local firstSlot = ib.getLocal("firstSlot", "I", 4);
        Local slotCount = ib.getLocal("slotCount", "I", 5);
        Local offset = ib.getLocal("offset", "I", 6);
        Local slot = ib.getLocal("slot", "I", 7);
        Local stateKey = ib.getLocal("stateKey", "I", 8);
        Local key = ib.getLocal("methodKey", "I", 9);
        Local rawStateKey = ib.getLocal("rawStateKey", "I", 10);

        ib.set(blockCount, AdvIBdr.divide(
                AdvIBdr.arrayLength(callProgramArray(program, programLayout.blockStream.name())),
                AdvIBdr.constant(ProtectedVMMethod.BLOCK_SIZE)));
        ib.ifCondition(
                AdvIBdr.or(
                        AdvIBdr.lessThan(blockIndex, AdvIBdr.constant(0)),
                        AdvIBdr.greaterOrEqual(blockIndex, blockCount)),
                b -> b.returnValue(AdvIBdr.constant(-1)));
        ib.set(firstSlot, blockValue(program, blockIndex, AdvIBdr.constant(ProtectedVMMethod.BLOCK_START_SLOT)));
        ib.set(slotCount, blockValue(program, blockIndex, AdvIBdr.constant(ProtectedVMMethod.BLOCK_SLOT_COUNT)));
        ib.set(key, callProgramInt(program, programLayout.methodKey.name()));
        ib.set(stateKey, AdvIBdr.constant(0));
        ib.forLoop(
                b -> b.set(offset, AdvIBdr.constant(0)),
                AdvIBdr.lessThan(offset, slotCount),
                b -> b.increment(offset, 1),
                b -> {
                    b.set(slot, AdvIBdr.plus(firstSlot, offset));
                    b.set(rawStateKey, stateKeyCipherAt(program, slot));
                    b.ifElse(
                            AdvIBdr.equal(key, AdvIBdr.constant(0)),
                            plain -> plain.set(stateKey, rawStateKey),
                            protectedState -> protectedState.ifElse(
                                    AdvIBdr.equal(offset, AdvIBdr.constant(0)),
                                    entry -> entry.set(stateKey, AdvIBdr.bitXor(
                                            rawStateKey,
                                            mixCall(
                                                    key,
                                                    slot,
                                                    AdvIBdr.constant(profile.saltState),
                                                    AdvIBdr.constant(0)))),
                                    linked -> linked.set(stateKey, AdvIBdr.bitXor(
                                            rawStateKey,
                                            mixCall(
                                                    AdvIBdr.bitXor(key, stateKey),
                                                    slot,
                                                    blockIndex,
                                                    AdvIBdr.constant(profile.saltState))))));
                    b.ifCondition(
                            AdvIBdr.equal(
                                    layoutValue(program, slot, ProtectedVMMethod.LAYOUT_PC, stateKey),
                                    pc),
                            found -> found.returnValue(slot));
                    b.ifCondition(
                            AdvIBdr.equal(
                                    layoutValue(program, slot, ProtectedVMMethod.LAYOUT_ORIGINAL_PC, stateKey),
                                    pc),
                            found -> found.returnValue(slot));
                });
        ib.returnValue(AdvIBdr.constant(-1));
        return method;
    }

    private MethodNode genSyncStateMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, vmLayout.syncState.name(), vmLayout.syncState.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local program = ib.getLocal("program", programLayout.owner, 0);
        Local frame = ib.getLocal("frame", frameLayout.owner, 1);
        Local pc = ib.getLocal("pc", "I", 2);
        Local index = ib.getLocal("instructionIndex", "I", 3);
        Local stateKey = ib.getLocal("stateKey", "I", 4);
        Local blockIndex = ib.getLocal("blockIndex", "I", 5);

        ib.set(index, AdvIBdr.callStatic(
                vmLayout.owner,
                vmLayout.instructionIndex.name(),
                "I",
                program,
                frame,
                pc));
        ib.ifCondition(
                AdvIBdr.equal(index, AdvIBdr.constant(-1)),
                b -> {
                    b.set(AdvIBdr.field(frame, frameLayout.stateKey), AdvIBdr.constant(0));
                    b.set(AdvIBdr.field(frame, frameLayout.blockIndex), AdvIBdr.constant(-1));
                    b.returnVoid();
                });
        ib.set(stateKey, AdvIBdr.callStatic(
                vmLayout.owner,
                vmLayout.stateKey.name(),
                "I",
                program,
                index));
        ib.set(stateKey, AdvIBdr.bitXor(stateKey, AdvIBdr.field(frame, frameLayout.integrityKey)));
        ib.set(blockIndex, layoutValue(program, index, ProtectedVMMethod.LAYOUT_BLOCK_INDEX, stateKey));
        ib.set(AdvIBdr.field(frame, frameLayout.stateKey), stateKey);
        ib.set(AdvIBdr.field(frame, frameLayout.blockIndex), blockIndex);
        ib.returnVoid();
        return method;
    }

    private MethodNode genMixMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, vmLayout.mix.name(), vmLayout.mix.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local key = ib.getLocal("key", "I", 0);
        Local a = ib.getLocal("a", "I", 1);
        Local b = ib.getLocal("b", "I", 2);
        Local c = ib.getLocal("c", "I", 3);
        Local x = ib.getLocal("x", "I", 4);
        ib.set(x, AdvIBdr.bitXor(key, AdvIBdr.constant(profile.mixSeed)));
        mixRound(ib, x, a, profile.mixRoundA);
        mixRound(ib, x, b, profile.mixRoundB);
        mixRound(ib, x, c, profile.mixRoundC);
        ib.set(x, AdvIBdr.bitXor(x, AdvIBdr.unsignedShiftRight(x, AdvIBdr.constant(16))));
        ib.set(x, AdvIBdr.multiply(x, AdvIBdr.constant(profile.mixMulA)));
        ib.set(x, AdvIBdr.bitXor(x, AdvIBdr.unsignedShiftRight(x, AdvIBdr.constant(15))));
        ib.set(x, AdvIBdr.multiply(x, AdvIBdr.constant(profile.mixMulB)));
        ib.set(x, AdvIBdr.bitXor(x, AdvIBdr.unsignedShiftRight(x, AdvIBdr.constant(16))));
        ib.returnValue(x);
        return method;
    }

    private MethodNode genDispatchKeyMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, vmLayout.dispatchKey.name(), vmLayout.dispatchKey.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local opcode = ib.getLocal("opcode", "I", 0);
        ib.returnValue(dispatchKeyExpr(opcode));
        return method;
    }

    private void generateDispatch(AdvIBdr ib, LabelNode afterDispatch, LabelNode unknownOpcode)
    {
        Set<Opcs> opcodeSet = EnumSet.noneOf(Opcs.class);
        switch (config.interpretMode)
        {
            case SAVE_ALL_INSTRUCTION -> opcodeSet.addAll(branches.keySet());
            case SAVE_ONLY_REQUIRED_INSTRUCTION ->
            {
                for(CodePoolGenerator codePoolGenerator : codePoolGenerators)
                {
                    opcodeSet.addAll(codePoolGenerator.getUsedOpcodes());
                }
            }
        }
        if (structurePlan.schedulerKind() != VMStructurePlan.SchedulerKind.REGISTER)
        {
            opcodeSet.remove(Opcs.REGISTER_OP);
        }
        if (structurePlan.schedulerKind() != VMStructurePlan.SchedulerKind.DATA_FLOW)
        {
            opcodeSet.remove(Opcs.DATA_FLOW_REGION);
        }
        if (structurePlan.schedulerKind() == VMStructurePlan.SchedulerKind.REGISTER)
        {
            opcodeSet.add(Opcs.REGISTER_OP);
        }
        if (structurePlan.schedulerKind() == VMStructurePlan.SchedulerKind.DATA_FLOW)
        {
            opcodeSet.add(Opcs.DATA_FLOW_REGION);
        }
        if (!superInstructions.recipes().isEmpty())
        {
            opcodeSet.add(Opcs.SUPER_INSTRUCTION);
            superInstructionChunks = createSuperInstructionChunks(superInstructions.recipes());
            for (SuperInstructionChunk chunk : superInstructionChunks)
            {
                classNode.methods.add(chunk.method);
            }
        }

        List<Opcs> opcodes = new ArrayList<>(opcodeSet);
        opcodes.sort((left, right) -> Integer.compare(
                dispatchOpcode(left),
                dispatchOpcode(right)
        ));

        List<InterpretChunk> chunks = createInterpretChunks(opcodes);
        Map<Opcs, InterpretChunkSlot> slotByOpcode = new EnumMap<>(Opcs.class);
        for (InterpretChunk chunk : chunks)
        {
            classNode.methods.add(chunk.method);
            for (int opcodeIndex = 0; opcodeIndex < chunk.opcodes.size(); opcodeIndex++)
            {
                slotByOpcode.put(chunk.opcodes.get(opcodeIndex), new InterpretChunkSlot(chunk.index, opcodeIndex));
            }
        }

        List<DispatchEntry> entries = createDispatchEntries(opcodes, slotByOpcode);
        if (structureGenerator instanceof VMDispatchGenerator dispatchGenerator)
        {
            List<VMDispatchTarget> targets = entries.stream()
                    .map(entry -> new VMDispatchTarget(
                            entry.key,
                            entry.primaryKey,
                            entry.opcode,
                            entry.slot.chunkIndex,
                            entry.slot.opcodeIndex,
                            interpretChunkName(entry.slot.chunkIndex),
                            interpretChunkDescriptor()))
                    .toList();
            dispatchGenerator.emitDispatch(new VMDispatchGenerationContext(
                    structureGeneration,
                    ib,
                    interpretContext(afterDispatch),
                    targets,
                    afterDispatch,
                    unknownOpcode,
                    (instructions, runtime, target, instructionIndex) -> emitChunkCall(
                            instructions,
                            runtime,
                            new InterpretChunkSlot(target.handlerGroup(), target.handlerIndex()),
                            instructionIndex),
                    this::setDispatchSelector,
                    dispatchDescriptor()));
            return;
        }
        throw new IllegalStateException(
                "VM structure has no dedicated dispatch generator: " + structureGenerator.structure());
    }

    private List<DispatchEntry> createDispatchEntries(
            List<Opcs> opcodes,
            Map<Opcs, InterpretChunkSlot> slotByOpcode)
    {
        List<DispatchEntry> entries = new ArrayList<>();
        for (Opcs opcode : opcodes)
        {
            InterpretChunkSlot slot = slotByOpcode.get(opcode);
            if (slot == null)
            {
                throw new IllegalStateException("Opcode has no interpret chunk slot: " + opcode);
            }
            int primaryKey = dispatchOpcode(opcode);
            entries.add(new DispatchEntry(primaryKey, primaryKey, opcode, slot));
            int alternateKey = dispatchKey(primaryKey);
            if (alternateKey != primaryKey)
            {
                entries.add(new DispatchEntry(alternateKey, primaryKey, opcode, slot));
            }
        }
        return List.copyOf(entries);
    }

    private void setDispatchSelector(AdvIBdr ib, InterpretContext context, Local selector)
    {
        ib.set(selector, context.opcode());
        ib.ifCondition(
                featureEnabled(context.program(), ProtectedVMMethod.FEATURE_OBFUSCATE_DISPATCH),
                b -> b.set(selector, AdvIBdr.callStatic(
                        className(),
                        vmLayout.dispatchKey.name(),
                        "I",
                        context.opcode())));
    }

    private void emitChunkCall(
            AdvIBdr ib,
            InterpretContext context,
            InterpretChunkSlot slot,
            Expr instructionIndex)
    {
        ib.directCall(AdvIBdr.callStatic(
                className(),
                interpretChunkName(slot.chunkIndex),
                "V",
                semanticHandlerArguments(
                        context,
                        AdvIBdr.constant(slot.opcodeIndex),
                        instructionIndex)));
    }

    private InterpretContext interpretContext(LabelNode loopStart)
    {
        return new InterpretContext(
                className(),
                frameLayout,
                programLayout,
                vmLayout,
                loopStart,
                config.vmStructure == VMStructure.SIMPLE_DISPATCH ? null : this::emitDecodeOperandInline);
    }

    private int dispatchOpcode(Opcs opcode)
    {
        int mutated = opcMutator.toMutated(opcode);
        return structurePlan.usesDirectTokens() ? profile.directHandlerToken(mutated) : mutated;
    }

    private String structureMethodName(String baseName, String descriptor)
    {
        return namer.method(className(), baseName, descriptor);
    }

    private String structureClassName(String baseName)
    {
        return namer.className(
                classPackage(className()),
                classSimpleName(className()) + '$' + baseName);
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

    private String dispatchDescriptor()
    {
        return "(" +
                vmProgramGenerator.descriptor() +
                methodFrameGenerator.descriptor() +
                "[I[Ljava/lang/Object;II)I";
    }

    private List<InterpretChunk> createInterpretChunks(List<Opcs> opcodes)
    {
        if (config.vmStructure != VMStructure.SIMPLE_DISPATCH)
        {
            List<InterpretChunk> handlers = new ArrayList<>(opcodes.size());
            for (int index = 0; index < opcodes.size(); index++)
            {
                handlers.add(newInterpretChunk(index, List.of(opcodes.get(index))));
            }
            return List.copyOf(handlers);
        }
        List<InterpretChunk> chunks = new ArrayList<>();
        List<Opcs> current = new ArrayList<>();
        int chunkIndex = 0;
        for (Opcs opcode : opcodes)
        {
            current.add(opcode);
            if (current.size() <= 1)
            {
                continue;
            }
            if (current.size() > MAX_INTERPRET_CHUNK_OPCODES || tooLargeInterpretChunk(chunkIndex, current))
            {
                current.remove(current.size() - 1);
                chunks.add(newInterpretChunk(chunkIndex++, current));
                current = new ArrayList<>();
                current.add(opcode);
            }
        }
        if (!current.isEmpty())
        {
            chunks.add(newInterpretChunk(chunkIndex, current));
        }
        return List.copyOf(chunks);
    }

    private boolean tooLargeInterpretChunk(int chunkIndex, List<Opcs> opcodes)
    {
        return FileUtils.estimateMaxSize(genInterpretChunkMethod(chunkIndex, opcodes)) > INTERPRET_CHUNK_CODE_SIZE_LIMIT;
    }

    private InterpretChunk newInterpretChunk(int chunkIndex, List<Opcs> opcodes)
    {
        List<Opcs> chunkOpcodes = List.copyOf(opcodes);
        return new InterpretChunk(chunkIndex, chunkOpcodes, genInterpretChunkMethod(chunkIndex, chunkOpcodes));
    }

    private List<SuperInstructionChunk> createSuperInstructionChunks(List<SuperInstructionRegistry.Recipe> recipes)
    {
        List<SuperInstructionChunk> chunks = new ArrayList<>();
        List<SuperInstructionRegistry.Recipe> current = new ArrayList<>();
        int chunkIndex = 0;
        for (SuperInstructionRegistry.Recipe recipe : recipes)
        {
            current.add(recipe);
            if (current.size() <= 1)
            {
                continue;
            }
            if (current.size() > MAX_SUPER_INSTRUCTION_CHUNK_RECIPES ||
                tooLargeSuperInstructionChunk(chunkIndex, current))
            {
                current.remove(current.size() - 1);
                chunks.add(newSuperInstructionChunk(chunkIndex++, current));
                current = new ArrayList<>();
                current.add(recipe);
            }
        }
        if (!current.isEmpty())
        {
            chunks.add(newSuperInstructionChunk(chunkIndex, current));
        }
        return List.copyOf(chunks);
    }

    private boolean tooLargeSuperInstructionChunk(
            int chunkIndex,
            List<SuperInstructionRegistry.Recipe> recipes)
    {
        return FileUtils.estimateMaxSize(genSuperInstructionChunkMethod(chunkIndex, recipes)) >
               SUPER_INSTRUCTION_CHUNK_CODE_SIZE_LIMIT;
    }

    private SuperInstructionChunk newSuperInstructionChunk(
            int chunkIndex,
            List<SuperInstructionRegistry.Recipe> recipes)
    {
        List<SuperInstructionRegistry.Recipe> chunkRecipes = List.copyOf(recipes);
        return new SuperInstructionChunk(
                chunkIndex,
                chunkRecipes,
                genSuperInstructionChunkMethod(chunkIndex, chunkRecipes));
    }

    private MethodNode genInterpretChunkMethod(int chunkIndex, List<Opcs> opcodes)
    {
        MethodNode method = MethodUtils.newMethodNode(
                config.vmStructure == VMStructure.SIMPLE_DISPATCH
                        ? new Acc[]{Acc.PRIVATE, Acc.STATIC}
                        : new Acc[]{Acc.STATIC},
                interpretChunkName(chunkIndex),
                interpretChunkDescriptor());
        AdvIBdr ib = new AdvIBdr(method);
        if (config.vmStructure != VMStructure.SIMPLE_DISPATCH)
        {
            normalizeSemanticHandlerParameters(ib);
        }
        Local opcodeIndex = ib.getLocal("opcodeIndex", "I", InterpretContext.RIGHT_VALUE);
        Local passedInstructionIndex = ib.getLocal("passedInstructionIndex", "I", 6);
        int decoderSite = profile.decodeVariant ^ chunkIndex * 0x45D9F3B ^
                (opcodes.isEmpty() ? 0 : opcodes.getFirst().ordinal());
        InterpretContext context = new InterpretContext(
                className(),
                frameLayout,
                programLayout,
                vmLayout,
                null,
                config.vmStructure == VMStructure.SIMPLE_DISPATCH
                        ? null
                        : (instructions, runtime, target) -> emitDecodeOperandInline(
                                instructions,
                                runtime,
                                target,
                                decoderSite));
        ib.set(context.instructionIndex(), passedInstructionIndex);
        ib.set(context.operandIndex(), AdvIBdr.constant(0));
        if (opcodes.size() == 1)
        {
            Opcs opcode = opcodes.getFirst();
            if (opcode == Opcs.SUPER_INSTRUCTION)
            {
                generateSuperInstruction(ib, context);
            }
            else
            {
                InterpretBranch branch = branches.get(opcode);
                if (branch == null)
                {
                    throw new IllegalStateException("Missing interpret branch: " + opcode);
                }
                emitInterpretBranch(ib, context, branch, opcode);
            }
            ib.returnVoid();
            return method;
        }
        @SuppressWarnings("unchecked")
        java.util.function.Consumer<AdvIBdr>[] cases = new java.util.function.Consumer[opcodes.size()];
        for (int i = 0; i < opcodes.size(); i++)
        {
            Opcs opcode = opcodes.get(i);
            if (opcode == Opcs.SUPER_INSTRUCTION)
            {
                cases[i] = b -> generateSuperInstruction(b, context);
            }
            else
            {
                InterpretBranch branch = branches.get(opcode);
                if (branch == null)
                {
                    throw new IllegalStateException("Missing interpret branch: " + opcode);
                }
                cases[i] = b -> emitInterpretBranch(b, context, branch, opcode);
            }
        }

        ib.switchTable(
                opcodeIndex,
                0,
                this::generateUnknownOpcode,
                cases);
        ib.returnVoid();
        return method;
    }

    private void generateSuperInstruction(AdvIBdr ib, InterpretContext context)
    {
        Local superId = context.intLocal("superId", InterpretContext.RIGHT_VALUE);
        context.nextOperand(ib, superId);
        List<SuperInstructionRegistry.Recipe> recipes = superInstructions.recipes();
        @SuppressWarnings("unchecked")
        java.util.function.Consumer<AdvIBdr>[] cases = new java.util.function.Consumer[recipes.size()];
        for (SuperInstructionChunk chunk : superInstructionChunks)
        {
            for (int recipeIndex = 0; recipeIndex < chunk.recipes.size(); recipeIndex++)
            {
                SuperInstructionRegistry.Recipe recipe = chunk.recipes.get(recipeIndex);
                int localRecipeIndex = recipeIndex;
                cases[recipe.id()] = b -> b.directCall(AdvIBdr.callStatic(
                        className(),
                        superInstructionChunkName(chunk.index),
                        "V",
                        context.program(),
                        context.frame(),
                        context.code(),
                        context.constants(),
                        context.opcode(),
                        AdvIBdr.constant(localRecipeIndex),
                        context.instructionIndex()));
            }
        }
        ib.switchTable(superId, 0, this::generateUnknownOpcode, cases);
    }

    private MethodNode genSuperInstructionChunkMethod(
            int chunkIndex,
            List<SuperInstructionRegistry.Recipe> recipes)
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                superInstructionChunkName(chunkIndex),
                superInstructionChunkDescriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local recipeIndex = ib.getLocal("recipeIndex", "I", InterpretContext.RIGHT_VALUE);
        Local passedInstructionIndex = ib.getLocal("passedInstructionIndex", "I", 6);
        int decoderSite = profile.decodeVariant ^ chunkIndex * 0x27D4EB2D;
        InterpretContext context = new InterpretContext(
                className(),
                frameLayout,
                programLayout,
                vmLayout,
                null,
                config.vmStructure == VMStructure.SIMPLE_DISPATCH
                        ? null
                        : (instructions, runtime, target) -> emitDecodeOperandInline(
                                instructions,
                                runtime,
                                target,
                                decoderSite));
        ib.set(context.instructionIndex(), passedInstructionIndex);
        ib.set(context.operandIndex(), AdvIBdr.constant(1));

        @SuppressWarnings("unchecked")
        java.util.function.Consumer<AdvIBdr>[] cases = new java.util.function.Consumer[recipes.size()];
        for (int i = 0; i < recipes.size(); i++)
        {
            SuperInstructionRegistry.Recipe recipe = recipes.get(i);
            cases[i] = b -> {
                for (Opcs opcode : recipe.sequence())
                {
                    InterpretBranch branch = branches.get(opcode);
                    if (branch == null)
                    {
                        throw new IllegalStateException("Missing interpret branch for super instruction body: " + opcode);
                    }
                    branch.generate(b, context, opcode);
                }
            };
        }

        ib.switchTable(recipeIndex, 0, this::generateUnknownOpcode, cases);
        ib.returnVoid();
        return method;
    }

    private void emitInterpretBranch(
            AdvIBdr ib,
            InterpretContext context,
            InterpretBranch branch,
            Opcs opcode)
    {
        int caseCount = config.interpretBranchCases;
        if (!config.obfuscateInterpretBranch || caseCount <= 1)
        {
            branch.generate(ib, context, opcode);
            return;
        }

        List<Map.Entry<Opcs, InterpretBranch>> fakeCandidates = new ArrayList<>(branches
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey() != opcode)
                .filter(entry -> entry.getValue() != branch)
                .filter(entry -> !entry.getValue().term(entry.getKey()))
                .filter(entry -> !Opcs.isSpecialOpc(entry.getKey()))
                .toList());

        if (fakeCandidates.isEmpty())
        {
            branch.generate(ib, context, opcode);
            return;
        }

        VMObfProfile.InterpretBranchPlan plan = profile.interpretBranchPlan(opcode, caseCount);
        RandomUtils.shuffle(fakeCandidates);
        int[] labels = plan.labels();
        List<SwitchCase> cases = new ArrayList<>(caseCount);
        int fakeIndex = 0;
        for (int label : labels)
        {
            if (label == plan.realLabel())
            {
                cases.add(AdvIBdr.switchCase(
                        label,
                        b -> branch.generate(b, context, opcode)));
                continue;
            }

            Map.Entry<Opcs, InterpretBranch> fake =
                    fakeCandidates.get(fakeIndex++ % fakeCandidates.size());

            Opcs fakeOpcode = fake.getKey();
            InterpretBranch fakeBranch = fake.getValue();

            cases.add(AdvIBdr.switchCase(
                    label,
                    b -> fakeBranch.generate(b, context, fakeOpcode)));
        }

        Local methodKey = context.intLocal("branchMethodKey", InterpretContext.BRANCH_METHOD_KEY);
        Local virtualPc = context.intLocal("branchVirtualPc", InterpretContext.BRANCH_VIRTUAL_PC);
        Local encodedSelector = context.intLocal(
                "branchEncodedSelector",
                InterpretContext.BRANCH_ENCODED_SELECTOR);
        Local mixPath = context.intLocal("branchMixPath", InterpretContext.BRANCH_MIX_PATH);
        Local mixMask = context.intLocal("branchMixMask", InterpretContext.BRANCH_MIX_MASK);
        Local selector = context.intLocal("branchSelector", InterpretContext.BRANCH_SELECTOR);

        ib.set(methodKey, callProgramInt(context.program(), programLayout.methodKey.name()));
        emitLayoutValueInline(
                ib,
                context,
                virtualPc,
                ProtectedVMMethod.LAYOUT_PC,
                InterpretContext.BRANCH_PC_SCRATCH);
        emitLayoutValueInline(
                ib,
                context,
                encodedSelector,
                ProtectedVMMethod.LAYOUT_BRANCH_SELECTOR,
                InterpretContext.BRANCH_SELECTOR_SCRATCH);
        ib.set(selector, encodedSelector);
        ib.ifCondition(
                featureEnabled(
                        context.program(),
                        ProtectedVMMethod.FEATURE_OBFUSCATE_INTERPRET_BRANCH),
                decode -> {
                    decode.set(mixPath, mixCall(
                            AdvIBdr.bitXor(methodKey, context.frameStateKey()),
                            virtualPc,
                            context.instructionIndex(),
                            AdvIBdr.constant(plan.maskSalt())));
                    decode.set(mixMask, mixCall(
                            AdvIBdr.bitXor(mixPath, context.opcode()),
                            context.frameStateKey(),
                            AdvIBdr.constant(plan.decodeSalt()),
                            AdvIBdr.constant(plan.maskSalt() ^ profile.saltHandler)));
                    decode.set(selector, structureXorDecode(
                            encodedSelector,
                            mixMask,
                            plan.decodeSalt()));
                });

        ib.switchLookup(
                selector,
                this::generateUnknownOpcode,
                cases.toArray(SwitchCase[]::new));
    }

    private String interpretChunkName(int chunkIndex)
    {
        return interpretChunkNames.computeIfAbsent(
                chunkIndex,
                index -> namer.method(
                        className(),
                        config.vmStructure == VMStructure.SIMPLE_DISPATCH
                                ? "interpretChunk$" + index
                                : "handler$" + config.vmStructure.name().toLowerCase(Locale.ROOT) + '$' + index,
                        interpretChunkDescriptor()));
    }

    private String superInstructionChunkName(int chunkIndex)
    {
        return superInstructionChunkNames.computeIfAbsent(
                chunkIndex,
                index -> namer.method(className(), "superInstructionChunk$" + index, superInstructionChunkDescriptor()));
    }

    private String interpretHandlerDescriptor()
    {
        return "(" +
                vmProgramGenerator.descriptor() +
                methodFrameGenerator.descriptor() +
                "[I[Ljava/lang/Object;I)V";
    }

    private String interpretChunkDescriptor()
    {
        if (config.vmStructure == VMStructure.SIMPLE_DISPATCH)
        {
            return superInstructionChunkDescriptor();
        }
        int shape = structurePlan.structure().ordinal();
        StringBuilder descriptor = new StringBuilder("(");
        for (SemanticArgument argument : semanticCoreOrder(shape))
        {
            descriptor.append(semanticArgumentDescriptor(argument));
        }
        for (int bit = 0; bit < 5; bit++)
        {
            descriptor.append((shape & 1 << bit) == 0 ? 'I' : 'J');
        }
        return descriptor.append(")V").toString();
    }

    private String superInstructionChunkDescriptor()
    {
        return "(" +
                vmProgramGenerator.descriptor() +
                methodFrameGenerator.descriptor() +
                "[I[Ljava/lang/Object;III)V";
    }

    private Expr[] semanticHandlerArguments(
            InterpretContext context,
            Expr opcodeIndex,
            Expr instructionIndex)
    {
        if (config.vmStructure == VMStructure.SIMPLE_DISPATCH)
        {
            return new Expr[]{
                    context.program(),
                    context.frame(),
                    context.code(),
                    context.constants(),
                    context.opcode(),
                    opcodeIndex,
                    instructionIndex
            };
        }
        int shape = structurePlan.structure().ordinal();
        Map<SemanticArgument, Expr> core = new EnumMap<>(SemanticArgument.class);
        core.put(SemanticArgument.PROGRAM, context.program());
        core.put(SemanticArgument.FRAME, context.frame());
        core.put(SemanticArgument.CODE, context.code());
        core.put(SemanticArgument.CONSTANTS, context.constants());
        core.put(SemanticArgument.OPCODE, context.opcode());
        core.put(SemanticArgument.OPCODE_INDEX, opcodeIndex);
        core.put(SemanticArgument.INSTRUCTION_INDEX, instructionIndex);
        List<Expr> arguments = new ArrayList<>();
        for (SemanticArgument argument : semanticCoreOrder(shape))
        {
            arguments.add(core.get(argument));
        }
        for (int bit = 0; bit < 5; bit++)
        {
            arguments.add((shape & 1 << bit) == 0
                    ? AdvIBdr.constant(profile.decodeVariant ^ bit)
                    : AdvIBdr.constant(
                            ((long) profile.decodeVariant << 32) ^
                            (0xD1B54A32D192ED03L + bit)));
        }
        return arguments.toArray(Expr[]::new);
    }

    private void normalizeSemanticHandlerParameters(AdvIBdr ib)
    {
        int shape = structurePlan.structure().ordinal();
        List<SemanticArgument> order = semanticCoreOrder(shape);
        Map<SemanticArgument, Local> copies = new EnumMap<>(SemanticArgument.class);
        for (int slot = 0; slot < order.size(); slot++)
        {
            SemanticArgument argument = order.get(slot);
            Local source = ib.getLocal(
                    "encoded" + argument,
                    semanticArgumentType(argument),
                    slot);
            Local copy = ib.getLocal(
                    "semantic" + argument,
                    semanticArgumentType(argument),
                    160 + argument.ordinal());
            ib.set(copy, source);
            copies.put(argument, copy);
        }
        for (SemanticArgument argument : SemanticArgument.values())
        {
            ib.set(
                    ib.getLocal(
                            "canonical" + argument,
                            semanticArgumentType(argument),
                            argument.ordinal()),
                    copies.get(argument));
        }
    }

    private String semanticArgumentDescriptor(SemanticArgument argument)
    {
        return switch (argument)
        {
            case PROGRAM -> vmProgramGenerator.descriptor();
            case FRAME -> methodFrameGenerator.descriptor();
            case CODE -> "[I";
            case CONSTANTS -> "[Ljava/lang/Object;";
            case OPCODE, OPCODE_INDEX, INSTRUCTION_INDEX -> "I";
        };
    }

    private String semanticArgumentType(SemanticArgument argument)
    {
        return switch (argument)
        {
            case PROGRAM -> programLayout.owner;
            case FRAME -> frameLayout.owner;
            case CODE -> "[I";
            case CONSTANTS -> "[Ljava/lang/Object;";
            case OPCODE, OPCODE_INDEX, INSTRUCTION_INDEX -> "I";
        };
    }

    private static List<SemanticArgument> semanticCoreOrder(int structureShape)
    {
        List<SemanticArgument> remaining = new ArrayList<>(List.of(SemanticArgument.values()));
        List<SemanticArgument> order = new ArrayList<>(remaining.size());
        int rank = structureShape;
        while (!remaining.isEmpty())
        {
            int selected = Math.floorMod(rank, remaining.size());
            rank /= remaining.size();
            order.add(remaining.remove(selected));
        }
        return order;
    }

    private void generateUnknownOpcode(AdvIBdr ib)
    {
        Local frame = AdvIBdr.local("frame", frameLayout.owner, InterpretContext.FRAME);
        Local opcode = AdvIBdr.local("opcode", "I", InterpretContext.OPCODE);
        ib.throwValue(AdvIBdr.newObject(
                "java/lang/IllegalStateException",
                stringConcat(
                        AdvIBdr.constant("Unknown VM opcode "),
                        AdvIBdr.callStatic("java/lang/Integer", "toHexString", "java/lang/String", opcode),
                        AdvIBdr.constant(" at pc "),
                        AdvIBdr.minus(AdvIBdr.field(frame, frameLayout.programCounter), AdvIBdr.constant(1)))));
    }

    private MethodNode genExecuteMethod()
    {
        MethodNode methodNode = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC, Acc.STATIC, Acc.VARARGS},
                "execute",
                "(ILjava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;",
                "<T:Ljava/lang/Object;>(ILjava/lang/Object;[Ljava/lang/Object;)TT;",
                null);
        AdvIBdr ib = new AdvIBdr(methodNode);
        Local codeId = ib.getLocal("codeId", "I", 0);
        Local receiver = ib.getLocal("receiver", "java/lang/Object", 1);
        Local arguments = ib.getLocal("arguments", "[Ljava/lang/Object;", 2);
        ib.returnValue(AdvIBdr.callStatic(
                vmLayout.owner,
                "execute",
                "java/lang/Object",
                codeId,
                receiver,
                arguments,
                AdvIBdr.constant(integrityCapability)));
        return methodNode;
    }

    private MethodNode genExecuteWithIntegrityMethod()
    {
        MethodNode methodNode = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC, Acc.STATIC},
                "execute",
                "(ILjava/lang/Object;[Ljava/lang/Object;I)Ljava/lang/Object;",
                "<T:Ljava/lang/Object;>(ILjava/lang/Object;[Ljava/lang/Object;I)TT;",
                null);
        AdvIBdr ib = new AdvIBdr(methodNode);
        Local codeId = ib.getLocal("codeId", "I", 0);
        Local receiver = ib.getLocal("receiver", "java/lang/Object", 1);
        Local arguments = ib.getLocal("arguments", "[Ljava/lang/Object;", 2);
        Local integrityKey = ib.getLocal("integrityKey", "I", 3);
        Local program = ib.getLocal("program", programLayout.owner, 4);
        Local frame = ib.getLocal("frame", frameLayout.owner, 5);
        Local argumentOffset = ib.getLocal("argumentOffset", "I", 6);

        // VMProgram program = resolve(codeId);
        ib.set(program, AdvIBdr.callStatic(
                vmLayout.owner,
                vmLayout.resolve.name(),
                programLayout.owner,
                codeId));

        // MethodFrame frame = new MethodFrame(program.maxLocals(), program.maxStack());
        ib.set(frame, AdvIBdr.newObject(
                frameLayout.owner,
                AdvIBdr.callVirtual(program, programLayout.owner, programLayout.maxLocals.name(), "I"),
                AdvIBdr.callVirtual(program, programLayout.owner, programLayout.maxStack.name(), "I")));
        ib.set(
                AdvIBdr.field(frame, frameLayout.integrityKey),
                AdvIBdr.bitXor(integrityKey, AdvIBdr.constant(integrityCapability)));

        // Instance methods reserve locals[0] for the receiver.
        ib.ifElse(
                AdvIBdr.notNull(receiver),
                b -> {
                    b.setArray(AdvIBdr.field(frame, frameLayout.locals), AdvIBdr.constant(0), receiver);
                    b.set(argumentOffset, AdvIBdr.constant(1));
                },
                b -> b.set(argumentOffset, AdvIBdr.constant(0)));

        // System.arraycopy(arguments, 0, frame.locals, argumentOffset, arguments.length);
        ib.directCall(AdvIBdr.callStatic(
                "java/lang/System",
                "arraycopy",
                "V",
                AdvIBdr.cast(arguments, "java/lang/Object"),
                AdvIBdr.constant(0),
                AdvIBdr.cast(AdvIBdr.field(frame, frameLayout.locals), "java/lang/Object"),
                argumentOffset,
                AdvIBdr.arrayLength(arguments)));

        ib.directCall(AdvIBdr.callStatic(
                vmLayout.owner,
                vmLayout.syncState.name(),
                "V",
                program,
                frame,
                AdvIBdr.field(frame, frameLayout.programCounter)));

        // interpret(program, frame);
        ib.directCall(AdvIBdr.callStatic(
                vmLayout.owner,
                vmLayout.interpret.name(),
                "V",
                program,
                frame));
        ib.ifCondition(
                AdvIBdr.isFalse(AdvIBdr.field(frame, frameLayout.returned)),
                b -> b.throwValue(AdvIBdr.newObject(
                        "java/lang/IllegalStateException",
                        stringConcat(
                                AdvIBdr.constant("Unknown VM pc "),
                                AdvIBdr.field(frame, frameLayout.programCounter)))));

        ib.returnValue(AdvIBdr.field(frame, frameLayout.returnValue));
        return methodNode;
    }

    private MethodNode genExecuteSegmentedMethod()
    {
        MethodNode methodNode = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC, Acc.STATIC, Acc.VARARGS},
                "execute",
                "([ILjava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;",
                "<T:Ljava/lang/Object;>([ILjava/lang/Object;[Ljava/lang/Object;)TT;",
                null);
        AdvIBdr ib = new AdvIBdr(methodNode);
        Local codeIds = ib.getLocal("codeIds", "[I", 0);
        Local receiver = ib.getLocal("receiver", "java/lang/Object", 1);
        Local arguments = ib.getLocal("arguments", "[Ljava/lang/Object;", 2);
        ib.returnValue(AdvIBdr.callStatic(
                vmLayout.owner,
                "execute",
                "java/lang/Object",
                codeIds,
                receiver,
                arguments,
                AdvIBdr.constant(integrityCapability)));
        return methodNode;
    }

    private MethodNode genExecuteSegmentedWithIntegrityMethod()
    {
        MethodNode methodNode = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC, Acc.STATIC},
                "execute",
                "([ILjava/lang/Object;[Ljava/lang/Object;I)Ljava/lang/Object;",
                "<T:Ljava/lang/Object;>([ILjava/lang/Object;[Ljava/lang/Object;I)TT;",
                null);
        AdvIBdr ib = new AdvIBdr(methodNode);
        Local codeIds = ib.getLocal("codeIds", "[I", 0);
        Local receiver = ib.getLocal("receiver", "java/lang/Object", 1);
        Local arguments = ib.getLocal("arguments", "[Ljava/lang/Object;", 2);
        Local integrityKey = ib.getLocal("integrityKey", "I", 3);
        Local firstProgram = ib.getLocal("firstProgram", programLayout.owner, 4);
        Local program = ib.getLocal("program", programLayout.owner, 5);
        Local frame = ib.getLocal("frame", frameLayout.owner, 6);
        Local argumentOffset = ib.getLocal("argumentOffset", "I", 7);
        Local index = ib.getLocal("segmentIndex", "I", 8);
        Local candidate = ib.getLocal("candidateProgram", programLayout.owner, 9);

        ib.set(firstProgram, AdvIBdr.callStatic(
                vmLayout.owner,
                vmLayout.resolve.name(),
                programLayout.owner,
                AdvIBdr.arrayAt(codeIds, AdvIBdr.constant(0))));
        ib.set(frame, AdvIBdr.newObject(
                frameLayout.owner,
                AdvIBdr.callVirtual(firstProgram, programLayout.owner, programLayout.maxLocals.name(), "I"),
                AdvIBdr.callVirtual(firstProgram, programLayout.owner, programLayout.maxStack.name(), "I")));
        ib.set(
                AdvIBdr.field(frame, frameLayout.integrityKey),
                AdvIBdr.bitXor(integrityKey, AdvIBdr.constant(integrityCapability)));

        ib.ifElse(
                AdvIBdr.notNull(receiver),
                b -> {
                    b.setArray(AdvIBdr.field(frame, frameLayout.locals), AdvIBdr.constant(0), receiver);
                    b.set(argumentOffset, AdvIBdr.constant(1));
                },
                b -> b.set(argumentOffset, AdvIBdr.constant(0)));

        ib.directCall(AdvIBdr.callStatic(
                "java/lang/System",
                "arraycopy",
                "V",
                AdvIBdr.cast(arguments, "java/lang/Object"),
                AdvIBdr.constant(0),
                AdvIBdr.cast(AdvIBdr.field(frame, frameLayout.locals), "java/lang/Object"),
                argumentOffset,
                AdvIBdr.arrayLength(arguments)));

        ib.directCall(AdvIBdr.callStatic(
                vmLayout.owner,
                vmLayout.syncState.name(),
                "V",
                firstProgram,
                frame,
                AdvIBdr.field(frame, frameLayout.programCounter)));

        ib.whileLoop(
                AdvIBdr.isFalse(AdvIBdr.field(frame, frameLayout.returned)),
                loop -> {
                    loop.set(candidate, AdvIBdr.nullValue(programLayout.owner));
                    loop.forLoop(
                            b -> b.set(index, AdvIBdr.constant(0)),
                            AdvIBdr.lessThan(index, AdvIBdr.arrayLength(codeIds)),
                            b -> b.increment(index, 1),
                            b -> {
                                b.set(program, AdvIBdr.callStatic(
                                        vmLayout.owner,
                                        vmLayout.resolve.name(),
                                        programLayout.owner,
                                        AdvIBdr.arrayAt(codeIds, index)));
                                b.ifCondition(
                                        AdvIBdr.notEqual(
                                                AdvIBdr.callStatic(
                                                        vmLayout.owner,
                                                        vmLayout.instructionIndex.name(),
                                                        "I",
                                                        program,
                                                        frame,
                                                        AdvIBdr.field(frame, frameLayout.programCounter)),
                                                AdvIBdr.constant(-1)),
                                        found -> {
                                            found.set(candidate, program);
                                            found.breakLoop();
                                        });
                            });
                    loop.ifCondition(
                            AdvIBdr.isNull(candidate),
                            b -> b.throwValue(AdvIBdr.newObject(
                                    "java/lang/IllegalStateException",
                                    stringConcat(
                                            AdvIBdr.constant("Unknown VM pc "),
                                            AdvIBdr.field(frame, frameLayout.programCounter)))));
                    loop.directCall(AdvIBdr.callStatic(
                            vmLayout.owner,
                            vmLayout.interpret.name(),
                            "V",
                            candidate,
                            frame));
                });

        ib.returnValue(AdvIBdr.field(frame, frameLayout.returnValue));
        return methodNode;
    }

    private MethodNode genResolveMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.resolve.name(),
                vmLayout.resolve.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local codeId = ib.getLocal("codeId", "I", 0);
        Local resolved = ib.getLocal("resolved", programLayout.owner, 1);
        Local iterator = ib.getLocal("iterator", "java/util/Iterator", 2);
        Local candidate = ib.getLocal("candidate", programLayout.owner, 3);

        if (watermarkPlan != null)
        {
            ib.set(codeId, AdvIBdr.bitXor(
                    codeId,
                    AdvIBdr.callStatic(
                            watermarkPlan.runtimeClass(),
                            watermarkPlan.guardMethod(),
                            "I")));
        }

        ib.set(resolved, AdvIBdr.nullValue(programLayout.owner));
        ib.set(iterator, AdvIBdr.callInterface(
                AdvIBdr.staticField(vmLayout.codePools),
                "java/util/List",
                "iterator",
                "java/util/Iterator"));

        ib.whileLoop(
                AdvIBdr.isTrue(AdvIBdr.callInterface(
                        iterator,
                        "java/util/Iterator",
                        "hasNext",
                        "Z")),
                b -> {
                    Expr pool = AdvIBdr.cast(
                            AdvIBdr.callInterface(
                                    iterator,
                                    "java/util/Iterator",
                                    "next",
                                    "java/lang/Object"),
                            vmCodePoolGenerator.className());
                    b.set(candidate, AdvIBdr.callInterface(
                            pool,
                            vmCodePoolGenerator.className(),
                            vmCodePoolGenerator.find.name(),
                            programLayout.owner,
                            codeId));
                    b.ifCondition(
                            AdvIBdr.notNull(candidate),
                            found -> {
                                found.ifCondition(
                                        AdvIBdr.notNull(resolved),
                                        duplicate -> throwExceptionWithInt(
                                                duplicate,
                                                "java/lang/IllegalStateException",
                                                "Duplicate code id: ",
                                                codeId));
                                found.set(resolved, candidate);
                            });
                });

        ib.ifCondition(
                AdvIBdr.isNull(resolved),
                b -> throwExceptionWithInt(
                        b,
                        "java/lang/IllegalArgumentException",
                        "Unknown code id: ",
                        codeId));
        ib.returnValue(resolved);
        return method;
    }

    private MethodNode genConstantStringMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.constantString.name(),
                vmLayout.constantString.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local program = ib.getLocal("program", programLayout.owner, 0);
        Local frame = ib.getLocal("frame", frameLayout.owner, 1);
        Local constants = ib.getLocal("constants", "[Ljava/lang/Object;", 2);
        Local index = ib.getLocal("index", "I", 3);
        Local instructionIndex = ib.getLocal("instructionIndex", "I", 4);
        Local opcode = ib.getLocal("opcode", "I", 5);
        ib.returnValue(AdvIBdr.cast(
                AdvIBdr.callStatic(
                        vmLayout.owner,
                        vmLayout.resolveConstant.name(),
                        "java/lang/Object",
                        program,
                        AdvIBdr.arrayAt(constants, index),
                        frame,
                        instructionIndex,
                        opcode),
                "java/lang/String"));
        return method;
    }

    private MethodNode genMethodTypeMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.methodType.name(),
                vmLayout.methodType.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local descriptor = ib.getLocal("descriptor", "java/lang/String", 0);
        Local cached = ib.getLocal("cached", "java/lang/invoke/MethodType", 1);

        ib.set(cached, AdvIBdr.cast(mapGet(AdvIBdr.staticField(vmLayout.methodTypes), descriptor), "java/lang/invoke/MethodType"));
        ib.ifCondition(AdvIBdr.notNull(cached), b -> b.returnValue(cached));

        ib.set(cached, AdvIBdr.callStatic(
                "java/lang/invoke/MethodType",
                "fromMethodDescriptorString",
                "java/lang/invoke/MethodType",
                descriptor,
                AdvIBdr.callVirtual(
                        AdvIBdr.constant(org.objectweb.asm.Type.getObjectType(className())),
                        "java/lang/Class",
                        "getClassLoader",
                        "java/lang/ClassLoader")));
        ib.directCall(mapPut(AdvIBdr.staticField(vmLayout.methodTypes), descriptor, cached));
        ib.returnValue(cached);
        return method;
    }

    private MethodNode genResolveConstantMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.resolveConstant.name(),
                vmLayout.resolveConstant.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local program = ib.getLocal("program", programLayout.owner, 0);
        Local value = ib.getLocal("value", "java/lang/Object", 1);
        Local frame = ib.getLocal("frame", frameLayout.owner, 2);
        Local instructionIndex = ib.getLocal("instructionIndex", "I", 3);
        Local opcode = ib.getLocal("opcode", "I", 4);
        Local encoded = ib.getLocal("encoded", "[[I", 5);
        Local methodKey = ib.getLocal("constantMethodKey", "I", 6);
        Local stateKey = ib.getLocal("constantStateKey", "I", 7);
        Local virtualPc = ib.getLocal("constantVirtualPc", "I", 8);
        Local blockIndex = ib.getLocal("constantBlockIndex", "I", 9);
        Local path = ib.getLocal("constantPath", "I", 10);
        Local secondaryPath = ib.getLocal("constantSecondaryPath", "I", 11);
        Local binding = ib.getLocal("constantBinding", "I", 12);
        Local secondaryBinding = ib.getLocal("constantSecondaryBinding", "I", 13);
        Local index = ib.getLocal("constantVariantIndex", "I", 14);
        Local variant = ib.getLocal("constantVariant", "[I", 15);
        Local candidate = ib.getLocal("constantCandidate", "[I", 16);
        Local nonceA = ib.getLocal("constantNonceA", "I", 17);
        Local nonceB = ib.getLocal("constantNonceB", "I", 18);
        Local decoded = ib.getLocal("decodedConstant", "[I", 19);
        Local tag = ib.getLocal("constantTag", "I", 20);
        Local chars = ib.getLocal("constantChars", "[C", 21);
        Local descriptor = ib.getLocal("descriptor", "java/lang/String", 22);
        Local loader = ib.getLocal("loader", "java/lang/ClassLoader", 23);
        Local receiver = ib.getLocal("receiver", "java/lang/Object", 24);
        Local streamMask = ib.getLocal("constantStreamMask", "I", 25);
        Local typeDescriptor = ib.getLocal("typeDescriptor", "[Ljava/lang/String;", 26);
        LabelNode resolveType = new LabelNode();

        ib.ifCondition(
                AdvIBdr.isInstanceOf(value, "[Ljava/lang/String;"),
                b -> {
                    b.set(typeDescriptor, AdvIBdr.cast(value, "[Ljava/lang/String;"));
                    b.ifCondition(
                            AdvIBdr.notEqual(AdvIBdr.arrayLength(typeDescriptor), AdvIBdr.constant(1)),
                            invalid -> invalid.throwValue(AdvIBdr.newObject(
                                    "java/lang/IllegalStateException",
                                    AdvIBdr.constant("Invalid VM type constant"))));
                    b.set(descriptor, AdvIBdr.arrayAt(typeDescriptor, AdvIBdr.constant(0)));
                    b.gotoLabel(resolveType);
                });
        ib.ifCondition(AdvIBdr.not(AdvIBdr.isInstanceOf(value, "[[I")), b -> b.returnValue(value));
        ib.set(encoded, AdvIBdr.cast(value, "[[I"));
        ib.ifCondition(
                AdvIBdr.equal(AdvIBdr.arrayLength(encoded), AdvIBdr.constant(0)),
                b -> b.throwValue(AdvIBdr.newObject(
                        "java/lang/IllegalStateException",
                        AdvIBdr.constant("Invalid dynamic constant"))));

        ib.set(methodKey, callProgramInt(program, programLayout.methodKey.name()));
        ib.set(stateKey, AdvIBdr.field(frame, frameLayout.stateKey));
        ib.set(virtualPc, layoutValue(
                program,
                instructionIndex,
                ProtectedVMMethod.LAYOUT_PC,
                stateKey));
        ib.set(blockIndex, AdvIBdr.field(frame, frameLayout.blockIndex));
        ib.set(path, mixCall(
                AdvIBdr.bitXor(methodKey, stateKey),
                virtualPc,
                blockIndex,
                AdvIBdr.constant(profile.saltConstant)));
        ib.set(binding, mixCall(
                path,
                instructionIndex,
                opcode,
                AdvIBdr.constant(profile.saltString)));
        ib.set(secondaryPath, mixCall(
                AdvIBdr.bitXor(stateKey, AdvIBdr.constant(profile.saltArray)),
                methodKey,
                opcode,
                virtualPc));
        ib.set(secondaryBinding, mixCall(
                secondaryPath,
                blockIndex,
                instructionIndex,
                AdvIBdr.constant(profile.saltOpcodeMap)));

        ib.set(variant, AdvIBdr.nullValue("[I"));
        ib.forLoop(
                b -> b.set(index, AdvIBdr.constant(0)),
                AdvIBdr.lessThan(index, AdvIBdr.arrayLength(encoded)),
                b -> b.increment(index, 1),
                b -> b.ifCondition(
                        AdvIBdr.isNull(variant),
                        unresolved -> {
                            unresolved.set(candidate, AdvIBdr.arrayAt(encoded, index));
                            unresolved.ifCondition(
                                    AdvIBdr.greaterOrEqual(
                                            AdvIBdr.arrayLength(candidate),
                                            AdvIBdr.constant(5)),
                                    sized -> {
                                        sized.set(nonceA, AdvIBdr.arrayAt(candidate, AdvIBdr.constant(0)));
                                        sized.set(nonceB, AdvIBdr.arrayAt(candidate, AdvIBdr.constant(1)));
                                        sized.ifCondition(
                                                AdvIBdr.and(
                                                        AdvIBdr.equal(
                                                                AdvIBdr.arrayAt(candidate, AdvIBdr.constant(2)),
                                                                mixCall(
                                                                        AdvIBdr.bitXor(binding, nonceA),
                                                                        secondaryBinding,
                                                                        nonceB,
                                                                        AdvIBdr.constant(profile.saltHandler))),
                                                        AdvIBdr.equal(
                                                                AdvIBdr.arrayAt(candidate, AdvIBdr.constant(3)),
                                                                mixCall(
                                                                        AdvIBdr.bitXor(secondaryBinding, nonceB),
                                                                        binding,
                                                                        nonceA,
                                                                        AdvIBdr.constant(profile.saltBlock)))),
                                                found -> found.set(variant, candidate));
                                    });
                        }));
        ib.ifCondition(
                AdvIBdr.isNull(variant),
                b -> b.throwValue(AdvIBdr.newObject(
                        "java/lang/IllegalStateException",
                        AdvIBdr.constant("VM constant requested outside its execution state"))));
        ib.set(nonceA, AdvIBdr.arrayAt(variant, AdvIBdr.constant(0)));
        ib.set(nonceB, AdvIBdr.arrayAt(variant, AdvIBdr.constant(1)));
        ib.set(decoded, AdvIBdr.newArray(
                "int",
                AdvIBdr.minus(AdvIBdr.arrayLength(variant), AdvIBdr.constant(4))));
        ib.forLoop(
                b -> b.set(index, AdvIBdr.constant(0)),
                AdvIBdr.lessThan(index, AdvIBdr.arrayLength(decoded)),
                b -> b.increment(index, 1),
                b -> {
                    b.set(streamMask, AdvIBdr.bitXor(
                            mixCall(
                                    AdvIBdr.bitXor(binding, nonceA),
                                    secondaryBinding,
                                    index,
                                    AdvIBdr.constant(profile.saltConstant)),
                            AdvIBdr.callStatic(
                                    "java/lang/Integer",
                                    "rotateLeft",
                                    "I",
                                    mixCall(
                                            AdvIBdr.bitXor(secondaryBinding, nonceB),
                                            binding,
                                            index,
                                            AdvIBdr.constant(profile.saltArray)),
                                    AdvIBdr.bitAnd(
                                            AdvIBdr.plus(nonceA, index),
                                            AdvIBdr.constant(31)))));
                    b.setArray(
                            decoded,
                            index,
                            AdvIBdr.bitXor(
                                    AdvIBdr.arrayAt(
                                            variant,
                                            AdvIBdr.plus(index, AdvIBdr.constant(4))),
                                    streamMask));
                });
        ib.set(tag, AdvIBdr.arrayAt(decoded, AdvIBdr.constant(0)));
        ib.ifCondition(
                AdvIBdr.equal(tag, AdvIBdr.constant(ProtectedVMMethod.CONSTANT_INTEGER)),
                b -> b.returnValue(AdvIBdr.callStatic(
                        "java/lang/Integer",
                        "valueOf",
                        "java/lang/Integer",
                        AdvIBdr.arrayAt(decoded, AdvIBdr.constant(1)))));
        ib.ifCondition(
                AdvIBdr.equal(tag, AdvIBdr.constant(ProtectedVMMethod.CONSTANT_LONG)),
                b -> b.returnValue(AdvIBdr.callStatic(
                        "java/lang/Long",
                        "valueOf",
                        "java/lang/Long",
                        AdvIBdr.bitOr(
                                AdvIBdr.shiftLeft(
                                        AdvIBdr.cast(AdvIBdr.arrayAt(decoded, AdvIBdr.constant(1)), "J"),
                                        AdvIBdr.constant(32)),
                                AdvIBdr.callStatic(
                                        "java/lang/Integer",
                                        "toUnsignedLong",
                                        "J",
                                        AdvIBdr.arrayAt(decoded, AdvIBdr.constant(2)))))));
        ib.ifCondition(
                AdvIBdr.equal(tag, AdvIBdr.constant(ProtectedVMMethod.CONSTANT_FLOAT)),
                b -> b.returnValue(AdvIBdr.callStatic(
                        "java/lang/Float",
                        "valueOf",
                        "java/lang/Float",
                        AdvIBdr.callStatic(
                                "java/lang/Float",
                                "intBitsToFloat",
                                "F",
                                AdvIBdr.arrayAt(decoded, AdvIBdr.constant(1))))));
        ib.ifCondition(
                AdvIBdr.equal(tag, AdvIBdr.constant(ProtectedVMMethod.CONSTANT_DOUBLE)),
                b -> b.returnValue(AdvIBdr.callStatic(
                        "java/lang/Double",
                        "valueOf",
                        "java/lang/Double",
                        AdvIBdr.callStatic(
                                "java/lang/Double",
                                "longBitsToDouble",
                                "D",
                                AdvIBdr.bitOr(
                                        AdvIBdr.shiftLeft(
                                                AdvIBdr.cast(AdvIBdr.arrayAt(decoded, AdvIBdr.constant(1)), "J"),
                                                AdvIBdr.constant(32)),
                                        AdvIBdr.callStatic(
                                                "java/lang/Integer",
                                                "toUnsignedLong",
                                                "J",
                                                AdvIBdr.arrayAt(decoded, AdvIBdr.constant(2))))))));
        ib.ifCondition(
                AdvIBdr.and(
                        AdvIBdr.notEqual(tag, AdvIBdr.constant(ProtectedVMMethod.CONSTANT_STRING)),
                        AdvIBdr.notEqual(tag, AdvIBdr.constant(ProtectedVMMethod.CONSTANT_TYPE))),
                b -> b.throwValue(AdvIBdr.newObject(
                        "java/lang/IllegalStateException",
                        AdvIBdr.constant("Unknown dynamic constant type"))));
        ib.set(chars, AdvIBdr.newArray(
                "char",
                AdvIBdr.minus(AdvIBdr.arrayLength(decoded), AdvIBdr.constant(1))));
        ib.forLoop(
                b -> b.set(index, AdvIBdr.constant(1)),
                AdvIBdr.lessThan(index, AdvIBdr.arrayLength(decoded)),
                b -> b.increment(index, 1),
                b -> b.setArray(
                        chars,
                        AdvIBdr.minus(index, AdvIBdr.constant(1)),
                        AdvIBdr.cast(AdvIBdr.arrayAt(decoded, index), "C")));
        ib.set(descriptor, AdvIBdr.newObject("java/lang/String", chars));
        ib.ifCondition(
                AdvIBdr.equal(tag, AdvIBdr.constant(ProtectedVMMethod.CONSTANT_STRING)),
                b -> b.returnValue(descriptor));
        ib.mark(resolveType, "resolveTypeConstant");
        ib.set(loader, AdvIBdr.callVirtual(
                AdvIBdr.constant(org.objectweb.asm.Type.getObjectType(className())),
                "java/lang/Class",
                "getClassLoader",
                "java/lang/ClassLoader"));
        ib.ifCondition(
                AdvIBdr.greaterThan(AdvIBdr.arrayLength(AdvIBdr.field(frame, frameLayout.locals)), AdvIBdr.constant(0)),
                b -> {
                    b.set(receiver, AdvIBdr.arrayAt(AdvIBdr.field(frame, frameLayout.locals), AdvIBdr.constant(0)));
                    b.ifCondition(
                            AdvIBdr.notNull(receiver),
                            bb -> bb.set(loader, AdvIBdr.callVirtual(
                                    AdvIBdr.callVirtual(receiver, "java/lang/Object", "getClass", "java/lang/Class"),
                                    "java/lang/Class",
                                    "getClassLoader",
                                    "java/lang/ClassLoader")));
                });

        ib.ifCondition(
                AdvIBdr.equal(
                        AdvIBdr.callVirtual(descriptor, "java/lang/String", "length", "I"),
                        AdvIBdr.constant(0)),
                b -> b.throwValue(AdvIBdr.newObject(
                        "java/lang/IllegalStateException",
                        AdvIBdr.constant("Invalid encoded VM type constant"))));
        ib.ifElse(
                AdvIBdr.equal(
                        AdvIBdr.callVirtual(descriptor, "java/lang/String", "charAt", "C", AdvIBdr.constant(0)),
                        AdvIBdr.constant('(')),
                b -> b.returnValue(AdvIBdr.callStatic(
                        "java/lang/invoke/MethodType",
                        "fromMethodDescriptorString",
                        "java/lang/invoke/MethodType",
                        descriptor,
                        loader)),
                b -> b.returnValue(AdvIBdr.callStatic(
                        vmLayout.owner,
                        vmLayout.loadOwnerWithLoader.name(),
                        "java/lang/Class",
                        descriptor,
                        loader)));
        return method;
    }

    private MethodNode genFindExceptionHandlerMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.findExceptionHandler.name(),
                vmLayout.findExceptionHandler.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local throwable = ib.getLocal("throwable", "java/lang/Throwable", 0);
        Local handlers = ib.getLocal("handlers", "[I", 1);
        Local instructionPc = ib.getLocal("instructionPc", "I", 2);
        Local instructionIndex = ib.getLocal("instructionIndex", "I", 3);
        Local opcode = ib.getLocal("opcode", "I", 4);
        Local program = ib.getLocal("program", programLayout.owner, 5);
        Local frame = ib.getLocal("frame", frameLayout.owner, 6);
        Local constants = ib.getLocal("constants", "[Ljava/lang/Object;", 7);
        Local methodKey = ib.getLocal("methodKey", "I", 8);
        Local index = ib.getLocal("index", "I", 9);
        Local handlerSlot = ib.getLocal("handlerSlot", "I", 10);
        Local startPc = ib.getLocal("startPc", "I", 11);
        Local endPc = ib.getLocal("endPc", "I", 12);
        Local handlerPc = ib.getLocal("handlerPc", "I", 13);
        Local typeIndex = ib.getLocal("typeIndex", "I", 14);

        ib.set(methodKey, callProgramInt(program, programLayout.methodKey.name()));

        ib.forLoop(
                b -> b.set(index, AdvIBdr.constant(0)),
                AdvIBdr.lessThan(index, AdvIBdr.arrayLength(handlers)),
                b -> b.increment(index, 4),
                b -> {
                    b.set(handlerSlot, AdvIBdr.divide(index, AdvIBdr.constant(ProtectedVMMethod.HANDLER_SIZE)));
                    b.set(startPc, AdvIBdr.arrayAt(handlers, index));
                    b.set(endPc, AdvIBdr.arrayAt(handlers, AdvIBdr.plus(index, AdvIBdr.constant(1))));
                    b.set(handlerPc, AdvIBdr.arrayAt(handlers, AdvIBdr.plus(index, AdvIBdr.constant(2))));
                    b.set(typeIndex, AdvIBdr.arrayAt(handlers, AdvIBdr.plus(index, AdvIBdr.constant(3))));
                    b.ifCondition(
                            AdvIBdr.notEqual(methodKey, AdvIBdr.constant(0)),
                            decode -> {
                                decode.set(startPc, AdvIBdr.bitXor(startPc, handlerMixCall(methodKey, handlerSlot, 0)));
                                decode.set(endPc, AdvIBdr.bitXor(endPc, handlerMixCall(methodKey, handlerSlot, 1)));
                                decode.set(handlerPc, AdvIBdr.bitXor(handlerPc, handlerMixCall(methodKey, handlerSlot, 2)));
                                decode.set(typeIndex, AdvIBdr.bitXor(typeIndex, handlerMixCall(methodKey, handlerSlot, 3)));
                            });
                    b.ifCondition(
                            AdvIBdr.and(
                                    AdvIBdr.greaterOrEqual(instructionPc, startPc),
                                    AdvIBdr.lessThan(instructionPc, endPc)),
                            inRange -> {
                                inRange.ifCondition(AdvIBdr.lessThan(typeIndex, AdvIBdr.constant(0)), catchAll -> catchAll.returnValue(handlerPc));
                                inRange.ifCondition(
                                        AdvIBdr.isTrue(AdvIBdr.callVirtual(
                                                AdvIBdr.callStatic(
                                                        vmLayout.owner,
                                                        vmLayout.loadOwner.name(),
                                                        "java/lang/Class",
                                                        AdvIBdr.callStatic(
                                                                vmLayout.owner,
                                                                vmLayout.constantString.name(),
                                                                "java/lang/String",
                                                                program,
                                                                frame,
                                                                constants,
                                                                typeIndex,
                                                                instructionIndex,
                                                                opcode)),
                                                "java/lang/Class",
                                                "isInstance",
                                                "Z",
                                                AdvIBdr.cast(throwable, "java/lang/Object"))),
                                        typeMatches -> typeMatches.returnValue(handlerPc));
                            });
                });
        ib.returnValue(AdvIBdr.constant(-1));
        return method;
    }

    private MethodNode genGetFieldMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.getField.name(),
                vmLayout.getField.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local owner = ib.getLocal("owner", "java/lang/String", 0);
        Local name = ib.getLocal("name", "java/lang/String", 1);
        Local descriptor = ib.getLocal("descriptor", "java/lang/String", 2);
        Local isStatic = ib.getLocal("isStatic", "Z", 3);
        Local receiver = ib.getLocal("receiver", "java/lang/Object", 4);
        ib.tryCatch(
                b -> b.returnValue(AdvIBdr.callVirtual(
                        fieldHandle(owner, name, descriptor, isStatic, AdvIBdr.constant(false)),
                        "java/lang/invoke/MethodHandle",
                        "invokeExact",
                        "java/lang/Object",
                        receiver)),
                "java/lang/Throwable",
                "throwable",
                (b) -> b.throwValue(rethrow(b.getLocal("throwable"))));
        return method;
    }

    private MethodNode genSetFieldMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.setField.name(),
                vmLayout.setField.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local owner = ib.getLocal("owner", "java/lang/String", 0);
        Local name = ib.getLocal("name", "java/lang/String", 1);
        Local descriptor = ib.getLocal("descriptor", "java/lang/String", 2);
        Local isStatic = ib.getLocal("isStatic", "Z", 3);
        Local receiver = ib.getLocal("receiver", "java/lang/Object", 4);
        Local value = ib.getLocal("value", "java/lang/Object", 5);
        Local ownerClass = ib.getLocal("ownerClass", "java/lang/Class", 6);
        Local field = ib.getLocal("field", "java/lang/reflect/Field", 7);
        ib.tryCatch(
                b -> {
                    b.set(value, AdvIBdr.callStatic(
                            vmLayout.owner,
                            vmLayout.coerceArgument.name(),
                            "java/lang/Object",
                            value,
                            AdvIBdr.callStatic(vmLayout.owner, vmLayout.loadOwner.name(), "java/lang/Class", descriptor)));
                    b.ifCondition(
                            AdvIBdr.isTrue(isStatic),
                            staticWrite -> {
                                staticWrite.set(ownerClass, AdvIBdr.callStatic(vmLayout.owner, vmLayout.loadOwner.name(), "java/lang/Class", owner));
                                staticWrite.set(field, AdvIBdr.callStatic(vmLayout.owner, vmLayout.findField.name(), "java/lang/reflect/Field", ownerClass, name));
                                staticWrite.ifCondition(
                                        AdvIBdr.isTrue(AdvIBdr.callStatic(
                                                "java/lang/reflect/Modifier",
                                                "isFinal",
                                                "Z",
                                                AdvIBdr.callVirtual(field, "java/lang/reflect/Field", "getModifiers", "I"))),
                                        finalWrite -> {
                                            finalWrite.directCall(AdvIBdr.callVirtual(field, "java/lang/reflect/Field", "setAccessible", "V", AdvIBdr.constant(true)));
                                            finalWrite.directCall(AdvIBdr.callStatic(
                                                    vmLayout.owner,
                                                    vmLayout.unsafeSetStaticField.name(),
                                                    "V",
                                                    field,
                                                    value));
                                            finalWrite.returnVoid();
                                        });
                            });
                    b.directCall(AdvIBdr.callVirtual(
                            fieldHandle(owner, name, descriptor, isStatic, AdvIBdr.constant(true)),
                            "java/lang/invoke/MethodHandle",
                            "invokeExact",
                            "V",
                            receiver,
                            value));
                    b.returnVoid();
                },
                "java/lang/Throwable",
                "throwable",
                b -> b.throwValue(rethrow(b.getLocal("throwable"))));
        return method;
    }

    private MethodNode genFieldHandleMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.fieldHandle.name(),
                vmLayout.fieldHandle.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local owner = ib.getLocal("owner", "java/lang/String", 0);
        Local name = ib.getLocal("name", "java/lang/String", 1);
        Local descriptor = ib.getLocal("descriptor", "java/lang/String", 2);
        Local isStatic = ib.getLocal("isStatic", "Z", 3);
        Local setter = ib.getLocal("setter", "Z", 4);
        Local key = ib.getLocal("key", "java/lang/String", 5);
        Local cached = ib.getLocal("cached", "java/lang/invoke/MethodHandle", 6);
        Local ownerClass = ib.getLocal("ownerClass", "java/lang/Class", 7);
        Local fieldType = ib.getLocal("fieldType", "java/lang/Class", 8);
        Local field = ib.getLocal("field", "java/lang/reflect/Field", 9);
        Local handle = ib.getLocal("handle", "java/lang/invoke/MethodHandle", 10);

        ib.set(key, fieldHandleKey(owner, name, descriptor, isStatic, setter));
        ib.set(cached, AdvIBdr.cast(mapGet(AdvIBdr.staticField(vmLayout.fieldHandles), key), "java/lang/invoke/MethodHandle"));
        ib.ifCondition(AdvIBdr.notNull(cached), b -> b.returnValue(cached));

        ib.tryCatch(
                b -> {
                    b.set(ownerClass, AdvIBdr.callStatic(vmLayout.owner, vmLayout.loadOwner.name(), "java/lang/Class", owner));
                    b.set(fieldType, AdvIBdr.callStatic(vmLayout.owner, vmLayout.loadOwner.name(), "java/lang/Class", descriptor));
                    b.set(field, AdvIBdr.callStatic(vmLayout.owner, vmLayout.findField.name(), "java/lang/reflect/Field", ownerClass, name));
                    b.directCall(AdvIBdr.callVirtual(field, "java/lang/reflect/Field", "setAccessible", "V", AdvIBdr.constant(true)));

                    b.ifCondition(
                            AdvIBdr.notEqual(
                                    AdvIBdr.callVirtual(field, "java/lang/reflect/Field", "getType", "java/lang/Class"),
                                    fieldType),
                            mismatch -> throwNoSuchField(mismatch, ownerClass, name));
                    b.ifCondition(
                            AdvIBdr.notEqual(
                                    AdvIBdr.callStatic(
                                            "java/lang/reflect/Modifier",
                                            "isStatic",
                                            "Z",
                                            AdvIBdr.callVirtual(field, "java/lang/reflect/Field", "getModifiers", "I")),
                                    isStatic),
                            mismatch -> throwNoSuchField(mismatch, ownerClass, name));

                    b.set(handle, AdvIBdr.callStatic(
                            vmLayout.owner,
                            vmLayout.adaptFieldHandle.name(),
                            "java/lang/invoke/MethodHandle",
                            field,
                            isStatic,
                            setter));
                    b.directCall(mapPut(AdvIBdr.staticField(vmLayout.fieldHandles), key, handle));
                    b.returnValue(handle);
                },
                "java/lang/ReflectiveOperationException",
                "exception",
                b -> b.throwValue(illegalStateException(b.getLocal("exception"))));
        return method;
    }

    private MethodNode genAdaptFieldHandleMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.adaptFieldHandle.name(),
                vmLayout.adaptFieldHandle.descriptor(),
                new String[]{"java/lang/IllegalAccessException"});
        AdvIBdr ib = new AdvIBdr(method);
        Local field = ib.getLocal("field", "java/lang/reflect/Field", 0);
        Local isStatic = ib.getLocal("isStatic", "Z", 1);
        Local setter = ib.getLocal("setter", "Z", 2);
        Local handle = ib.getLocal("handle", "java/lang/invoke/MethodHandle", 3);

        ib.ifElse(
                AdvIBdr.isFalse(setter),
                b -> {
                    b.set(handle, AdvIBdr.callVirtual(
                            AdvIBdr.callStatic("java/lang/invoke/MethodHandles", "lookup", "java/lang/invoke/MethodHandles$Lookup"),
                            "java/lang/invoke/MethodHandles$Lookup",
                            "unreflectGetter",
                            "java/lang/invoke/MethodHandle",
                            field));
                    b.ifCondition(AdvIBdr.isTrue(isStatic), bb -> dropLeadingObjectArgument(bb, handle));
                    b.returnValue(AdvIBdr.callVirtual(
                            handle,
                            "java/lang/invoke/MethodHandle",
                            "asType",
                            "java/lang/invoke/MethodHandle",
                            getterHandleType()));
                },
                b -> {
                    b.set(handle, AdvIBdr.callVirtual(
                            AdvIBdr.callStatic("java/lang/invoke/MethodHandles", "lookup", "java/lang/invoke/MethodHandles$Lookup"),
                            "java/lang/invoke/MethodHandles$Lookup",
                            "unreflectSetter",
                            "java/lang/invoke/MethodHandle",
                            field));
                    b.ifCondition(AdvIBdr.isTrue(isStatic), bb -> dropLeadingObjectArgument(bb, handle));
                    b.returnValue(AdvIBdr.callVirtual(
                            handle,
                            "java/lang/invoke/MethodHandle",
                            "asType",
                            "java/lang/invoke/MethodHandle",
                            methodType(voidClass(), objectClass(), classArray(b, "setterParameters", objectClass()))));
                });
        return method;
    }

    private MethodNode genUnsafeMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.unsafe.name(),
                vmLayout.unsafe.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local field = ib.getLocal("field", "java/lang/reflect/Field", 0);

        ib.tryCatch(
                b -> {
                    b.set(field, AdvIBdr.callVirtual(
                            AdvIBdr.constant(org.objectweb.asm.Type.getObjectType("sun/misc/Unsafe")),
                            "java/lang/Class",
                            "getDeclaredField",
                            "java/lang/reflect/Field",
                            AdvIBdr.constant("theUnsafe")));
                    b.directCall(AdvIBdr.callVirtual(field, "java/lang/reflect/Field", "setAccessible", "V", AdvIBdr.constant(true)));
                    b.returnValue(AdvIBdr.cast(
                            AdvIBdr.callVirtual(field, "java/lang/reflect/Field", "get", "java/lang/Object", AdvIBdr.constant(null)),
                            "sun/misc/Unsafe"));
                },
                "java/lang/ReflectiveOperationException",
                "exception",
                b -> b.throwValue(illegalStateException(b.getLocal("exception"))));
        return method;
    }

    private MethodNode genUnsafeSetStaticFieldMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.unsafeSetStaticField.name(),
                vmLayout.unsafeSetStaticField.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local field = ib.getLocal("field", "java/lang/reflect/Field", 0);
        Local value = ib.getLocal("value", "java/lang/Object", 1);
        Local unsafe = ib.getLocal("unsafe", "sun/misc/Unsafe", 2);
        Local base = ib.getLocal("base", "java/lang/Object", 3);
        Local offset = ib.getLocal("offset", "J", 4);
        Local type = ib.getLocal("type", "java/lang/Class", 6);

        ib.set(unsafe, AdvIBdr.callStatic(vmLayout.owner, vmLayout.unsafe.name(), "sun/misc/Unsafe"));
        ib.set(base, AdvIBdr.callVirtual(unsafe, "sun/misc/Unsafe", "staticFieldBase", "java/lang/Object", field));
        ib.set(offset, AdvIBdr.callVirtual(unsafe, "sun/misc/Unsafe", "staticFieldOffset", "J", field));
        ib.set(type, AdvIBdr.callVirtual(field, "java/lang/reflect/Field", "getType", "java/lang/Class"));
        ib.ifCondition(AdvIBdr.equal(type, primitiveType("java/lang/Boolean")), b -> {
            b.directCall(AdvIBdr.callVirtual(unsafe, "sun/misc/Unsafe", "putBoolean", "V", base, offset, AdvIBdr.unbox(value, "Z")));
            b.returnVoid();
        });
        ib.ifCondition(AdvIBdr.equal(type, primitiveType("java/lang/Character")), b -> {
            b.directCall(AdvIBdr.callVirtual(unsafe, "sun/misc/Unsafe", "putChar", "V", base, offset, AdvIBdr.unbox(value, "C")));
            b.returnVoid();
        });
        ib.ifCondition(AdvIBdr.equal(type, primitiveType("java/lang/Byte")), b -> {
            b.directCall(AdvIBdr.callVirtual(unsafe, "sun/misc/Unsafe", "putByte", "V", base, offset, AdvIBdr.unbox(value, "B")));
            b.returnVoid();
        });
        ib.ifCondition(AdvIBdr.equal(type, primitiveType("java/lang/Short")), b -> {
            b.directCall(AdvIBdr.callVirtual(unsafe, "sun/misc/Unsafe", "putShort", "V", base, offset, AdvIBdr.unbox(value, "S")));
            b.returnVoid();
        });
        ib.ifCondition(AdvIBdr.equal(type, primitiveType("java/lang/Integer")), b -> {
            b.directCall(AdvIBdr.callVirtual(unsafe, "sun/misc/Unsafe", "putInt", "V", base, offset, AdvIBdr.unbox(value, "I")));
            b.returnVoid();
        });
        ib.ifCondition(AdvIBdr.equal(type, primitiveType("java/lang/Long")), b -> {
            b.directCall(AdvIBdr.callVirtual(unsafe, "sun/misc/Unsafe", "putLong", "V", base, offset, AdvIBdr.unbox(value, "J")));
            b.returnVoid();
        });
        ib.ifCondition(AdvIBdr.equal(type, primitiveType("java/lang/Float")), b -> {
            b.directCall(AdvIBdr.callVirtual(unsafe, "sun/misc/Unsafe", "putFloat", "V", base, offset, AdvIBdr.unbox(value, "F")));
            b.returnVoid();
        });
        ib.ifCondition(AdvIBdr.equal(type, primitiveType("java/lang/Double")), b -> {
            b.directCall(AdvIBdr.callVirtual(unsafe, "sun/misc/Unsafe", "putDouble", "V", base, offset, AdvIBdr.unbox(value, "D")));
            b.returnVoid();
        });
        ib.directCall(AdvIBdr.callVirtual(unsafe, "sun/misc/Unsafe", "putObject", "V", base, offset, value));
        ib.returnVoid();
        return method;
    }

    private MethodNode genFindFieldMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.findField.name(),
                vmLayout.findField.descriptor(),
                new String[]{"java/lang/NoSuchFieldException"});
        AdvIBdr ib = new AdvIBdr(method);
        Local ownerClass = ib.getLocal("ownerClass", "java/lang/Class", 0);
        Local name = ib.getLocal("name", "java/lang/String", 1);
        Local interfaces = ib.getLocal("interfaces", "[Ljava/lang/Class;", 2);
        Local index = ib.getLocal("index", "I", 3);
        Local superClass = ib.getLocal("superClass", "java/lang/Class", 5);

        ib.tryCatch(
                b -> b.returnValue(AdvIBdr.callVirtual(
                        ownerClass,
                        "java/lang/Class",
                        "getDeclaredField",
                        "java/lang/reflect/Field",
                        name)),
                "java/lang/NoSuchFieldException",
                "ignored",
                b -> {});

        ib.set(interfaces, AdvIBdr.callVirtual(ownerClass, "java/lang/Class", "getInterfaces", "[Ljava/lang/Class;"));
        ib.forLoop(
                b -> b.set(index, AdvIBdr.constant(0)),
                AdvIBdr.lessThan(index, AdvIBdr.arrayLength(interfaces)),
                b -> b.increment(index, 1),
                b -> b.tryCatch(
                        tryFind -> tryFind.returnValue(AdvIBdr.callStatic(
                                vmLayout.owner,
                                vmLayout.findField.name(),
                                "java/lang/reflect/Field",
                                AdvIBdr.arrayAt(interfaces, index),
                                name)),
                        "java/lang/NoSuchFieldException",
                        "ignored",
                        ignored -> {}));

        ib.set(superClass, AdvIBdr.callVirtual(ownerClass, "java/lang/Class", "getSuperclass", "java/lang/Class"));
        ib.ifCondition(
                AdvIBdr.notNull(superClass),
                b -> b.returnValue(AdvIBdr.callStatic(vmLayout.owner, vmLayout.findField.name(), "java/lang/reflect/Field", superClass, name)));
        ib.throwValue(AdvIBdr.newObject("java/lang/NoSuchFieldException", name));
        return method;
    }

    private MethodNode genFindMethodMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.findMethod.name(),
                vmLayout.findMethod.descriptor(),
                new String[]{"java/lang/NoSuchMethodException"});
        AdvIBdr ib = new AdvIBdr(method);
        Local ownerClass = ib.getLocal("ownerClass", "java/lang/Class", 0);
        Local name = ib.getLocal("name", "java/lang/String", 1);
        Local parameterTypes = ib.getLocal("parameterTypes", "[Ljava/lang/Class;", 2);
        Local returnType = ib.getLocal("returnType", "java/lang/Class", 3);
        Local declaredMethods = ib.getLocal("declaredMethods", "[Ljava/lang/reflect/Method;", 4);
        Local candidate = ib.getLocal("candidate", "java/lang/reflect/Method", 5);
        Local interfaces = ib.getLocal("interfaces", "[Ljava/lang/Class;", 6);
        Local index = ib.getLocal("index", "I", 7);
        Local superClass = ib.getLocal("superClass", "java/lang/Class", 9);

        ib.set(declaredMethods, AdvIBdr.callVirtual(
                ownerClass,
                "java/lang/Class",
                "getDeclaredMethods",
                "[Ljava/lang/reflect/Method;"));
        ib.forLoop(
                b -> b.set(index, AdvIBdr.constant(0)),
                AdvIBdr.lessThan(index, AdvIBdr.arrayLength(declaredMethods)),
                b -> b.increment(index, 1),
                b -> {
                    b.set(candidate, AdvIBdr.arrayAt(declaredMethods, index));
                    b.ifCondition(
                            AdvIBdr.and(
                                    AdvIBdr.isTrue(AdvIBdr.callVirtual(
                                            name,
                                            "java/lang/String",
                                            "equals",
                                            "Z",
                                            AdvIBdr.cast(
                                                    AdvIBdr.callVirtual(candidate, "java/lang/reflect/Method", "getName", "java/lang/String"),
                                                    "java/lang/Object"))),
                                    AdvIBdr.and(
                                            AdvIBdr.equal(
                                                    AdvIBdr.callVirtual(candidate, "java/lang/reflect/Method", "getReturnType", "java/lang/Class"),
                                                    returnType),
                                            AdvIBdr.isTrue(AdvIBdr.callStatic(
                                                    "java/util/Arrays",
                                                    "equals",
                                                    "Z",
                                                    AdvIBdr.cast(
                                                            AdvIBdr.callVirtual(candidate, "java/lang/reflect/Method", "getParameterTypes", "[Ljava/lang/Class;"),
                                                            "[Ljava/lang/Object;"),
                                                    AdvIBdr.cast(parameterTypes, "[Ljava/lang/Object;"))))),
                            found -> found.returnValue(candidate));
                });

        ib.set(interfaces, AdvIBdr.callVirtual(ownerClass, "java/lang/Class", "getInterfaces", "[Ljava/lang/Class;"));
        ib.forLoop(
                b -> b.set(index, AdvIBdr.constant(0)),
                AdvIBdr.lessThan(index, AdvIBdr.arrayLength(interfaces)),
                b -> b.increment(index, 1),
                b -> b.tryCatch(
                        tryFind -> tryFind.returnValue(AdvIBdr.callStatic(
                                vmLayout.owner,
                                vmLayout.findMethod.name(),
                                "java/lang/reflect/Method",
                                AdvIBdr.arrayAt(interfaces, index),
                                name,
                                parameterTypes,
                                returnType)),
                        "java/lang/NoSuchMethodException",
                        "ignored",
                        ignored -> {}));

        ib.set(superClass, AdvIBdr.callVirtual(ownerClass, "java/lang/Class", "getSuperclass", "java/lang/Class"));
        ib.ifCondition(
                AdvIBdr.notNull(superClass),
                b -> b.returnValue(AdvIBdr.callStatic(
                        vmLayout.owner,
                        vmLayout.findMethod.name(),
                        "java/lang/reflect/Method",
                        superClass,
                        name,
                        parameterTypes,
                        returnType)));
        ib.ifCondition(
                AdvIBdr.isTrue(AdvIBdr.callVirtual(ownerClass, "java/lang/Class", "isInterface", "Z")),
                b -> b.tryCatch(
                        tryObjectMethod -> {
                            tryObjectMethod.set(candidate, AdvIBdr.callVirtual(
                                    objectClass(),
                                    "java/lang/Class",
                                    "getMethod",
                                    "java/lang/reflect/Method",
                                    name,
                                    parameterTypes));
                            tryObjectMethod.ifCondition(
                                    AdvIBdr.equal(
                                            AdvIBdr.callVirtual(candidate, "java/lang/reflect/Method", "getReturnType", "java/lang/Class"),
                                            returnType),
                                    found -> found.returnValue(candidate));
                        },
                        "java/lang/NoSuchMethodException",
                        "ignored",
                        ignored -> {}));
        ib.throwValue(AdvIBdr.newObject(
                "java/lang/NoSuchMethodException",
                stringConcat(
                        AdvIBdr.callVirtual(ownerClass, "java/lang/Class", "getName", "java/lang/String"),
                        AdvIBdr.constant("."),
                        name)));
        return method;
    }

    private MethodNode genInvokeMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.invoke.name(),
                vmLayout.invoke.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local owner = ib.getLocal("owner", "java/lang/String", 0);
        Local name = ib.getLocal("name", "java/lang/String", 1);
        Local methodType = ib.getLocal("methodType", "java/lang/invoke/MethodType", 2);
        Local isStatic = ib.getLocal("isStatic", "Z", 3);
        Local receiver = ib.getLocal("receiver", "java/lang/Object", 4);
        Local arguments = ib.getLocal("arguments", "[Ljava/lang/Object;", 5);
        Local key = ib.getLocal("key", "java/lang/String", 6);
        Local target = ib.getLocal("target", "java/lang/invoke/MethodHandle", 7);
        Local ownerClass = ib.getLocal("ownerClass", "java/lang/Class", 8);
        Local exception = ib.getLocal("exception", "java/lang/Throwable", 9);
        Local reflectedMethod = ib.getLocal("reflectedMethod", "java/lang/reflect/Method", 10);
        Local accessMode = ib.getLocal("accessMode", "java/lang/invoke/VarHandle$AccessMode", 11);

        ib.ifCondition(
                AdvIBdr.and(
                        AdvIBdr.isFalse(isStatic),
                        AdvIBdr.and(
                                AdvIBdr.isTrue(AdvIBdr.callVirtual(
                                        name,
                                        "java/lang/String",
                                        "equals",
                                        "Z",
                                        AdvIBdr.cast(AdvIBdr.constant("clone"), "java/lang/Object"))),
                                AdvIBdr.and(
                                        AdvIBdr.equal(
                                                AdvIBdr.callVirtual(methodType, "java/lang/invoke/MethodType", "parameterCount", "I"),
                                                AdvIBdr.constant(0)),
                                        AdvIBdr.isTrue(AdvIBdr.callVirtual(
                                                AdvIBdr.callVirtual(receiver, "java/lang/Object", "getClass", "java/lang/Class"),
                                                "java/lang/Class",
                                                "isArray",
                                                "Z"))))),
                b -> b.returnValue(AdvIBdr.callStatic(vmLayout.owner, vmLayout.cloneArray.name(), "java/lang/Object", receiver)));

        ib.ifCondition(
                isMethodHandleInvoke(owner, name, isStatic),
                b -> {
                    coerceArguments(b, arguments, methodType);
                    b.tryCatch(
                            invokeMethodHandle -> invokeMethodHandle.returnValue(AdvIBdr.callVirtual(
                                    asArrayInvokerHandle(
                                            AdvIBdr.callVirtual(
                                                    AdvIBdr.callVirtual(
                                                            AdvIBdr.cast(receiver, "java/lang/invoke/MethodHandle"),
                                                            "java/lang/invoke/MethodHandle",
                                                            "asType",
                                                            "java/lang/invoke/MethodHandle",
                                                            methodType),
                                                    "java/lang/invoke/MethodHandle",
                                                    "asSpreader",
                                                    "java/lang/invoke/MethodHandle",
                                                    objectArrayClass(),
                                                    AdvIBdr.callVirtual(methodType, "java/lang/invoke/MethodType", "parameterCount", "I"))),
                                    "java/lang/invoke/MethodHandle",
                                    "invokeExact",
                                    "java/lang/Object",
                                    arguments)),
                            "java/lang/Throwable",
                            "throwable",
                            caught -> caught.throwValue(rethrow(caught.getLocal("throwable"))));
                });

        ib.set(accessMode, AdvIBdr.nullValue("java/lang/invoke/VarHandle$AccessMode"));
        ib.ifCondition(
                isVarHandleInvocation(owner, isStatic),
                b -> b.tryCatch(
                        resolveMode -> resolveMode.set(accessMode, AdvIBdr.callStatic(
                                "java/lang/invoke/VarHandle$AccessMode",
                                "valueFromMethodName",
                                "java/lang/invoke/VarHandle$AccessMode",
                                name)),
                        "java/lang/IllegalArgumentException",
                        "ignored",
                        ignored -> {}));
        ib.ifCondition(
                AdvIBdr.notNull(accessMode),
                b -> {
                    coerceArguments(b, arguments, methodType);
                    b.set(target, AdvIBdr.callVirtual(
                            AdvIBdr.callVirtual(
                                    AdvIBdr.callVirtual(
                                            AdvIBdr.callVirtual(
                                                    AdvIBdr.cast(receiver, "java/lang/invoke/VarHandle"),
                                                    "java/lang/invoke/VarHandle",
                                                    "toMethodHandle",
                                                    "java/lang/invoke/MethodHandle",
                                                    accessMode),
                                            "java/lang/invoke/MethodHandle",
                                            "asType",
                                            "java/lang/invoke/MethodHandle",
                                            methodType),
                                    "java/lang/invoke/MethodHandle",
                                    "asFixedArity",
                                    "java/lang/invoke/MethodHandle"),
                            "java/lang/invoke/MethodHandle",
                            "asSpreader",
                            "java/lang/invoke/MethodHandle",
                            objectArrayClass(),
                            AdvIBdr.callVirtual(methodType, "java/lang/invoke/MethodType", "parameterCount", "I")));
                    b.tryCatch(
                            invokeVarHandle -> invokeVarHandle.returnValue(AdvIBdr.callVirtual(
                                    asArrayInvokerHandle(target),
                                    "java/lang/invoke/MethodHandle",
                                    "invokeExact",
                                    "java/lang/Object",
                                    arguments)),
                            "java/lang/Throwable",
                            "throwable",
                            caught -> caught.throwValue(rethrow(caught.getLocal("throwable"))));
                });

        ib.set(key, methodHandleKey(owner, name, methodType, isStatic));
        ib.set(target, AdvIBdr.cast(mapGet(AdvIBdr.staticField(vmLayout.methodHandles), key), "java/lang/invoke/MethodHandle"));

        ib.ifCondition(
                AdvIBdr.isNull(target),
                b -> {
                    b.set(ownerClass, AdvIBdr.nullValue("java/lang/Class"));
                    b.set(exception, AdvIBdr.nullValue("java/lang/Throwable"));
                    b.tryCatch(
                            tryReflect -> {
                                tryReflect.set(ownerClass, AdvIBdr.callStatic(vmLayout.owner, vmLayout.loadOwner.name(), "java/lang/Class", owner));
                                tryReflect.set(reflectedMethod, AdvIBdr.callStatic(
                                        vmLayout.owner,
                                        vmLayout.findMethod.name(),
                                        "java/lang/reflect/Method",
                                        ownerClass,
                                        name,
                                        AdvIBdr.callVirtual(methodType, "java/lang/invoke/MethodType", "parameterArray", "[Ljava/lang/Class;"),
                                        AdvIBdr.callVirtual(methodType, "java/lang/invoke/MethodType", "returnType", "java/lang/Class")));
                                cacheAdaptedMethodHandle(tryReflect, reflectedMethod, ownerClass, name, methodType, isStatic, key, target);
                            },
                            "java/lang/Throwable",
                            "caught",
                            caught -> caught.set(exception, caught.getLocal("caught")));

                    b.ifCondition(
                            AdvIBdr.and(AdvIBdr.isNull(target), AdvIBdr.isInstanceOf(exception, "java/lang/NoSuchMethodException")),
                            publicLookup -> publicLookup.tryCatch(
                                    tryPublic -> {
                                        tryPublic.set(reflectedMethod, AdvIBdr.callVirtual(
                                                ownerClass,
                                                "java/lang/Class",
                                                "getMethod",
                                                "java/lang/reflect/Method",
                                                name,
                                                AdvIBdr.callVirtual(methodType, "java/lang/invoke/MethodType", "parameterArray", "[Ljava/lang/Class;")));
                                        cacheAdaptedMethodHandle(tryPublic, reflectedMethod, ownerClass, name, methodType, isStatic, key, target);
                                    },
                                    "java/lang/ReflectiveOperationException",
                                    "caught",
                                    caught -> caught.set(exception, caught.getLocal("caught"))));

                    b.ifCondition(
                            AdvIBdr.isNull(target),
                            miss -> miss.ifElse(
                                    isThrowableNamed(exception, "java.lang.reflect.InaccessibleObjectException"),
                                    direct -> {
                                        direct.set(target, AdvIBdr.callStatic(
                                                vmLayout.owner,
                                                vmLayout.adaptDirectMethodHandle.name(),
                                                "java/lang/invoke/MethodHandle",
                                                ownerClass,
                                                name,
                                                methodType,
                                                isStatic));
                                        direct.directCall(mapPut(AdvIBdr.staticField(vmLayout.methodHandles), key, target));
                                    },
                                    failure -> failure.throwValue(illegalStateException(exception))));
                });

        coerceArguments(ib, arguments, methodType);
        ib.tryCatch(
                b -> b.returnValue(AdvIBdr.callVirtual(
                        target,
                        "java/lang/invoke/MethodHandle",
                        "invokeExact",
                        "java/lang/Object",
                        receiver,
                        arguments)),
                "java/lang/Throwable",
                "throwable",
                b -> b.throwValue(rethrow(b.getLocal("throwable"))));
        return method;
    }

    private static Condition isMethodHandleInvoke(Local owner, Local name, Local isStatic)
    {
        return AdvIBdr.and(
                AdvIBdr.isFalse(isStatic),
                AdvIBdr.and(
                        AdvIBdr.isTrue(AdvIBdr.callVirtual(
                                owner,
                                "java/lang/String",
                                "equals",
                                "Z",
                                AdvIBdr.cast(AdvIBdr.constant("java/lang/invoke/MethodHandle"), "java/lang/Object"))),
                        AdvIBdr.or(
                                AdvIBdr.isTrue(AdvIBdr.callVirtual(
                                        name,
                                        "java/lang/String",
                                        "equals",
                                        "Z",
                                        AdvIBdr.cast(AdvIBdr.constant("invoke"), "java/lang/Object"))),
                                AdvIBdr.isTrue(AdvIBdr.callVirtual(
                                        name,
                                        "java/lang/String",
                                        "equals",
                                        "Z",
                                        AdvIBdr.cast(AdvIBdr.constant("invokeExact"), "java/lang/Object"))))));
    }

    private static Condition isVarHandleInvocation(Local owner, Local isStatic)
    {
        return AdvIBdr.and(
                AdvIBdr.isFalse(isStatic),
                AdvIBdr.isTrue(AdvIBdr.callVirtual(
                        owner,
                        "java/lang/String",
                        "equals",
                        "Z",
                        AdvIBdr.cast(AdvIBdr.constant("java/lang/invoke/VarHandle"), "java/lang/Object"))));
    }

    private MethodNode genConstructMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.construct.name(),
                vmLayout.construct.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local owner = ib.getLocal("owner", "java/lang/String", 0);
        Local methodType = ib.getLocal("methodType", "java/lang/invoke/MethodType", 1);
        Local arguments = ib.getLocal("arguments", "[Ljava/lang/Object;", 2);
        Local key = ib.getLocal("key", "java/lang/String", 3);
        Local target = ib.getLocal("target", "java/lang/invoke/MethodHandle", 4);
        Local ownerClass = ib.getLocal("ownerClass", "java/lang/Class", 5);
        Local constructor = ib.getLocal("constructor", "java/lang/reflect/Constructor", 6);

        ib.set(key, stringConcat(AdvIBdr.constant("<init>:"), owner, methodType));
        ib.set(target, AdvIBdr.cast(mapGet(AdvIBdr.staticField(vmLayout.methodHandles), key), "java/lang/invoke/MethodHandle"));

        ib.ifCondition(
                AdvIBdr.isNull(target),
                b -> b.tryCatch(
                        resolve -> {
                            resolve.set(ownerClass, AdvIBdr.callStatic(vmLayout.owner, vmLayout.loadOwner.name(), "java/lang/Class", owner));
                            resolve.set(constructor, AdvIBdr.callVirtual(
                                    ownerClass,
                                    "java/lang/Class",
                                    "getDeclaredConstructor",
                                    "java/lang/reflect/Constructor",
                                    AdvIBdr.callVirtual(methodType, "java/lang/invoke/MethodType", "parameterArray", "[Ljava/lang/Class;")));
                            resolve.directCall(AdvIBdr.callVirtual(constructor, "java/lang/reflect/Constructor", "setAccessible", "V", AdvIBdr.constant(true)));
                            resolve.set(target, AdvIBdr.callStatic(
                                    vmLayout.owner,
                                    vmLayout.adaptConstructorHandle.name(),
                                    "java/lang/invoke/MethodHandle",
                                    constructor,
                                    AdvIBdr.callVirtual(methodType, "java/lang/invoke/MethodType", "parameterCount", "I")));
                            resolve.directCall(mapPut(AdvIBdr.staticField(vmLayout.methodHandles), key, target));
                        },
                        "java/lang/Throwable",
                        "throwable",
                        caught -> caught.throwValue(rethrow(caught.getLocal("throwable")))));

        coerceArguments(ib, arguments, methodType);
        ib.tryCatch(
                b -> b.returnValue(AdvIBdr.callVirtual(
                        target,
                        "java/lang/invoke/MethodHandle",
                        "invokeExact",
                        "java/lang/Object",
                        AdvIBdr.nullValue("java/lang/Object"),
                        arguments)),
                "java/lang/Throwable",
                "throwable",
                b -> b.throwValue(rethrow(b.getLocal("throwable"))));
        return method;
    }

    private MethodNode genAdaptMethodHandleMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.adaptMethodHandle.name(),
                vmLayout.adaptMethodHandle.descriptor(),
                new String[]{"java/lang/IllegalAccessException"});
        AdvIBdr ib = new AdvIBdr(method);
        Local methodRef = ib.getLocal("method", "java/lang/reflect/Method", 0);
        Local isStatic = ib.getLocal("isStatic", "Z", 1);
        Local parameterCount = ib.getLocal("parameterCount", "I", 2);
        Local handle = ib.getLocal("handle", "java/lang/invoke/MethodHandle", 3);

        ib.set(handle, AdvIBdr.callVirtual(
                AdvIBdr.callVirtual(
                        AdvIBdr.callVirtual(
                                AdvIBdr.callStatic("java/lang/invoke/MethodHandles", "lookup", "java/lang/invoke/MethodHandles$Lookup"),
                                "java/lang/invoke/MethodHandles$Lookup",
                                "unreflect",
                                "java/lang/invoke/MethodHandle",
                                methodRef),
                        "java/lang/invoke/MethodHandle",
                        "asFixedArity",
                        "java/lang/invoke/MethodHandle"),
                "java/lang/invoke/MethodHandle",
                "asSpreader",
                "java/lang/invoke/MethodHandle",
                objectArrayClass(),
                parameterCount));
        ib.ifCondition(AdvIBdr.isTrue(isStatic), b -> dropLeadingObjectArgument(b, handle));
        ib.returnValue(asInvokerHandle(handle, classArray(ib, "invokerParameters", objectArrayClass())));
        return method;
    }

    private MethodNode genAdaptDirectMethodHandleMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.adaptDirectMethodHandle.name(),
                vmLayout.adaptDirectMethodHandle.descriptor(),
                new String[]{"java/lang/IllegalAccessException", "java/lang/NoSuchMethodException"});
        AdvIBdr ib = new AdvIBdr(method);
        Local ownerClass = ib.getLocal("ownerClass", "java/lang/Class", 0);
        Local name = ib.getLocal("name", "java/lang/String", 1);
        Local methodType = ib.getLocal("methodType", "java/lang/invoke/MethodType", 2);
        Local isStatic = ib.getLocal("isStatic", "Z", 3);
        Local lookup = ib.getLocal("lookup", "java/lang/invoke/MethodHandles$Lookup", 4);
        Local handle = ib.getLocal("handle", "java/lang/invoke/MethodHandle", 5);

        ib.set(lookup, AdvIBdr.callStatic(
                "java/lang/invoke/MethodHandles",
                "privateLookupIn",
                "java/lang/invoke/MethodHandles$Lookup",
                ownerClass,
                AdvIBdr.callStatic("java/lang/invoke/MethodHandles", "lookup", "java/lang/invoke/MethodHandles$Lookup")));
        ib.ifElse(
                AdvIBdr.isTrue(isStatic),
                b -> {
                    b.set(handle, directHandle(lookup, ownerClass, name, methodType, true));
                    dropLeadingObjectArgument(b, handle);
                    b.returnValue(asInvokerHandle(handle, classArray(b, "staticInvokerParameters", objectArrayClass())));
                },
                b -> {
                    b.set(handle, directHandle(lookup, ownerClass, name, methodType, false));
                    b.returnValue(asInvokerHandle(handle, classArray(b, "virtualInvokerParameters", objectArrayClass())));
                });
        return method;
    }

    private MethodNode genAdaptConstructorHandleMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.adaptConstructorHandle.name(),
                vmLayout.adaptConstructorHandle.descriptor(),
                new String[]{"java/lang/IllegalAccessException"});
        AdvIBdr ib = new AdvIBdr(method);
        Local constructor = ib.getLocal("constructor", "java/lang/reflect/Constructor", 0);
        Local parameterCount = ib.getLocal("parameterCount", "I", 1);
        Local handle = ib.getLocal("handle", "java/lang/invoke/MethodHandle", 2);

        ib.set(handle, AdvIBdr.callVirtual(
                AdvIBdr.callVirtual(
                        AdvIBdr.callVirtual(
                                AdvIBdr.callStatic("java/lang/invoke/MethodHandles", "lookup", "java/lang/invoke/MethodHandles$Lookup"),
                                "java/lang/invoke/MethodHandles$Lookup",
                                "unreflectConstructor",
                                "java/lang/invoke/MethodHandle",
                                constructor),
                        "java/lang/invoke/MethodHandle",
                        "asFixedArity",
                        "java/lang/invoke/MethodHandle"),
                "java/lang/invoke/MethodHandle",
                "asSpreader",
                "java/lang/invoke/MethodHandle",
                objectArrayClass(),
                parameterCount));
        dropLeadingObjectArgument(ib, handle);
        ib.returnValue(asInvokerHandle(handle, classArray(ib, "constructorInvokerParameters", objectArrayClass())));
        return method;
    }

    private MethodNode genCoerceArgumentMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.coerceArgument.name(),
                vmLayout.coerceArgument.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local value = ib.getLocal("value", "java/lang/Object", 0);
        Local targetType = ib.getLocal("targetType", "java/lang/Class", 1);

        ib.ifCondition(AdvIBdr.equal(targetType, primitiveType("java/lang/Boolean")), b -> b.returnValue(AdvIBdr.callStatic(
                "java/lang/Boolean", "valueOf", "java/lang/Boolean", AdvIBdr.unbox(value, "Z"))));
        ib.ifCondition(AdvIBdr.equal(targetType, primitiveType("java/lang/Character")), b -> b.returnValue(AdvIBdr.callStatic(
                "java/lang/Character", "valueOf", "java/lang/Character", AdvIBdr.cast(AdvIBdr.unbox(value, "I"), "C"))));
        ib.ifCondition(AdvIBdr.equal(targetType, primitiveType("java/lang/Byte")), b -> b.returnValue(AdvIBdr.callStatic(
                "java/lang/Byte", "valueOf", "java/lang/Byte", AdvIBdr.cast(AdvIBdr.unbox(value, "I"), "B"))));
        ib.ifCondition(AdvIBdr.equal(targetType, primitiveType("java/lang/Short")), b -> b.returnValue(AdvIBdr.callStatic(
                "java/lang/Short", "valueOf", "java/lang/Short", AdvIBdr.cast(AdvIBdr.unbox(value, "I"), "S"))));
        ib.ifCondition(AdvIBdr.equal(targetType, primitiveType("java/lang/Integer")), b -> b.returnValue(AdvIBdr.callStatic(
                "java/lang/Integer", "valueOf", "java/lang/Integer", AdvIBdr.unbox(value, "I"))));
        ib.ifCondition(AdvIBdr.equal(targetType, primitiveType("java/lang/Long")), b -> b.returnValue(AdvIBdr.callStatic(
                "java/lang/Long", "valueOf", "java/lang/Long", AdvIBdr.callVirtual(AdvIBdr.cast(value, "java/lang/Number"), "java/lang/Number", "longValue", "J"))));
        ib.ifCondition(AdvIBdr.equal(targetType, primitiveType("java/lang/Float")), b -> b.returnValue(AdvIBdr.callStatic(
                "java/lang/Float", "valueOf", "java/lang/Float", AdvIBdr.callVirtual(AdvIBdr.cast(value, "java/lang/Number"), "java/lang/Number", "floatValue", "F"))));
        ib.ifCondition(AdvIBdr.equal(targetType, primitiveType("java/lang/Double")), b -> b.returnValue(AdvIBdr.callStatic(
                "java/lang/Double", "valueOf", "java/lang/Double", AdvIBdr.callVirtual(AdvIBdr.cast(value, "java/lang/Number"), "java/lang/Number", "doubleValue", "D"))));
        ib.returnValue(value);
        return method;
    }

    private MethodNode genCloneArrayMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.cloneArray.name(),
                vmLayout.cloneArray.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local array = ib.getLocal("array", "java/lang/Object", 0);
        Local length = ib.getLocal("length", "I", 1);
        Local clone = ib.getLocal("clone", "java/lang/Object", 2);

        ib.set(length, AdvIBdr.callStatic("java/lang/reflect/Array", "getLength", "I", array));
        ib.set(clone, AdvIBdr.callStatic(
                "java/lang/reflect/Array",
                "newInstance",
                "java/lang/Object",
                AdvIBdr.callVirtual(
                        AdvIBdr.callVirtual(array, "java/lang/Object", "getClass", "java/lang/Class"),
                        "java/lang/Class",
                        "getComponentType",
                        "java/lang/Class"),
                length));
        ib.directCall(AdvIBdr.callStatic(
                "java/lang/System",
                "arraycopy",
                "V",
                array,
                AdvIBdr.constant(0),
                clone,
                AdvIBdr.constant(0),
                length));
        ib.returnValue(clone);
        return method;
    }

    private MethodNode genLoadOwnerMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.loadOwner.name(),
                vmLayout.loadOwner.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local owner = ib.getLocal("owner", "java/lang/String", 0);
        ib.returnValue(AdvIBdr.callStatic(
                vmLayout.owner,
                vmLayout.loadOwnerWithLoader.name(),
                "java/lang/Class",
                owner,
                AdvIBdr.callVirtual(
                        AdvIBdr.constant(org.objectweb.asm.Type.getObjectType(className())),
                        "java/lang/Class",
                        "getClassLoader",
                        "java/lang/ClassLoader")));
        return method;
    }

    private MethodNode genLoadOwnerWithLoaderMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.loadOwnerWithLoader.name(),
                vmLayout.loadOwnerWithLoader.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local owner = ib.getLocal("owner", "java/lang/String", 0);
        Local loader = ib.getLocal("loader", "java/lang/ClassLoader", 1);
        Local normalized = ib.getLocal("normalized", "java/lang/String", 2);

        ib.ifCondition(
                AdvIBdr.equal(AdvIBdr.callVirtual(owner, "java/lang/String", "length", "I"), AdvIBdr.constant(1)),
                b -> b.switchLookup(
                        AdvIBdr.callVirtual(owner, "java/lang/String", "charAt", "C", AdvIBdr.constant(0)),
                        null,
                        AdvIBdr.switchCase('Z', bb -> bb.returnValue(primitiveType("java/lang/Boolean"))),
                        AdvIBdr.switchCase('C', bb -> bb.returnValue(primitiveType("java/lang/Character"))),
                        AdvIBdr.switchCase('B', bb -> bb.returnValue(primitiveType("java/lang/Byte"))),
                        AdvIBdr.switchCase('S', bb -> bb.returnValue(primitiveType("java/lang/Short"))),
                        AdvIBdr.switchCase('I', bb -> bb.returnValue(primitiveType("java/lang/Integer"))),
                        AdvIBdr.switchCase('F', bb -> bb.returnValue(primitiveType("java/lang/Float"))),
                        AdvIBdr.switchCase('J', bb -> bb.returnValue(primitiveType("java/lang/Long"))),
                        AdvIBdr.switchCase('D', bb -> bb.returnValue(primitiveType("java/lang/Double"))),
                        AdvIBdr.switchCase('V', bb -> bb.returnValue(primitiveType("java/lang/Void")))));

        ib.ifCondition(
                AdvIBdr.and(
                        AdvIBdr.isTrue(AdvIBdr.callVirtual(owner, "java/lang/String", "startsWith", "Z", AdvIBdr.constant("L"))),
                        AdvIBdr.isTrue(AdvIBdr.callVirtual(owner, "java/lang/String", "endsWith", "Z", AdvIBdr.constant(";")))),
                b -> b.set(owner, AdvIBdr.callVirtual(
                        owner,
                        "java/lang/String",
                        "substring",
                        "java/lang/String",
                        AdvIBdr.constant(1),
                        AdvIBdr.minus(
                                AdvIBdr.callVirtual(owner, "java/lang/String", "length", "I"),
                                AdvIBdr.constant(1)))));

        ib.set(normalized, AdvIBdr.callVirtual(
                owner,
                "java/lang/String",
                "replace",
                "java/lang/String",
                AdvIBdr.constant('/'),
                AdvIBdr.constant('.')));
        ib.tryCatch(
                b -> b.returnValue(classForName(normalized, loader)),
                "java/lang/ClassNotFoundException",
                "exception",
                b -> b.tryCatch(
                        fallback -> fallback.returnValue(classForName(
                                normalized,
                                AdvIBdr.callVirtual(
                                        AdvIBdr.constant(org.objectweb.asm.Type.getObjectType(className())),
                                        "java/lang/Class",
                                        "getClassLoader",
                                        "java/lang/ClassLoader"))),
                        "java/lang/ClassNotFoundException",
                        "fallbackException",
                        fallback -> fallback.throwValue(illegalStateException(b.getLocal("exception")))));
        return method;
    }

    private MethodNode genRethrowMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.rethrow.name(),
                vmLayout.rethrow.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        ib.throwValue(ib.getLocal("throwable", "java/lang/Throwable", 0));
        return method;
    }

    private MethodNode genMonitorForMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.SYNCHRONIZED},
                vmLayout.monitorFor.name(),
                vmLayout.monitorFor.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local monitor = ib.getLocal("monitor", "java/lang/Object", 0);
        Local lock = ib.getLocal("lock", "java/util/concurrent/locks/ReentrantLock", 1);

        ib.ifCondition(AdvIBdr.isNull(monitor), b -> b.throwValue(AdvIBdr.newObject("java/lang/NullPointerException")));
        ib.set(lock, AdvIBdr.cast(mapGet(AdvIBdr.staticField(vmLayout.monitors), monitor), "java/util/concurrent/locks/ReentrantLock"));
        ib.ifCondition(
                AdvIBdr.isNull(lock),
                b -> {
                    b.set(lock, AdvIBdr.newObject("java/util/concurrent/locks/ReentrantLock"));
                    b.directCall(mapPut(AdvIBdr.staticField(vmLayout.monitors), monitor, lock));
                });
        ib.returnValue(lock);
        return method;
    }

    private MethodNode genMonitorEnterMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.monitorEnter.name(),
                vmLayout.monitorEnter.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local monitor = ib.getLocal("monitor", "java/lang/Object", 0);
        ib.directCall(AdvIBdr.callVirtual(
                AdvIBdr.callStatic(vmLayout.owner, vmLayout.monitorFor.name(), "java/util/concurrent/locks/ReentrantLock", monitor),
                "java/util/concurrent/locks/ReentrantLock",
                "lock",
                "V"));
        ib.returnVoid();
        return method;
    }

    private MethodNode genMonitorExitMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.monitorExit.name(),
                vmLayout.monitorExit.descriptor());
        AdvIBdr ib = new AdvIBdr(method);
        Local monitor = ib.getLocal("monitor", "java/lang/Object", 0);
        ib.directCall(AdvIBdr.callVirtual(
                AdvIBdr.callStatic(vmLayout.owner, vmLayout.monitorFor.name(), "java/util/concurrent/locks/ReentrantLock", monitor),
                "java/util/concurrent/locks/ReentrantLock",
                "unlock",
                "V"));
        ib.returnVoid();
        return method;
    }

    private MethodNode genClInitMethod(List<CodePoolGenerator> codePoolGenerators)
    {
        MethodNode initMethod = MethodUtils.newMethodNode(new Acc[]{Acc.STATIC}, "<clinit>", "()V");
        AdvIBdr ib = new AdvIBdr(initMethod);

        Local codePools = ib.var("codePools", "[Ljava/lang/Object;");
        ib.set(codePools, AdvIBdr.newArray("java/lang/Object", AdvIBdr.constant(codePoolGenerators.size())));
        for (int i = 0; i < codePoolGenerators.size(); i++)
        {
            ib.setArray(
                    codePools,
                    AdvIBdr.constant(i),
                    AdvIBdr.staticField(codePoolGenerators.get(i).layout.instance));
        }
        ib.set(AdvIBdr.staticField(vmLayout.codePools), AdvIBdr.callStatic(
                "java/util/Arrays",
                "asList",
                "java/util/List",
                codePools));

        ib.set(AdvIBdr.staticField(vmLayout.fieldHandles), AdvIBdr.newObject("java/util/concurrent/ConcurrentHashMap"));

        ib.set(AdvIBdr.staticField(vmLayout.methodHandles), AdvIBdr.newObject("java/util/concurrent/ConcurrentHashMap"));

        ib.set(AdvIBdr.staticField(vmLayout.methodTypes), AdvIBdr.newObject("java/util/concurrent/ConcurrentHashMap"));

        ib.set(AdvIBdr.staticField(vmLayout.monitors), AdvIBdr.callStatic(
                "java/util/Collections",
                "synchronizedMap",
                "java/util/Map",
                AdvIBdr.cast(AdvIBdr.newObject("java/util/WeakHashMap"), "java/util/Map")));
        structureGeneration.emitClassInitializers(ib);
        ib.returnVoid();
        return initMethod;
    }

    private static void register(InterpretBranch branch)
    {
        for (Opcs opcode : branch.opcodes())
        {
            InterpretBranch previous = branches.put(opcode, branch);

            if (previous != null)
            {
                throw new IllegalStateException(opcode + " is handled by both " + previous.getClass().getName() + " and " + branch.getClass().getName());
            }
        }
    }

    private static Expr mapGet(Expr map, Expr key)
    {
        return AdvIBdr.callInterface(
                map,
                "java/util/Map",
                "get",
                "java/lang/Object",
                AdvIBdr.cast(key, "java/lang/Object"));
    }

    private static Expr mapPut(Expr map, Expr key, Expr value)
    {
        return AdvIBdr.callInterface(
                map,
                "java/util/Map",
                "put",
                "java/lang/Object",
                AdvIBdr.cast(key, "java/lang/Object"),
                AdvIBdr.cast(value, "java/lang/Object"));
    }

    private Expr callProgramArray(Expr program, String methodName)
    {
        return AdvIBdr.callVirtual(program, programLayout.owner, methodName, "[I");
    }

    private Expr callProgramInt(Expr program, String methodName)
    {
        return AdvIBdr.callVirtual(program, programLayout.owner, methodName, "I");
    }

    private Condition featureEnabled(Expr program, int flag)
    {
        return AdvIBdr.notEqual(
                AdvIBdr.bitAnd(
                        callProgramInt(program, programLayout.featureFlags.name()),
                        AdvIBdr.constant(flag)),
                AdvIBdr.constant(0));
    }

    private Expr layoutValue(Expr program, Expr instructionIndex, int logicalField, Expr stateKey)
    {
        return AdvIBdr.callStatic(
                className(),
                vmLayout.layoutValue.name(),
                "I",
                program,
                instructionIndex,
                AdvIBdr.constant(profile.layoutSlot(logicalField)),
                stateKey);
    }

    private Expr blockValue(Expr program, Expr blockIndex, Expr field)
    {
        return AdvIBdr.callStatic(
                className(),
                vmLayout.blockValue.name(),
                "I",
                program,
                blockIndex,
                field);
    }

    private Expr mixCall(Expr key, Expr a, Expr b, Expr c)
    {
        return AdvIBdr.callStatic(className(), vmLayout.mix.name(), "I", key, a, b, c);
    }

    private Expr handlerMixCall(Expr methodKey, Expr handlerSlot, int field)
    {
        return mixCall(
                methodKey,
                handlerSlot,
                AdvIBdr.constant(field),
                AdvIBdr.constant(profile.saltHandler));
    }

    private Expr dispatchKeyExpr(Expr opcode)
    {
        return mixCall(
                AdvIBdr.constant(profile.dispatchSalt),
                opcode,
                AdvIBdr.constant(profile.saltOpcode),
                AdvIBdr.constant(0));
    }

    private int dispatchKey(int opcode)
    {
        return profile.dispatchKey(opcode);
    }

    private static void mixRound(AdvIBdr ib, Local x, Expr value, int salt)
    {
        ib.set(x, AdvIBdr.bitXor(
                x,
                add(
                        value,
                        AdvIBdr.constant(salt),
                        AdvIBdr.shiftLeft(x, AdvIBdr.constant(6)),
                        AdvIBdr.unsignedShiftRight(x, AdvIBdr.constant(2)))));
    }

    private static Expr add(Expr first, Expr... rest)
    {
        Expr result = first;
        for (Expr value : rest)
        {
            result = AdvIBdr.plus(result, value);
        }
        return result;
    }

    private Expr fieldHandle(Expr owner, Expr name, Expr descriptor, Expr isStatic, Expr setter)
    {
        return AdvIBdr.callStatic(
                vmLayout.owner,
                vmLayout.fieldHandle.name(),
                "java/lang/invoke/MethodHandle",
                owner,
                name,
                descriptor,
                isStatic,
                setter);
    }

    private static Expr fieldHandleKey(Expr owner, Expr name, Expr descriptor, Expr isStatic, Expr setter)
    {
        return stringConcat(
                owner,
                AdvIBdr.constant("."),
                name,
                AdvIBdr.constant(":"),
                descriptor,
                AdvIBdr.constant(":"),
                isStatic,
                AdvIBdr.constant(":"),
                setter);
    }

    private static Expr methodHandleKey(Expr owner, Expr name, Expr methodType, Expr isStatic)
    {
        return stringConcat(
                owner,
                AdvIBdr.constant("."),
                name,
                methodType,
                AdvIBdr.constant(":"),
                isStatic);
    }

    private Expr rethrow(Expr throwable)
    {
        return AdvIBdr.callStatic(
                vmLayout.owner,
                vmLayout.rethrow.name(),
                "java/lang/RuntimeException",
                throwable);
    }

    private static void throwNoSuchField(AdvIBdr ib, Expr ownerClass, Expr fieldName)
    {
        ib.throwValue(AdvIBdr.newObject(
                "java/lang/NoSuchFieldException",
                stringConcat(
                        AdvIBdr.callVirtual(ownerClass, "java/lang/Class", "getName", "java/lang/String"),
                        AdvIBdr.constant("."),
                        fieldName)));
    }

    private static void throwNoSuchMethod(AdvIBdr ib, Expr ownerClass, Expr methodName, Expr methodType)
    {
        ib.throwValue(AdvIBdr.newObject(
                "java/lang/NoSuchMethodException",
                stringConcat(
                        AdvIBdr.callVirtual(ownerClass, "java/lang/Class", "getName", "java/lang/String"),
                        AdvIBdr.constant("."),
                        methodName,
                        methodType)));
    }

    private static void throwExceptionWithInt(AdvIBdr ib, String exceptionType, String prefix, Expr value)
    {
        ib.throwValue(AdvIBdr.newObject(
                exceptionType,
                stringConcat(AdvIBdr.constant(prefix), value)));
    }

    private static Expr illegalStateException(Expr cause)
    {
        return AdvIBdr.newObject(
                "java/lang/IllegalStateException",
                AdvIBdr.cast(cause, "java/lang/Throwable"));
    }

    /**
     * Tests for an optional JDK exception without emitting a symbolic class
     * reference to it. InaccessibleObjectException was introduced in Java 9,
     * while generated VM classes target Java 8.
     */
    private static Condition isThrowableNamed(Expr throwable, String className)
    {
        return AdvIBdr.and(
                AdvIBdr.notNull(throwable),
                AdvIBdr.isTrue(AdvIBdr.callVirtual(
                        AdvIBdr.callVirtual(
                                AdvIBdr.callVirtual(throwable, "java/lang/Object", "getClass", "java/lang/Class"),
                                "java/lang/Class",
                                "getName",
                                "java/lang/String"),
                        "java/lang/String",
                        "equals",
                        "Z",
                        AdvIBdr.cast(AdvIBdr.constant(className), "java/lang/Object"))));
    }

    private static Expr classForName(Expr name, Expr loader)
    {
        return AdvIBdr.callStatic(
                "java/lang/Class",
                "forName",
                "java/lang/Class",
                name,
                AdvIBdr.constant(false),
                loader);
    }

    private void cacheAdaptedMethodHandle(
            AdvIBdr ib,
            Local reflectedMethod,
            Local ownerClass,
            Local name,
            Local methodType,
            Local isStatic,
            Local key,
            Local target)
    {
        ib.directCall(AdvIBdr.callVirtual(reflectedMethod, "java/lang/reflect/Method", "setAccessible", "V", AdvIBdr.constant(true)));
        ib.ifCondition(
                AdvIBdr.notEqual(
                        AdvIBdr.callVirtual(reflectedMethod, "java/lang/reflect/Method", "getReturnType", "java/lang/Class"),
                        AdvIBdr.callVirtual(methodType, "java/lang/invoke/MethodType", "returnType", "java/lang/Class")),
                b -> throwNoSuchMethod(b, ownerClass, name, methodType));
        ib.ifCondition(
                AdvIBdr.notEqual(
                        AdvIBdr.callStatic(
                                "java/lang/reflect/Modifier",
                                "isStatic",
                                "Z",
                                AdvIBdr.callVirtual(reflectedMethod, "java/lang/reflect/Method", "getModifiers", "I")),
                        isStatic),
                b -> throwNoSuchMethod(b, ownerClass, name, methodType));
        ib.set(target, AdvIBdr.callStatic(
                vmLayout.owner,
                vmLayout.adaptMethodHandle.name(),
                "java/lang/invoke/MethodHandle",
                reflectedMethod,
                isStatic,
                AdvIBdr.callVirtual(methodType, "java/lang/invoke/MethodType", "parameterCount", "I")));
        ib.directCall(mapPut(AdvIBdr.staticField(vmLayout.methodHandles), key, target));
    }

    private static Expr directHandle(Expr lookup, Expr ownerClass, Expr name, Expr methodType, boolean staticMethod)
    {
        return AdvIBdr.callVirtual(
                AdvIBdr.callVirtual(
                        AdvIBdr.callVirtual(
                                lookup,
                                "java/lang/invoke/MethodHandles$Lookup",
                                staticMethod ? "findStatic" : "findVirtual",
                                "java/lang/invoke/MethodHandle",
                                ownerClass,
                                name,
                                methodType),
                        "java/lang/invoke/MethodHandle",
                        "asFixedArity",
                        "java/lang/invoke/MethodHandle"),
                "java/lang/invoke/MethodHandle",
                "asSpreader",
                "java/lang/invoke/MethodHandle",
                objectArrayClass(),
                AdvIBdr.callVirtual(methodType, "java/lang/invoke/MethodType", "parameterCount", "I"));
    }

    private static Expr asInvokerHandle(Expr handle, Expr parameterTypes)
    {
        return AdvIBdr.callVirtual(
                handle,
                "java/lang/invoke/MethodHandle",
                "asType",
                "java/lang/invoke/MethodHandle",
                methodType(objectClass(), objectClass(), parameterTypes));
    }

    private static Expr asArrayInvokerHandle(Expr handle)
    {
        return AdvIBdr.callVirtual(
                handle,
                "java/lang/invoke/MethodHandle",
                "asType",
                "java/lang/invoke/MethodHandle",
                AdvIBdr.callStatic(
                        "java/lang/invoke/MethodType",
                        "methodType",
                        "java/lang/invoke/MethodType",
                        objectClass(),
                        objectArrayClass()));
    }

    private void coerceArguments(AdvIBdr ib, Local arguments, Local methodType)
    {
        Local index = ib.var("argumentIndex", "I");
        ib.forLoop(
                b -> b.set(index, AdvIBdr.constant(0)),
                AdvIBdr.lessThan(index, AdvIBdr.arrayLength(arguments)),
                b -> b.increment(index, 1),
                b -> b.setArray(
                        arguments,
                        index,
                        AdvIBdr.callStatic(
                                vmLayout.owner,
                                vmLayout.coerceArgument.name(),
                                "java/lang/Object",
                                AdvIBdr.arrayAt(arguments, index),
                                AdvIBdr.callVirtual(methodType, "java/lang/invoke/MethodType", "parameterType", "java/lang/Class", index))));
    }

    private static Expr stringConcat(Expr first, Expr... rest)
    {
        Expr builder = AdvIBdr.newObject("java/lang/StringBuilder", first);
        for (Expr value : rest)
        {
            if ((value.type().getSort() == org.objectweb.asm.Type.OBJECT || value.type().getSort() == org.objectweb.asm.Type.ARRAY)
                && !value.type().equals(org.objectweb.asm.Type.getType(String.class)))
            {
                value = AdvIBdr.cast(value, "java/lang/Object");
            }
            builder = AdvIBdr.callVirtual(
                    builder,
                    "java/lang/StringBuilder",
                    "append",
                    "java/lang/StringBuilder",
                    value);
        }
        return AdvIBdr.callVirtual(
                builder,
                "java/lang/StringBuilder",
                "toString",
                "java/lang/String");
    }

    private static Expr objectClass()
    {
        return AdvIBdr.constant(org.objectweb.asm.Type.getType("Ljava/lang/Object;"));
    }

    private static Expr objectArrayClass()
    {
        return AdvIBdr.constant(org.objectweb.asm.Type.getType("[Ljava/lang/Object;"));
    }

    private static Expr voidClass()
    {
        return AdvIBdr.staticField("java/lang/Void", "TYPE", "java/lang/Class");
    }

    private static Expr primitiveType(String wrapper)
    {
        return AdvIBdr.staticField(wrapper, "TYPE", "java/lang/Class");
    }

    private static Local classArray(AdvIBdr ib, String name, Expr... values)
    {
        Local array = ib.var(name, "[Ljava/lang/Class;");
        ib.set(array, AdvIBdr.newArray("java/lang/Class", AdvIBdr.constant(values.length)));
        for (int i = 0; i < values.length; i++)
        {
            ib.setArray(array, AdvIBdr.constant(i), values[i]);
        }
        return array;
    }

    private static Expr getterHandleType()
    {
        return AdvIBdr.callStatic(
                "java/lang/invoke/MethodType",
                "methodType",
                "java/lang/invoke/MethodType",
                objectClass(),
                objectClass());
    }

    private static Expr methodType(Expr returnType, Expr leadingParameter, Expr trailingParameters)
    {
        return AdvIBdr.callStatic(
                "java/lang/invoke/MethodType",
                "methodType",
                "java/lang/invoke/MethodType",
                returnType,
                leadingParameter,
                trailingParameters);
    }

    private static void dropLeadingObjectArgument(AdvIBdr ib, Local handle)
    {
        Local leadingObject = classArray(ib, "leadingObject", objectClass());
        ib.set(handle, AdvIBdr.callStatic(
                "java/lang/invoke/MethodHandles",
                "dropArguments",
                "java/lang/invoke/MethodHandle",
                handle,
                AdvIBdr.constant(0),
                leadingObject));
    }

    private static void validateBranches()
    {
        for (Opcs opcode : Opcs.values())
        {
            if(opcode == Opcs.INVOKEDYNAMIC || opcode == Opcs.SUPER_INSTRUCTION)
            {
                continue;
            }
            if (!branches.containsKey(opcode))
            {
                throw new IllegalStateException("No InterpretBranch for " + opcode);
            }
        }
    }

    private enum SemanticArgument
    {
        PROGRAM,
        FRAME,
        CODE,
        CONSTANTS,
        OPCODE,
        OPCODE_INDEX,
        INSTRUCTION_INDEX
    }

    private record InterpretChunk(int index, List<Opcs> opcodes, MethodNode method)
    {
    }

    private record InterpretChunkSlot(int chunkIndex, int opcodeIndex)
    {
    }

    private record DispatchEntry(
            int key,
            int primaryKey,
            Opcs opcode,
            InterpretChunkSlot slot)
    {
    }

    private record SuperInstructionChunk(
            int index,
            List<SuperInstructionRegistry.Recipe> recipes,
            MethodNode method)
    {
    }
}
