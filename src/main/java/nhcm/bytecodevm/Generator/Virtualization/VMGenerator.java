package nhcm.bytecodevm.Generator.Virtualization;

import lombok.Getter;
import nhcm.bytecodevm.Config.BytecodeVMConfig;
import nhcm.bytecodevm.Enums.Acc;
import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Generator.Abstract.ClassObj;
import nhcm.bytecodevm.Generator.GlobalClass.MethodFrameGenerator;
import nhcm.bytecodevm.Generator.GlobalClass.MethodFrameLayout;
import nhcm.bytecodevm.Generator.GlobalClass.VMCodePoolGenerator;
import nhcm.bytecodevm.Generator.GlobalClass.VMProgramGenerator;
import nhcm.bytecodevm.Generator.GlobalClass.VMProgramLayout;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Array.ArrayLengthBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Array.LoadArrayBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Array.NewArrayBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Array.StoreArrayBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Control.*;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Conversion.CompareBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Conversion.ConvertBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Field.ReadFieldBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Field.WriteFieldBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Invoke.InvokeNormalBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Local.StoreLocalBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Object.CastBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Object.NewObjectBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Stack.DuplicateBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Stack.PopBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Stack.SwapBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.lock.MonitorBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Constant.*;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math.*;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Local.IncrementBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Local.LoadLocalBranch;
import nhcm.bytecodevm.Tools.OpcMutator;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import nhcm.bytecodevm.Utils.ClassUtils;
import nhcm.bytecodevm.Utils.FieldUtils;
import nhcm.bytecodevm.Utils.InsnUtils;
import nhcm.bytecodevm.Utils.MethodUtils;
import nhcm.bytecodevm.Utils.TypeUtils;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

public class VMGenerator extends ClassObj
{
    private static final int INTERPRET_CHUNK_SIZE = 12;

    private static final Map<Opcs, InterpretBranch> branches = new EnumMap<>(Opcs.class);

    static
    {
        registerBranches();
        // validateBranches();
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

    public VMGenerator(
            String className,
            List<CodePoolGenerator> codePoolGenerators,
            OpcMutator opcMutator,
            MethodFrameGenerator methodFrameGenerator,
            VMProgramGenerator vmProgramGenerator,
            VMCodePoolGenerator vmCodePoolGenerator,
            BytecodeVMConfig config)
    {
        super(className);
        this.codePoolGenerators = List.copyOf(codePoolGenerators);
        this.opcMutator = opcMutator;
        this.methodFrameGenerator = methodFrameGenerator;
        this.vmProgramGenerator = vmProgramGenerator;
        this.vmCodePoolGenerator = vmCodePoolGenerator;
        this.frameLayout = methodFrameGenerator.getLayout();
        this.programLayout = vmProgramGenerator.getLayout();
        this.vmLayout = new VMRuntimeLayout(className, methodFrameGenerator.descriptor(), vmProgramGenerator.descriptor());
        this.config = config;
        ClassNode cn = ClassUtils.newClassNode(new Acc[]{Acc.PUBLIC, Acc.FINAL}, className);
        InsnUtils.addPrivateInit(cn);
        this.classNode = cn;
        String vmCodePoolSign = vmCodePoolGenerator.descriptor();
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, "CODE_POOLS", "Ljava/util/List;", "Ljava/util/List<" + vmCodePoolSign + ">;"));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, vmLayout.fieldHandles.name(), vmLayout.fieldHandles.descriptor(), "Ljava/util/Map<Ljava/lang/String;Ljava/lang/invoke/MethodHandle;>;"));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, vmLayout.methodHandles.name(), vmLayout.methodHandles.descriptor(), "Ljava/util/Map<Ljava/lang/String;Ljava/lang/invoke/MethodHandle;>;"));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, vmLayout.methodTypes.name(), vmLayout.methodTypes.descriptor(), "Ljava/util/Map<Ljava/lang/String;Ljava/lang/invoke/MethodType;>;"));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, vmLayout.monitors.name(), vmLayout.monitors.descriptor(), "Ljava/util/Map<Ljava/lang/Object;Ljava/util/concurrent/locks/ReentrantLock;>;"));
        cn.methods.add(genClInitMethod(codePoolGenerators));
        cn.methods.add(genExecuteMethod());
        cn.methods.add(genInterpretMethod());
        cn.methods.add(genResolveMethod());
        cn.methods.add(genConstantStringMethod());
        cn.methods.add(genMethodTypeMethod());
        cn.methods.add(genResolveConstantMethod());
        cn.methods.add(genFindExceptionHandlerMethod());
        cn.methods.add(genGetFieldMethod());
        cn.methods.add(genSetFieldMethod());
        cn.methods.add(genFieldHandleMethod());
        cn.methods.add(genAdaptFieldHandleMethod());
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
    }

    private MethodNode genInterpretMethod()
    {
        MethodNode methodNode = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, vmLayout.interpret.name(), vmLayout.interpret.descriptor());
        InsnBuilder ib = new InsnBuilder(methodNode.instructions);
        // int[] code = program.code();
        ib.aload(InterpretContext.PROGRAM);
        programLayout.code.invokeVirtual(ib);
        ib.astore(InterpretContext.CODE);
        // Object[] constants = program.constants();
        ib.aload(InterpretContext.PROGRAM);
        programLayout.constants.invokeVirtual(ib);
        ib.astore(InterpretContext.CONSTANTS);
        // int[] exceptionHandlers = program.exceptionHandlers();
        ib.aload(InterpretContext.PROGRAM);
        programLayout.exceptionHandlers.invokeVirtual(ib);
        ib.astore(InterpretContext.EXCEPTION_HANDLERS);

        LabelNode loopStart = new LabelNode();
        LabelNode loopEnd = new LabelNode();
        LabelNode unknownOpcode = new LabelNode();
        LabelNode tryStart = new LabelNode();
        LabelNode tryEnd = new LabelNode();
        LabelNode exceptionHandler = new LabelNode();
        LabelNode noHandler = new LabelNode();
        methodNode.tryCatchBlocks.add(new org.objectweb.asm.tree.TryCatchBlockNode(
                tryStart,
                tryEnd,
                exceptionHandler,
                "java/lang/Throwable"));

        // while (!frame.returned)
        ib.label(loopStart);
        ib.aload(InterpretContext.FRAME);
        frameLayout.returned.get(ib);
        ib.ifne(loopEnd);

        // int instructionPc = frame.programCounter;
        ib.aload(InterpretContext.FRAME);
        frameLayout.programCounter.get(ib);
        ib.istore(InterpretContext.INSTRUCTION_PC);

        // int opcode = code[frame.programCounter++];
        ib.label(tryStart);
        ib.aload(InterpretContext.CODE);
        ib.aload(InterpretContext.FRAME);
        ib.dup();
        frameLayout.programCounter.get(ib);
        ib.dupX1();
        ib.iconst1();
        ib.iadd();
        frameLayout.programCounter.put(ib);
        ib.iaload();
        ib.istore(InterpretContext.OPCODE);

        generateDispatch(
                methodNode,
                loopStart,
                unknownOpcode
        );
        ib.label(tryEnd);

        ib.label(unknownOpcode);
        generateUnknownOpcode(ib);

        ib.label(exceptionHandler);
        ib.astore(InterpretContext.THROWN);
        ib.aload(InterpretContext.THROWN);
        ib.aload(InterpretContext.EXCEPTION_HANDLERS);
        ib.iload(InterpretContext.INSTRUCTION_PC);
        ib.aload(InterpretContext.CONSTANTS);
        vmLayout.findExceptionHandler.invokeStatic(ib);
        ib.istore(InterpretContext.HANDLER_PC);
        ib.iload(InterpretContext.HANDLER_PC);
        ib.iflt(noHandler);
        ib.aload(InterpretContext.FRAME);
        ib.iconst0();
        frameLayout.stackPointer.put(ib);
        ib.aload(InterpretContext.FRAME);
        ib.aload(InterpretContext.THROWN);
        frameLayout.push.invokeVirtual(ib);
        ib.aload(InterpretContext.FRAME);
        ib.iload(InterpretContext.HANDLER_PC);
        frameLayout.programCounter.put(ib);
        ib.goto_(loopStart);

        ib.label(noHandler);
        ib.aload(InterpretContext.THROWN);
        vmLayout.rethrow.invokeStatic(ib);
        ib.athrow();

        ib.label(loopEnd);
        ib._return();

        return methodNode;
    }

    private void generateDispatch(MethodNode method, LabelNode loopStart, LabelNode unknownOpcode)
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

        List<Opcs> opcodes = new ArrayList<>(opcodeSet);
        opcodes.sort((left, right) -> Integer.compare(
                opcMutator.toMutated(left),
                opcMutator.toMutated(right)
        ));

        List<List<Opcs>> chunks = new ArrayList<>();
        for (int start = 0; start < opcodes.size(); start += INTERPRET_CHUNK_SIZE)
        {
            chunks.add(new ArrayList<>(opcodes.subList(
                    start,
                    Math.min(start + INTERPRET_CHUNK_SIZE, opcodes.size()))));
        }

        for (int i = 0; i < chunks.size(); i++)
        {
            classNode.methods.add(genInterpretChunkMethod(i, chunks.get(i)));
        }

        int[] keys = new int[opcodes.size()];
        LabelNode[] labels = new LabelNode[opcodes.size()];

        for (int i = 0; i < opcodes.size(); i++)
        {
            keys[i] = opcMutator.toMutated(opcodes.get(i));
            labels[i] = new LabelNode();
        }

        InsnBuilder ib = new InsnBuilder(method.instructions);

        ib.iload(4);
        ib.lookupSwitch(unknownOpcode, keys, labels);

        for (int i = 0; i < opcodes.size(); i++)
        {
            ib.label(labels[i]);
            ib.aload(InterpretContext.PROGRAM);
            ib.aload(InterpretContext.FRAME);
            ib.aload(InterpretContext.CODE);
            ib.aload(InterpretContext.CONSTANTS);
            ib.iload(InterpretContext.OPCODE);
            ib.pushInt(i % INTERPRET_CHUNK_SIZE);
            ib.invokeStatic(
                    className(),
                    interpretChunkName(i / INTERPRET_CHUNK_SIZE),
                    interpretChunkDescriptor());
            ib.goto_(loopStart);
        }
    }

    private MethodNode genInterpretChunkMethod(int chunkIndex, List<Opcs> opcodes)
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                interpretChunkName(chunkIndex),
                interpretChunkDescriptor());
        InsnBuilder ib = new InsnBuilder(method.instructions);
        LabelNode done = new LabelNode();
        LabelNode unknownOpcode = new LabelNode();
        LabelNode[] labels = new LabelNode[opcodes.size()];

        for (int i = 0; i < opcodes.size(); i++)
        {
            labels[i] = new LabelNode();
        }

        ib.iload(InterpretContext.RIGHT_VALUE);
        ib.tableSwitch(0, opcodes.size() - 1, unknownOpcode, labels);

        InterpretContext context = new InterpretContext(
                className(),
                frameLayout.owner,
                done);
        for (int i = 0; i < opcodes.size(); i++)
        {
            Opcs opcode = opcodes.get(i);
            InterpretBranch branch = branches.get(opcode);
            if (branch == null)
            {
                throw new IllegalStateException("Missing interpret branch: " + opcode);
            }

            ib.label(labels[i]);
            method.instructions.add(branch.generate(context, opcode));
            if (!branch.term(opcode))
            {
                ib.goto_(done);
            }
        }

        ib.label(unknownOpcode);
        generateUnknownOpcode(ib);

        ib.label(done);
        ib._return();
        return method;
    }

    private String interpretChunkName(int chunkIndex)
    {
        return "interpretChunk$" + chunkIndex;
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
        return "(" +
                vmProgramGenerator.descriptor() +
                methodFrameGenerator.descriptor() +
                "[I[Ljava/lang/Object;II)V";
    }

    private void generateUnknownOpcode(InsnBuilder ib)
    {
        ib.new_("java/lang/IllegalStateException");
        ib.dup();
        ib.new_("java/lang/StringBuilder");
        ib.dup();
        ib.ldc("Unknown VM opcode ");
        ib.invokeSpecial("java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V");
        ib.iload(InterpretContext.OPCODE);
        ib.invokeStatic("java/lang/Integer", "toHexString", "(I)Ljava/lang/String;");
        ib.invokeVirtual(
                "java/lang/StringBuilder",
                "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        ib.ldc(" at pc ");
        ib.invokeVirtual(
                "java/lang/StringBuilder",
                "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        ib.aload(InterpretContext.FRAME);
        frameLayout.programCounter.get(ib);
        ib.iconst1();
        ib.isub();
        ib.invokeVirtual(
                "java/lang/StringBuilder",
                "append",
                "(I)Ljava/lang/StringBuilder;");
        ib.invokeVirtual("java/lang/StringBuilder", "toString", "()Ljava/lang/String;");
        ib.invokeSpecial("java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V");
        ib.athrow();
    }

    private MethodNode genExecuteMethod()
    {
        MethodNode methodNode = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC, Acc.STATIC, Acc.VARARGS},
                "execute",
                "(ILjava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;",
                "<T:Ljava/lang/Object;>(ILjava/lang/Object;[Ljava/lang/Object;)TT;",
                null);
        InsnBuilder ib = new InsnBuilder(methodNode.instructions);
        int codeId = 0;
        int receiver = 1;
        int arguments = 2;
        int program = 3;
        int frame = 4;
        int argumentOffset = 5;

        // VMProgram program = resolve(codeId);
        ib.iload(codeId);
        vmLayout.resolve.invokeStatic(ib);
        ib.astore(program);

        // MethodFrame frame = new MethodFrame(program.maxLocals(), program.maxStack());
        ib.new_(frameLayout.owner);
        ib.dup();
        ib.aload(program);
        programLayout.maxLocals.invokeVirtual(ib);
        ib.aload(program);
        programLayout.maxStack.invokeVirtual(ib);
        frameLayout.init.invokeSpecial(ib);
        ib.astore(frame);

        LabelNode staticMethod = new LabelNode();
        LabelNode argumentsReady = new LabelNode();

        // Instance methods reserve locals[0] for the receiver.
        ib.aload(receiver);
        ib.ifNull(staticMethod);
        ib.aload(frame);
        frameLayout.locals.get(ib);
        ib.iconst0();
        ib.aload(receiver);
        ib.aastore();
        ib.iconst1();
        ib.istore(argumentOffset);
        ib.goto_(argumentsReady);

        // Static methods start their arguments at locals[0].
        ib.label(staticMethod);
        ib.iconst0();
        ib.istore(argumentOffset);

        ib.label(argumentsReady);

        // System.arraycopy(arguments, 0, frame.locals, argumentOffset, arguments.length);
        ib.aload(arguments);
        ib.iconst0();
        ib.aload(frame);
        frameLayout.locals.get(ib);
        ib.iload(argumentOffset);
        ib.aload(arguments);
        ib.arrayLength();
        ib.invokeStatic(
                "java/lang/System",
                "arraycopy",
                "(Ljava/lang/Object;ILjava/lang/Object;II)V");

        // interpret(program, frame);
        ib.aload(program);
        ib.aload(frame);
        vmLayout.interpret.invokeStatic(ib);

        ib.aload(frame);
        frameLayout.returnValue.get(ib);
        ib.areturn();
        return methodNode;
    }

    private MethodNode genResolveMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.resolve.name(),
                vmLayout.resolve.descriptor());
        InsnBuilder ib = new InsnBuilder(method.instructions);
        int resolved = 1;
        int iterator = 2;
        int candidate = 3;

        ib.aconstNull();
        ib.astore(resolved);
        vmLayout.codePools.getStatic(ib);
        ib.invokeInterface("java/util/List", "iterator", "()Ljava/util/Iterator;");
        ib.astore(iterator);

        LabelNode loop = new LabelNode();
        LabelNode done = new LabelNode();
        LabelNode next = new LabelNode();
        LabelNode accept = new LabelNode();
        LabelNode found = new LabelNode();

        ib.label(loop);
        ib.aload(iterator);
        ib.invokeInterface("java/util/Iterator", "hasNext", "()Z");
        ib.ifeq(done);
        ib.aload(iterator);
        ib.invokeInterface("java/util/Iterator", "next", "()Ljava/lang/Object;");
        ib.checkCast(vmCodePoolGenerator.className());
        ib.iload(0);
        ib.invokeInterface(vmCodePoolGenerator.className(), "find", "(I)" + vmProgramGenerator.descriptor());
        ib.astore(candidate);
        ib.aload(candidate);
        ib.ifNull(next);
        ib.aload(resolved);
        ib.ifNull(accept);
        emitExceptionWithInt(ib, "java/lang/IllegalStateException", "Duplicate code id: ", 0);

        ib.label(accept);
        ib.aload(candidate);
        ib.astore(resolved);
        ib.label(next);
        ib.goto_(loop);

        ib.label(done);
        ib.aload(resolved);
        ib.ifNonNull(found);
        emitExceptionWithInt(ib, "java/lang/IllegalArgumentException", "Unknown code id: ", 0);
        ib.label(found);
        ib.aload(resolved);
        ib.areturn();
        return method;
    }

    private MethodNode genConstantStringMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.constantString.name(),
                vmLayout.constantString.descriptor());
        InsnBuilder ib = new InsnBuilder(method.instructions);
        ib.aload(0);
        ib.iload(1);
        ib.aaload();
        ib.checkCast("java/lang/String");
        ib.areturn();
        return method;
    }

    private MethodNode genMethodTypeMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.methodType.name(),
                vmLayout.methodType.descriptor());
        InsnBuilder ib = new InsnBuilder(method.instructions);
        int cached = 1;

        vmLayout.methodTypes.getStatic(ib);
        ib.aload(0);
        ib.invokeInterface("java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;");
        ib.checkCast("java/lang/invoke/MethodType");
        ib.astore(cached);

        LabelNode create = new LabelNode();
        ib.aload(cached);
        ib.ifNull(create);
        ib.aload(cached);
        ib.areturn();

        ib.label(create);
        ib.aload(0);
        ib.ldc(org.objectweb.asm.Type.getObjectType(className()));
        ib.invokeVirtual("java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;");
        ib.invokeStatic(
                "java/lang/invoke/MethodType",
                "fromMethodDescriptorString",
                "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;");
        ib.astore(cached);
        vmLayout.methodTypes.getStatic(ib);
        ib.aload(0);
        ib.aload(cached);
        ib.invokeInterface("java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
        ib.pop();
        ib.aload(cached);
        ib.areturn();
        return method;
    }

    private MethodNode genResolveConstantMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.resolveConstant.name(),
                vmLayout.resolveConstant.descriptor());
        InsnBuilder ib = new InsnBuilder(method.instructions);
        int encoded = 2;
        int descriptor = 3;
        int loader = 4;
        int receiver = 5;
        LabelNode notEncoded = new LabelNode();
        LabelNode notTypeConstant = new LabelNode();
        LabelNode classType = new LabelNode();
        LabelNode loaderReady = new LabelNode();

        ib.aload(0);
        ib.instanceOf("[Ljava/lang/Object;");
        ib.ifeq(notEncoded);
        ib.aload(0);
        ib.checkCast("[Ljava/lang/Object;");
        ib.astore(encoded);

        ib.aload(encoded);
        ib.arrayLength();
        ib.iconst2();
        ib.ifIcmpNe(notEncoded);
        ib.ldc("__BytecodeVM_TYPE__");
        ib.aload(encoded);
        ib.iconst0();
        ib.aaload();
        ib.invokeVirtual("java/lang/String", "equals", "(Ljava/lang/Object;)Z");
        ib.ifeq(notEncoded);
        ib.aload(encoded);
        ib.iconst1();
        ib.aaload();
        ib.instanceOf("java/lang/String");
        ib.ifeq(notEncoded);

        ib.aload(encoded);
        ib.iconst1();
        ib.aaload();
        ib.checkCast("java/lang/String");
        ib.astore(descriptor);

        ib.ldc(org.objectweb.asm.Type.getObjectType(className()));
        ib.invokeVirtual("java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;");
        ib.astore(loader);
        ib.aload(1);
        frameLayout.locals.get(ib);
        ib.arrayLength();
        ib.ifle(loaderReady);
        ib.aload(1);
        frameLayout.locals.get(ib);
        ib.iconst0();
        ib.aaload();
        ib.astore(receiver);
        ib.aload(receiver);
        ib.ifNull(loaderReady);
        ib.aload(receiver);
        ib.invokeVirtual("java/lang/Object", "getClass", "()Ljava/lang/Class;");
        ib.invokeVirtual("java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;");
        ib.astore(loader);
        ib.label(loaderReady);

        ib.aload(descriptor);
        ib.invokeVirtual("java/lang/String", "length", "()I");
        ib.ifeq(notTypeConstant);
        ib.aload(descriptor);
        ib.iconst0();
        ib.invokeVirtual("java/lang/String", "charAt", "(I)C");
        ib.bipush('(');
        ib.ifIcmpNe(classType);

        ib.aload(descriptor);
        ib.aload(loader);
        ib.invokeStatic(
                "java/lang/invoke/MethodType",
                "fromMethodDescriptorString",
                "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;");
        ib.areturn();

        ib.label(classType);
        ib.aload(descriptor);
        ib.aload(loader);
        vmLayout.loadOwnerWithLoader.invokeStatic(ib);
        ib.areturn();

        ib.label(notTypeConstant);
        ib.new_("java/lang/IllegalStateException");
        ib.dup();
        ib.ldc("Invalid encoded VM type constant");
        ib.invokeSpecial("java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V");
        ib.athrow();

        ib.label(notEncoded);
        ib.aload(0);
        ib.areturn();
        return method;
    }

    private MethodNode genFindExceptionHandlerMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                "findExceptionHandler",
                "(Ljava/lang/Throwable;[II[Ljava/lang/Object;)I");
        InsnBuilder ib = new InsnBuilder(method.instructions);
        int throwable = 0;
        int handlers = 1;
        int instructionPc = 2;
        int constants = 3;
        int index = 4;
        int typeIndex = 5;
        LabelNode loop = new LabelNode();
        LabelNode noMatch = new LabelNode();
        LabelNode next = new LabelNode();
        LabelNode catchAll = new LabelNode();
        LabelNode typeMatches = new LabelNode();

        ib.iconst0();
        ib.istore(index);
        ib.label(loop);
        ib.iload(index);
        ib.aload(handlers);
        ib.arrayLength();
        ib.ifIcmpGe(noMatch);

        ib.iload(instructionPc);
        ib.aload(handlers);
        ib.iload(index);
        ib.iaload();
        ib.ifIcmpLt(next);

        ib.iload(instructionPc);
        ib.aload(handlers);
        ib.iload(index);
        ib.iconst1();
        ib.iadd();
        ib.iaload();
        ib.ifIcmpGe(next);

        ib.aload(handlers);
        ib.iload(index);
        ib.iconst3();
        ib.iadd();
        ib.iaload();
        ib.istore(typeIndex);
        ib.iload(typeIndex);
        ib.iflt(catchAll);

        ib.aload(constants);
        ib.iload(typeIndex);
        vmLayout.constantString.invokeStatic(ib);
        vmLayout.loadOwner.invokeStatic(ib);
        ib.aload(throwable);
        ib.invokeVirtual("java/lang/Class", "isInstance", "(Ljava/lang/Object;)Z");
        ib.ifne(typeMatches);
        ib.goto_(next);

        ib.label(catchAll);
        ib.label(typeMatches);
        ib.aload(handlers);
        ib.iload(index);
        ib.iconst2();
        ib.iadd();
        ib.iaload();
        ib.ireturn();

        ib.label(next);
        ib.iinc(index, 4);
        ib.goto_(loop);

        ib.label(noMatch);
        ib.iconstM1();
        ib.ireturn();
        return method;
    }

    private MethodNode genGetFieldMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.getField.name(),
                vmLayout.getField.descriptor());
        InsnBuilder ib = new InsnBuilder(method.instructions);
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        method.tryCatchBlocks.add(new org.objectweb.asm.tree.TryCatchBlockNode(
                start, end, handler, "java/lang/Throwable"));

        ib.label(start);
        ib.aload(0);
        ib.aload(1);
        ib.aload(2);
        ib.iload(3);
        ib.iconst0();
        ib.invokeStatic(
                className(),
                "fieldHandle",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZ)Ljava/lang/invoke/MethodHandle;");
        ib.aload(4);
        ib.invokeVirtual(
                "java/lang/invoke/MethodHandle",
                "invokeExact",
                "(Ljava/lang/Object;)Ljava/lang/Object;");
        ib.label(end);
        ib.areturn();

        ib.label(handler);
        ib.astore(5);
        ib.aload(5);
        vmLayout.rethrow.invokeStatic(ib);
        ib.athrow();
        return method;
    }

    private MethodNode genSetFieldMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.setField.name(),
                vmLayout.setField.descriptor());
        InsnBuilder ib = new InsnBuilder(method.instructions);
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        method.tryCatchBlocks.add(new org.objectweb.asm.tree.TryCatchBlockNode(
                start, end, handler, "java/lang/Throwable"));

        ib.label(start);
        ib.aload(5);
        ib.aload(2);
        vmLayout.loadOwner.invokeStatic(ib);
        ib.invokeStatic(
                className(),
                "coerceArgument",
                "(Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;");
        ib.astore(5);

        ib.aload(0);
        ib.aload(1);
        ib.aload(2);
        ib.iload(3);
        ib.iconst1();
        ib.invokeStatic(
                className(),
                "fieldHandle",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZ)Ljava/lang/invoke/MethodHandle;");
        ib.aload(4);
        ib.aload(5);
        ib.invokeVirtual(
                "java/lang/invoke/MethodHandle",
                "invokeExact",
                "(Ljava/lang/Object;Ljava/lang/Object;)V");
        ib.label(end);
        ib._return();

        ib.label(handler);
        ib.astore(6);
        ib.aload(6);
        vmLayout.rethrow.invokeStatic(ib);
        ib.athrow();
        return method;
    }

    private MethodNode genFieldHandleMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                "fieldHandle",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZ)Ljava/lang/invoke/MethodHandle;");
        InsnBuilder ib = new InsnBuilder(method.instructions);
        int key = 5;
        int cached = 6;
        int ownerClass = 7;
        int fieldType = 8;
        int field = 9;
        int handle = 10;
        int exception = 11;

        emitFieldHandleKey(ib);
        ib.astore(key);
        vmLayout.fieldHandles.getStatic(ib);
        ib.aload(key);
        ib.invokeInterface("java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;");
        ib.checkCast("java/lang/invoke/MethodHandle");
        ib.astore(cached);
        LabelNode create = new LabelNode();
        ib.aload(cached);
        ib.ifNull(create);
        ib.aload(cached);
        ib.areturn();

        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        LabelNode fieldTypeMatches = new LabelNode();
        LabelNode modifiersMatch = new LabelNode();
        method.tryCatchBlocks.add(new org.objectweb.asm.tree.TryCatchBlockNode(
                start, end, handler, "java/lang/ReflectiveOperationException"));

        ib.label(create);
        ib.label(start);
        ib.aload(0);
        vmLayout.loadOwner.invokeStatic(ib);
        ib.astore(ownerClass);

        ib.aload(2);
        vmLayout.loadOwner.invokeStatic(ib);
        ib.astore(fieldType);

        ib.aload(ownerClass);
        ib.aload(1);
        vmLayout.findField.invokeStatic(ib);
        ib.astore(field);
        ib.aload(field);
        ib.iconst1();
        ib.invokeVirtual("java/lang/reflect/Field", "setAccessible", "(Z)V");

        ib.aload(field);
        ib.invokeVirtual("java/lang/reflect/Field", "getType", "()Ljava/lang/Class;");
        ib.aload(fieldType);
        ib.ifAcmpEq(fieldTypeMatches);
        emitNoSuchField(ib, ownerClass);

        ib.label(fieldTypeMatches);
        ib.aload(field);
        ib.invokeVirtual("java/lang/reflect/Field", "getModifiers", "()I");
        ib.invokeStatic("java/lang/reflect/Modifier", "isStatic", "(I)Z");
        ib.iload(3);
        ib.ifIcmpEq(modifiersMatch);
        emitNoSuchField(ib, ownerClass);

        ib.label(modifiersMatch);
        ib.aload(field);
        ib.iload(3);
        ib.iload(4);
        ib.invokeStatic(
                className(),
                "adaptFieldHandle",
                "(Ljava/lang/reflect/Field;ZZ)Ljava/lang/invoke/MethodHandle;");
        ib.astore(handle);

        vmLayout.fieldHandles.getStatic(ib);
        ib.aload(key);
        ib.aload(handle);
        ib.invokeInterface(
                "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
        ib.pop();
        ib.label(end);
        ib.aload(handle);
        ib.areturn();

        ib.label(handler);
        ib.astore(exception);
        ib.new_("java/lang/IllegalStateException");
        ib.dup();
        ib.aload(exception);
        ib.invokeSpecial("java/lang/IllegalStateException", "<init>", "(Ljava/lang/Throwable;)V");
        ib.athrow();
        return method;
    }

    private MethodNode genAdaptFieldHandleMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                "adaptFieldHandle",
                "(Ljava/lang/reflect/Field;ZZ)Ljava/lang/invoke/MethodHandle;",
                new String[]{"java/lang/IllegalAccessException"});
        InsnBuilder ib = new InsnBuilder(method.instructions);
        int handle = 3;
        LabelNode setter = new LabelNode();
        LabelNode getterInstance = new LabelNode();
        LabelNode setterInstance = new LabelNode();

        ib.iload(2);
        ib.ifne(setter);

        ib.invokeStatic("java/lang/invoke/MethodHandles", "lookup", "()Ljava/lang/invoke/MethodHandles$Lookup;");
        ib.aload(0);
        ib.invokeVirtual(
                "java/lang/invoke/MethodHandles$Lookup",
                "unreflectGetter",
                "(Ljava/lang/reflect/Field;)Ljava/lang/invoke/MethodHandle;");
        ib.astore(handle);
        ib.iload(1);
        ib.ifeq(getterInstance);
        emitDropLeadingObjectArgument(ib, handle);
        ib.label(getterInstance);
        ib.aload(handle);
        emitGetterHandleType(ib);
        ib.invokeVirtual(
                "java/lang/invoke/MethodHandle",
                "asType",
                "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;");
        ib.areturn();

        ib.label(setter);
        ib.invokeStatic("java/lang/invoke/MethodHandles", "lookup", "()Ljava/lang/invoke/MethodHandles$Lookup;");
        ib.aload(0);
        ib.invokeVirtual(
                "java/lang/invoke/MethodHandles$Lookup",
                "unreflectSetter",
                "(Ljava/lang/reflect/Field;)Ljava/lang/invoke/MethodHandle;");
        ib.astore(handle);
        ib.iload(1);
        ib.ifeq(setterInstance);
        emitDropLeadingObjectArgument(ib, handle);
        ib.label(setterInstance);
        ib.aload(handle);
        emitSetterHandleType(ib);
        ib.invokeVirtual(
                "java/lang/invoke/MethodHandle",
                "asType",
                "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;");
        ib.areturn();
        return method;
    }

    private MethodNode genFindFieldMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                "findField",
                "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/reflect/Field;",
                new String[]{"java/lang/NoSuchFieldException"});
        InsnBuilder ib = new InsnBuilder(method.instructions);
        int interfaces = 2;
        int index = 3;
        int found = 4;
        int superClass = 5;
        LabelNode declaredStart = new LabelNode();
        LabelNode declaredEnd = new LabelNode();
        LabelNode declaredMissing = new LabelNode();
        LabelNode interfaceLoop = new LabelNode();
        LabelNode nextInterface = new LabelNode();
        LabelNode interfacesDone = new LabelNode();
        LabelNode interfaceStart = new LabelNode();
        LabelNode interfaceEnd = new LabelNode();
        LabelNode interfaceMissing = new LabelNode();
        LabelNode noSuperClass = new LabelNode();
        method.tryCatchBlocks.add(new org.objectweb.asm.tree.TryCatchBlockNode(
                declaredStart,
                declaredEnd,
                declaredMissing,
                "java/lang/NoSuchFieldException"));
        method.tryCatchBlocks.add(new org.objectweb.asm.tree.TryCatchBlockNode(
                interfaceStart,
                interfaceEnd,
                interfaceMissing,
                "java/lang/NoSuchFieldException"));

        ib.label(declaredStart);
        ib.aload(0);
        ib.aload(1);
        ib.invokeVirtual("java/lang/Class", "getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;");
        ib.label(declaredEnd);
        ib.areturn();

        ib.label(declaredMissing);
        ib.pop();

        ib.aload(0);
        ib.invokeVirtual("java/lang/Class", "getInterfaces", "()[Ljava/lang/Class;");
        ib.astore(interfaces);
        ib.iconst0();
        ib.istore(index);
        ib.label(interfaceLoop);
        ib.iload(index);
        ib.aload(interfaces);
        ib.arrayLength();
        ib.ifIcmpGe(interfacesDone);

        ib.label(interfaceStart);
        ib.aload(interfaces);
        ib.iload(index);
        ib.aaload();
        ib.aload(1);
        vmLayout.findField.invokeStatic(ib);
        ib.astore(found);
        ib.label(interfaceEnd);
        ib.aload(found);
        ib.areturn();

        ib.label(interfaceMissing);
        ib.pop();
        ib.label(nextInterface);
        ib.iinc(index, 1);
        ib.goto_(interfaceLoop);

        ib.label(interfacesDone);
        ib.aload(0);
        ib.invokeVirtual("java/lang/Class", "getSuperclass", "()Ljava/lang/Class;");
        ib.astore(superClass);
        ib.aload(superClass);
        ib.ifNull(noSuperClass);
        ib.aload(superClass);
        ib.aload(1);
        vmLayout.findField.invokeStatic(ib);
        ib.areturn();

        ib.label(noSuperClass);
        ib.new_("java/lang/NoSuchFieldException");
        ib.dup();
        ib.aload(1);
        ib.invokeSpecial("java/lang/NoSuchFieldException", "<init>", "(Ljava/lang/String;)V");
        ib.athrow();
        return method;
    }

    private MethodNode genFindMethodMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                "findMethod",
                "(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;",
                new String[]{"java/lang/NoSuchMethodException"});
        InsnBuilder ib = new InsnBuilder(method.instructions);
        int interfaces = 3;
        int index = 4;
        int found = 5;
        int superClass = 6;
        LabelNode declaredStart = new LabelNode();
        LabelNode declaredEnd = new LabelNode();
        LabelNode declaredMissing = new LabelNode();
        LabelNode interfaceLoop = new LabelNode();
        LabelNode nextInterface = new LabelNode();
        LabelNode interfacesDone = new LabelNode();
        LabelNode interfaceStart = new LabelNode();
        LabelNode interfaceEnd = new LabelNode();
        LabelNode interfaceMissing = new LabelNode();
        LabelNode noSuperClass = new LabelNode();
        method.tryCatchBlocks.add(new org.objectweb.asm.tree.TryCatchBlockNode(
                declaredStart,
                declaredEnd,
                declaredMissing,
                "java/lang/NoSuchMethodException"));
        method.tryCatchBlocks.add(new org.objectweb.asm.tree.TryCatchBlockNode(
                interfaceStart,
                interfaceEnd,
                interfaceMissing,
                "java/lang/NoSuchMethodException"));

        ib.label(declaredStart);
        ib.aload(0);
        ib.aload(1);
        ib.aload(2);
        ib.invokeVirtual(
                "java/lang/Class",
                "getDeclaredMethod",
                "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;");
        ib.label(declaredEnd);
        ib.areturn();

        ib.label(declaredMissing);
        ib.pop();

        ib.aload(0);
        ib.invokeVirtual("java/lang/Class", "getInterfaces", "()[Ljava/lang/Class;");
        ib.astore(interfaces);
        ib.iconst0();
        ib.istore(index);
        ib.label(interfaceLoop);
        ib.iload(index);
        ib.aload(interfaces);
        ib.arrayLength();
        ib.ifIcmpGe(interfacesDone);

        ib.label(interfaceStart);
        ib.aload(interfaces);
        ib.iload(index);
        ib.aaload();
        ib.aload(1);
        ib.aload(2);
        ib.invokeStatic(
                className(),
                "findMethod",
                "(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;");
        ib.astore(found);
        ib.label(interfaceEnd);
        ib.aload(found);
        ib.areturn();

        ib.label(interfaceMissing);
        ib.pop();
        ib.label(nextInterface);
        ib.iinc(index, 1);
        ib.goto_(interfaceLoop);

        ib.label(interfacesDone);
        ib.aload(0);
        ib.invokeVirtual("java/lang/Class", "getSuperclass", "()Ljava/lang/Class;");
        ib.astore(superClass);
        ib.aload(superClass);
        ib.ifNull(noSuperClass);
        ib.aload(superClass);
        ib.aload(1);
        ib.aload(2);
        ib.invokeStatic(
                className(),
                "findMethod",
                "(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;");
        ib.areturn();

        ib.label(noSuperClass);
        ib.new_("java/lang/NoSuchMethodException");
        ib.dup();
        ib.new_("java/lang/StringBuilder");
        ib.dup();
        ib.aload(0);
        ib.invokeVirtual("java/lang/Class", "getName", "()Ljava/lang/String;");
        ib.invokeSpecial("java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V");
        appendString(ib, ".");
        ib.aload(1);
        ib.invokeVirtual("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        ib.invokeVirtual("java/lang/StringBuilder", "toString", "()Ljava/lang/String;");
        ib.invokeSpecial("java/lang/NoSuchMethodException", "<init>", "(Ljava/lang/String;)V");
        ib.athrow();
        return method;
    }

    private MethodNode genInvokeMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                "invoke",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/invoke/MethodType;ZLjava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
        InsnBuilder ib = new InsnBuilder(method.instructions);
        int key = 6;
        int target = 7;
        int ownerClass = 8;
        int exception = 9;
        int reflectedMethod = 10;

        LabelNode normalInvoke = new LabelNode();
        ib.iload(3);
        ib.ifne(normalInvoke);
        ib.aload(1);
        ib.ldc("clone");
        ib.invokeVirtual("java/lang/String", "equals", "(Ljava/lang/Object;)Z");
        ib.ifeq(normalInvoke);
        ib.aload(2);
        ib.invokeVirtual("java/lang/invoke/MethodType", "parameterCount", "()I");
        ib.ifne(normalInvoke);
        ib.aload(4);
        ib.invokeVirtual("java/lang/Object", "getClass", "()Ljava/lang/Class;");
        ib.invokeVirtual("java/lang/Class", "isArray", "()Z");
        ib.ifeq(normalInvoke);
        ib.aload(4);
        vmLayout.cloneArray.invokeStatic(ib);
        ib.areturn();
        ib.label(normalInvoke);

        emitMethodHandleKey(ib);
        ib.astore(key);
        vmLayout.methodHandles.getStatic(ib);
        ib.aload(key);
        ib.invokeInterface("java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;");
        ib.checkCast("java/lang/invoke/MethodHandle");
        ib.astore(target);

        LabelNode methodReady = new LabelNode();
        ib.aload(target);
        ib.ifNonNull(methodReady);

        LabelNode reflectionStart = new LabelNode();
        LabelNode reflectionEnd = new LabelNode();
        LabelNode reflectionHandler = new LabelNode();
        LabelNode publicLookupStart = new LabelNode();
        LabelNode publicLookupEnd = new LabelNode();
        LabelNode publicLookupHandler = new LabelNode();
        LabelNode methodFound = new LabelNode();
        LabelNode returnTypeMatches = new LabelNode();
        LabelNode modifiersMatch = new LabelNode();
        LabelNode throwReflectionFailure = new LabelNode();
        LabelNode cacheMethod = new LabelNode();
        LabelNode cacheTarget = new LabelNode();
        LabelNode directLookup = new LabelNode();
        method.tryCatchBlocks.add(new org.objectweb.asm.tree.TryCatchBlockNode(
                reflectionStart,
                reflectionEnd,
                reflectionHandler,
                "java/lang/Throwable"));
        method.tryCatchBlocks.add(new org.objectweb.asm.tree.TryCatchBlockNode(
                publicLookupStart,
                publicLookupEnd,
                publicLookupHandler,
                "java/lang/ReflectiveOperationException"));

        ib.aconstNull();
        ib.astore(ownerClass);

        ib.label(reflectionStart);
        ib.aload(0);
        vmLayout.loadOwner.invokeStatic(ib);
        ib.astore(ownerClass);
        ib.aload(ownerClass);
        ib.aload(1);
        ib.aload(2);
        ib.invokeVirtual("java/lang/invoke/MethodType", "parameterArray", "()[Ljava/lang/Class;");
        ib.invokeStatic(
                className(),
                "findMethod",
                "(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;");
        ib.astore(reflectedMethod);
        ib.label(methodFound);
        ib.aload(reflectedMethod);
        ib.iconst1();
        ib.invokeVirtual("java/lang/reflect/Method", "setAccessible", "(Z)V");

        ib.aload(reflectedMethod);
        ib.invokeVirtual("java/lang/reflect/Method", "getReturnType", "()Ljava/lang/Class;");
        ib.aload(2);
        ib.invokeVirtual("java/lang/invoke/MethodType", "returnType", "()Ljava/lang/Class;");
        ib.ifAcmpEq(returnTypeMatches);
        emitNoSuchMethod(ib, ownerClass);

        ib.label(returnTypeMatches);
        ib.aload(reflectedMethod);
        ib.invokeVirtual("java/lang/reflect/Method", "getModifiers", "()I");
        ib.invokeStatic("java/lang/reflect/Modifier", "isStatic", "(I)Z");
        ib.iload(3);
        ib.ifIcmpEq(modifiersMatch);
        emitNoSuchMethod(ib, ownerClass);

        ib.label(modifiersMatch);
        ib.label(cacheMethod);
        ib.aload(reflectedMethod);
        ib.iload(3);
        ib.aload(2);
        ib.invokeVirtual("java/lang/invoke/MethodType", "parameterCount", "()I");
        ib.invokeStatic(
                className(),
                "adaptMethodHandle",
                "(Ljava/lang/reflect/Method;ZI)Ljava/lang/invoke/MethodHandle;");
        ib.astore(target);
        ib.label(cacheTarget);
        vmLayout.methodHandles.getStatic(ib);
        ib.aload(key);
        ib.aload(target);
        ib.invokeInterface(
                "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
        ib.pop();
        ib.label(reflectionEnd);
        ib.goto_(methodReady);

        ib.label(reflectionHandler);
        ib.astore(exception);
        ib.aload(exception);
        ib.instanceOf("java/lang/NoSuchMethodException");
        ib.ifeq(directLookup);

        ib.label(publicLookupStart);
        ib.aload(ownerClass);
        ib.aload(1);
        ib.aload(2);
        ib.invokeVirtual("java/lang/invoke/MethodType", "parameterArray", "()[Ljava/lang/Class;");
        ib.invokeVirtual(
                "java/lang/Class",
                "getMethod",
                "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;");
        ib.astore(reflectedMethod);
        ib.label(publicLookupEnd);
        ib.goto_(methodFound);

        ib.label(publicLookupHandler);
        ib.astore(exception);

        ib.label(directLookup);
        ib.aload(exception);
        ib.instanceOf("java/lang/reflect/InaccessibleObjectException");
        ib.ifeq(throwReflectionFailure);
        ib.aload(ownerClass);
        ib.aload(1);
        ib.aload(2);
        ib.iload(3);
        ib.invokeStatic(
                className(),
                "adaptDirectMethodHandle",
                "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;Z)Ljava/lang/invoke/MethodHandle;");
        ib.astore(target);
        ib.goto_(cacheTarget);

        ib.label(throwReflectionFailure);
        ib.new_("java/lang/IllegalStateException");
        ib.dup();
        ib.aload(exception);
        ib.invokeSpecial("java/lang/IllegalStateException", "<init>", "(Ljava/lang/Throwable;)V");
        ib.athrow();

        ib.label(methodReady);
        emitCoerceArguments(ib, 5, 2, 11);

        LabelNode invokeStart = new LabelNode();
        LabelNode invokeEnd = new LabelNode();
        LabelNode invokeHandler = new LabelNode();
        method.tryCatchBlocks.add(new org.objectweb.asm.tree.TryCatchBlockNode(
                invokeStart, invokeEnd, invokeHandler, "java/lang/Throwable"));
        ib.label(invokeStart);
        ib.aload(target);
        ib.aload(4);
        ib.aload(5);
        ib.invokeVirtual(
                "java/lang/invoke/MethodHandle",
                "invokeExact",
                "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
        ib.label(invokeEnd);
        ib.areturn();

        ib.label(invokeHandler);
        ib.astore(exception);
        ib.aload(exception);
        vmLayout.rethrow.invokeStatic(ib);
        ib.athrow();
        return method;
    }

    private MethodNode genConstructMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.construct.name(),
                vmLayout.construct.descriptor());
        InsnBuilder ib = new InsnBuilder(method.instructions);
        int key = 3;
        int target = 4;
        int ownerClass = 5;
        int constructor = 6;
        int exception = 7;

        ib.new_("java/lang/StringBuilder");
        ib.dup();
        ib.ldc("<init>:");
        ib.invokeSpecial("java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V");
        ib.aload(0);
        ib.invokeVirtual("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        ib.aload(1);
        ib.invokeVirtual("java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;");
        ib.invokeVirtual("java/lang/StringBuilder", "toString", "()Ljava/lang/String;");
        ib.astore(key);

        vmLayout.methodHandles.getStatic(ib);
        ib.aload(key);
        ib.invokeInterface("java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;");
        ib.checkCast("java/lang/invoke/MethodHandle");
        ib.astore(target);

        LabelNode targetReady = new LabelNode();
        ib.aload(target);
        ib.ifNonNull(targetReady);

        LabelNode resolveStart = new LabelNode();
        LabelNode resolveEnd = new LabelNode();
        LabelNode resolveHandler = new LabelNode();
        method.tryCatchBlocks.add(new org.objectweb.asm.tree.TryCatchBlockNode(
                resolveStart,
                resolveEnd,
                resolveHandler,
                "java/lang/Throwable"));

        ib.label(resolveStart);
        ib.aload(0);
        vmLayout.loadOwner.invokeStatic(ib);
        ib.astore(ownerClass);

        ib.aload(ownerClass);
        ib.aload(1);
        ib.invokeVirtual("java/lang/invoke/MethodType", "parameterArray", "()[Ljava/lang/Class;");
        ib.invokeVirtual(
                "java/lang/Class",
                "getDeclaredConstructor",
                "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;");
        ib.astore(constructor);
        ib.aload(constructor);
        ib.iconst1();
        ib.invokeVirtual("java/lang/reflect/Constructor", "setAccessible", "(Z)V");

        ib.aload(constructor);
        ib.aload(1);
        ib.invokeVirtual("java/lang/invoke/MethodType", "parameterCount", "()I");
        ib.invokeStatic(
                className(),
                "adaptConstructorHandle",
                "(Ljava/lang/reflect/Constructor;I)Ljava/lang/invoke/MethodHandle;");
        ib.astore(target);

        vmLayout.methodHandles.getStatic(ib);
        ib.aload(key);
        ib.aload(target);
        ib.invokeInterface(
                "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
        ib.pop();
        ib.label(resolveEnd);
        ib.goto_(targetReady);

        ib.label(resolveHandler);
        ib.astore(exception);
        ib.aload(exception);
        vmLayout.rethrow.invokeStatic(ib);
        ib.athrow();

        ib.label(targetReady);
        emitCoerceArguments(ib, 2, 1, 8);

        LabelNode invokeStart = new LabelNode();
        LabelNode invokeEnd = new LabelNode();
        LabelNode invokeHandler = new LabelNode();
        method.tryCatchBlocks.add(new org.objectweb.asm.tree.TryCatchBlockNode(
                invokeStart,
                invokeEnd,
                invokeHandler,
                "java/lang/Throwable"));

        ib.label(invokeStart);
        ib.aload(target);
        ib.aconstNull();
        ib.aload(2);
        ib.invokeVirtual(
                "java/lang/invoke/MethodHandle",
                "invokeExact",
                "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
        ib.label(invokeEnd);
        ib.areturn();

        ib.label(invokeHandler);
        ib.astore(exception);
        ib.aload(exception);
        vmLayout.rethrow.invokeStatic(ib);
        ib.athrow();
        return method;
    }

    private MethodNode genAdaptMethodHandleMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                "adaptMethodHandle",
                "(Ljava/lang/reflect/Method;ZI)Ljava/lang/invoke/MethodHandle;",
                new String[]{"java/lang/IllegalAccessException"});
        InsnBuilder ib = new InsnBuilder(method.instructions);
        int handle = 3;
        LabelNode instanceMethod = new LabelNode();

        ib.invokeStatic("java/lang/invoke/MethodHandles", "lookup", "()Ljava/lang/invoke/MethodHandles$Lookup;");
        ib.aload(0);
        ib.invokeVirtual(
                "java/lang/invoke/MethodHandles$Lookup",
                "unreflect",
                "(Ljava/lang/reflect/Method;)Ljava/lang/invoke/MethodHandle;");
        ib.invokeVirtual(
                "java/lang/invoke/MethodHandle",
                "asFixedArity",
                "()Ljava/lang/invoke/MethodHandle;");
        ib.ldc(org.objectweb.asm.Type.getType("[Ljava/lang/Object;"));
        ib.iload(2);
        ib.invokeVirtual(
                "java/lang/invoke/MethodHandle",
                "asSpreader",
                "(Ljava/lang/Class;I)Ljava/lang/invoke/MethodHandle;");
        ib.astore(handle);

        ib.iload(1);
        ib.ifeq(instanceMethod);
        emitDropLeadingObjectArgument(ib, handle);

        ib.label(instanceMethod);
        ib.aload(handle);
        emitInvokerHandleType(ib);
        ib.invokeVirtual(
                "java/lang/invoke/MethodHandle",
                "asType",
                "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;");
        ib.areturn();
        return method;
    }

    private MethodNode genAdaptDirectMethodHandleMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                "adaptDirectMethodHandle",
                "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;Z)Ljava/lang/invoke/MethodHandle;",
                new String[]{"java/lang/IllegalAccessException", "java/lang/NoSuchMethodException"});
        InsnBuilder ib = new InsnBuilder(method.instructions);
        int lookup = 4;
        int handle = 5;
        LabelNode virtualMethod = new LabelNode();

        ib.aload(0);
        ib.invokeStatic("java/lang/invoke/MethodHandles", "lookup", "()Ljava/lang/invoke/MethodHandles$Lookup;");
        ib.invokeStatic(
                "java/lang/invoke/MethodHandles",
                "privateLookupIn",
                "(Ljava/lang/Class;Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/invoke/MethodHandles$Lookup;");
        ib.astore(lookup);

        ib.iload(3);
        ib.ifeq(virtualMethod);
        ib.aload(lookup);
        ib.aload(0);
        ib.aload(1);
        ib.aload(2);
        ib.invokeVirtual(
                "java/lang/invoke/MethodHandles$Lookup",
                "findStatic",
                "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;");
        ib.invokeVirtual(
                "java/lang/invoke/MethodHandle",
                "asFixedArity",
                "()Ljava/lang/invoke/MethodHandle;");
        ib.ldc(org.objectweb.asm.Type.getType("[Ljava/lang/Object;"));
        ib.aload(2);
        ib.invokeVirtual("java/lang/invoke/MethodType", "parameterCount", "()I");
        ib.invokeVirtual(
                "java/lang/invoke/MethodHandle",
                "asSpreader",
                "(Ljava/lang/Class;I)Ljava/lang/invoke/MethodHandle;");
        ib.astore(handle);
        emitDropLeadingObjectArgument(ib, handle);
        ib.aload(handle);
        emitInvokerHandleType(ib);
        ib.invokeVirtual(
                "java/lang/invoke/MethodHandle",
                "asType",
                "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;");
        ib.areturn();

        ib.label(virtualMethod);
        ib.aload(lookup);
        ib.aload(0);
        ib.aload(1);
        ib.aload(2);
        ib.invokeVirtual(
                "java/lang/invoke/MethodHandles$Lookup",
                "findVirtual",
                "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;");
        ib.invokeVirtual(
                "java/lang/invoke/MethodHandle",
                "asFixedArity",
                "()Ljava/lang/invoke/MethodHandle;");
        ib.ldc(org.objectweb.asm.Type.getType("[Ljava/lang/Object;"));
        ib.aload(2);
        ib.invokeVirtual("java/lang/invoke/MethodType", "parameterCount", "()I");
        ib.invokeVirtual(
                "java/lang/invoke/MethodHandle",
                "asSpreader",
                "(Ljava/lang/Class;I)Ljava/lang/invoke/MethodHandle;");
        ib.astore(handle);
        ib.aload(handle);
        emitInvokerHandleType(ib);
        ib.invokeVirtual(
                "java/lang/invoke/MethodHandle",
                "asType",
                "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;");
        ib.areturn();
        return method;
    }

    private MethodNode genAdaptConstructorHandleMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                "adaptConstructorHandle",
                "(Ljava/lang/reflect/Constructor;I)Ljava/lang/invoke/MethodHandle;",
                new String[]{"java/lang/IllegalAccessException"});
        InsnBuilder ib = new InsnBuilder(method.instructions);
        int handle = 2;

        ib.invokeStatic("java/lang/invoke/MethodHandles", "lookup", "()Ljava/lang/invoke/MethodHandles$Lookup;");
        ib.aload(0);
        ib.invokeVirtual(
                "java/lang/invoke/MethodHandles$Lookup",
                "unreflectConstructor",
                "(Ljava/lang/reflect/Constructor;)Ljava/lang/invoke/MethodHandle;");
        ib.invokeVirtual(
                "java/lang/invoke/MethodHandle",
                "asFixedArity",
                "()Ljava/lang/invoke/MethodHandle;");
        ib.ldc(org.objectweb.asm.Type.getType("[Ljava/lang/Object;"));
        ib.iload(1);
        ib.invokeVirtual(
                "java/lang/invoke/MethodHandle",
                "asSpreader",
                "(Ljava/lang/Class;I)Ljava/lang/invoke/MethodHandle;");
        ib.astore(handle);

        emitDropLeadingObjectArgument(ib, handle);
        ib.aload(handle);
        emitInvokerHandleType(ib);
        ib.invokeVirtual(
                "java/lang/invoke/MethodHandle",
                "asType",
                "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;");
        ib.areturn();
        return method;
    }

    private MethodNode genCoerceArgumentMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                "coerceArgument",
                "(Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;");
        InsnBuilder ib = new InsnBuilder(method.instructions);
        LabelNode booleanType = new LabelNode();
        LabelNode charType = new LabelNode();
        LabelNode byteType = new LabelNode();
        LabelNode shortType = new LabelNode();
        LabelNode intType = new LabelNode();
        LabelNode longType = new LabelNode();
        LabelNode floatType = new LabelNode();
        LabelNode doubleType = new LabelNode();
        LabelNode originalValue = new LabelNode();
        LabelNode falseValue = new LabelNode();
        LabelNode boxBoolean = new LabelNode();

        ib.aload(1);
        ib.getStatic("java/lang/Boolean", "TYPE", "Ljava/lang/Class;");
        ib.ifAcmpEq(booleanType);
        ib.aload(1);
        ib.getStatic("java/lang/Character", "TYPE", "Ljava/lang/Class;");
        ib.ifAcmpEq(charType);
        ib.aload(1);
        ib.getStatic("java/lang/Byte", "TYPE", "Ljava/lang/Class;");
        ib.ifAcmpEq(byteType);
        ib.aload(1);
        ib.getStatic("java/lang/Short", "TYPE", "Ljava/lang/Class;");
        ib.ifAcmpEq(shortType);
        ib.aload(1);
        ib.getStatic("java/lang/Integer", "TYPE", "Ljava/lang/Class;");
        ib.ifAcmpEq(intType);
        ib.aload(1);
        ib.getStatic("java/lang/Long", "TYPE", "Ljava/lang/Class;");
        ib.ifAcmpEq(longType);
        ib.aload(1);
        ib.getStatic("java/lang/Float", "TYPE", "Ljava/lang/Class;");
        ib.ifAcmpEq(floatType);
        ib.aload(1);
        ib.getStatic("java/lang/Double", "TYPE", "Ljava/lang/Class;");
        ib.ifAcmpEq(doubleType);
        ib.goto_(originalValue);

        ib.label(booleanType);
        ib.aload(0);
        TypeUtils.unboxIntLike(ib);
        ib.ifeq(falseValue);
        ib.iconst1();
        ib.goto_(boxBoolean);
        ib.label(falseValue);
        ib.iconst0();
        ib.label(boxBoolean);
        ib.invokeStatic("java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;");
        ib.areturn();

        ib.label(charType);
        ib.aload(0);
        TypeUtils.unboxIntLike(ib);
        ib.i2c();
        ib.invokeStatic("java/lang/Character", "valueOf", "(C)Ljava/lang/Character;");
        ib.areturn();

        ib.label(byteType);
        ib.aload(0);
        TypeUtils.unboxIntLike(ib);
        ib.i2b();
        ib.invokeStatic("java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;");
        ib.areturn();

        ib.label(shortType);
        ib.aload(0);
        TypeUtils.unboxIntLike(ib);
        ib.i2s();
        ib.invokeStatic("java/lang/Short", "valueOf", "(S)Ljava/lang/Short;");
        ib.areturn();

        ib.label(intType);
        ib.aload(0);
        TypeUtils.unboxIntLike(ib);
        ib.invokeStatic("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;");
        ib.areturn();

        ib.label(longType);
        ib.aload(0);
        ib.checkCast("java/lang/Number");
        ib.invokeVirtual("java/lang/Number", "longValue", "()J");
        ib.invokeStatic("java/lang/Long", "valueOf", "(J)Ljava/lang/Long;");
        ib.areturn();

        ib.label(floatType);
        ib.aload(0);
        ib.checkCast("java/lang/Number");
        ib.invokeVirtual("java/lang/Number", "floatValue", "()F");
        ib.invokeStatic("java/lang/Float", "valueOf", "(F)Ljava/lang/Float;");
        ib.areturn();

        ib.label(doubleType);
        ib.aload(0);
        ib.checkCast("java/lang/Number");
        ib.invokeVirtual("java/lang/Number", "doubleValue", "()D");
        ib.invokeStatic("java/lang/Double", "valueOf", "(D)Ljava/lang/Double;");
        ib.areturn();

        ib.label(originalValue);
        ib.aload(0);
        ib.areturn();
        return method;
    }

    private MethodNode genCloneArrayMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                "cloneArray",
                "(Ljava/lang/Object;)Ljava/lang/Object;");
        InsnBuilder ib = new InsnBuilder(method.instructions);
        int length = 1;
        int clone = 2;

        ib.aload(0);
        ib.invokeStatic("java/lang/reflect/Array", "getLength", "(Ljava/lang/Object;)I");
        ib.istore(length);

        ib.aload(0);
        ib.invokeVirtual("java/lang/Object", "getClass", "()Ljava/lang/Class;");
        ib.invokeVirtual("java/lang/Class", "getComponentType", "()Ljava/lang/Class;");
        ib.iload(length);
        ib.invokeStatic(
                "java/lang/reflect/Array",
                "newInstance",
                "(Ljava/lang/Class;I)Ljava/lang/Object;");
        ib.astore(clone);

        ib.aload(0);
        ib.iconst0();
        ib.aload(clone);
        ib.iconst0();
        ib.iload(length);
        ib.invokeStatic(
                "java/lang/System",
                "arraycopy",
                "(Ljava/lang/Object;ILjava/lang/Object;II)V");

        ib.aload(clone);
        ib.areturn();
        return method;
    }

    private MethodNode genLoadOwnerMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                "loadOwner",
                "(Ljava/lang/String;)Ljava/lang/Class;");
        InsnBuilder ib = new InsnBuilder(method.instructions);
        ib.aload(0);
        ib.ldc(org.objectweb.asm.Type.getObjectType(className()));
        ib.invokeVirtual("java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;");
        vmLayout.loadOwnerWithLoader.invokeStatic(ib);
        ib.areturn();
        return method;
    }

    private MethodNode genLoadOwnerWithLoaderMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                "loadOwner",
                "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/Class;");
        InsnBuilder ib = new InsnBuilder(method.instructions);
        LabelNode primitiveBoolean = new LabelNode();
        LabelNode primitiveChar = new LabelNode();
        LabelNode primitiveByte = new LabelNode();
        LabelNode primitiveShort = new LabelNode();
        LabelNode primitiveInt = new LabelNode();
        LabelNode primitiveFloat = new LabelNode();
        LabelNode primitiveLong = new LabelNode();
        LabelNode primitiveDouble = new LabelNode();
        LabelNode primitiveVoid = new LabelNode();
        LabelNode notPrimitive = new LabelNode();
        LabelNode notObjectDescriptor = new LabelNode();
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        method.tryCatchBlocks.add(new org.objectweb.asm.tree.TryCatchBlockNode(
                start, end, handler, "java/lang/ClassNotFoundException"));

        ib.aload(0);
        ib.invokeVirtual("java/lang/String", "length", "()I");
        ib.iconst1();
        ib.ifIcmpNe(notPrimitive);
        ib.aload(0);
        ib.iconst0();
        ib.invokeVirtual("java/lang/String", "charAt", "(I)C");
        ib.bipush('Z');
        ib.ifIcmpEq(primitiveBoolean);
        ib.aload(0);
        ib.iconst0();
        ib.invokeVirtual("java/lang/String", "charAt", "(I)C");
        ib.bipush('C');
        ib.ifIcmpEq(primitiveChar);
        ib.aload(0);
        ib.iconst0();
        ib.invokeVirtual("java/lang/String", "charAt", "(I)C");
        ib.bipush('B');
        ib.ifIcmpEq(primitiveByte);
        ib.aload(0);
        ib.iconst0();
        ib.invokeVirtual("java/lang/String", "charAt", "(I)C");
        ib.bipush('S');
        ib.ifIcmpEq(primitiveShort);
        ib.aload(0);
        ib.iconst0();
        ib.invokeVirtual("java/lang/String", "charAt", "(I)C");
        ib.bipush('I');
        ib.ifIcmpEq(primitiveInt);
        ib.aload(0);
        ib.iconst0();
        ib.invokeVirtual("java/lang/String", "charAt", "(I)C");
        ib.bipush('F');
        ib.ifIcmpEq(primitiveFloat);
        ib.aload(0);
        ib.iconst0();
        ib.invokeVirtual("java/lang/String", "charAt", "(I)C");
        ib.bipush('J');
        ib.ifIcmpEq(primitiveLong);
        ib.aload(0);
        ib.iconst0();
        ib.invokeVirtual("java/lang/String", "charAt", "(I)C");
        ib.bipush('D');
        ib.ifIcmpEq(primitiveDouble);
        ib.aload(0);
        ib.iconst0();
        ib.invokeVirtual("java/lang/String", "charAt", "(I)C");
        ib.bipush('V');
        ib.ifIcmpEq(primitiveVoid);

        ib.label(notPrimitive);
        ib.aload(0);
        ib.ldc("L");
        ib.invokeVirtual("java/lang/String", "startsWith", "(Ljava/lang/String;)Z");
        ib.ifeq(notObjectDescriptor);
        ib.aload(0);
        ib.ldc(";");
        ib.invokeVirtual("java/lang/String", "endsWith", "(Ljava/lang/String;)Z");
        ib.ifeq(notObjectDescriptor);
        ib.aload(0);
        ib.iconst1();
        ib.aload(0);
        ib.invokeVirtual("java/lang/String", "length", "()I");
        ib.iconst1();
        ib.isub();
        ib.invokeVirtual("java/lang/String", "substring", "(II)Ljava/lang/String;");
        ib.astore(0);

        ib.label(notObjectDescriptor);
        ib.label(start);
        ib.aload(0);
        ib.bipush('/');
        ib.bipush('.');
        ib.invokeVirtual("java/lang/String", "replace", "(CC)Ljava/lang/String;");
        ib.iconst0();
        ib.aload(1);
        ib.invokeStatic(
                "java/lang/Class",
                "forName",
                "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;");
        ib.label(end);
        ib.areturn();

        ib.label(handler);
        ib.astore(1);
        ib.new_("java/lang/IllegalStateException");
        ib.dup();
        ib.aload(1);
        ib.invokeSpecial("java/lang/IllegalStateException", "<init>", "(Ljava/lang/Throwable;)V");
        ib.athrow();

        ib.label(primitiveBoolean);
        ib.getStatic("java/lang/Boolean", "TYPE", "Ljava/lang/Class;");
        ib.areturn();
        ib.label(primitiveChar);
        ib.getStatic("java/lang/Character", "TYPE", "Ljava/lang/Class;");
        ib.areturn();
        ib.label(primitiveByte);
        ib.getStatic("java/lang/Byte", "TYPE", "Ljava/lang/Class;");
        ib.areturn();
        ib.label(primitiveShort);
        ib.getStatic("java/lang/Short", "TYPE", "Ljava/lang/Class;");
        ib.areturn();
        ib.label(primitiveInt);
        ib.getStatic("java/lang/Integer", "TYPE", "Ljava/lang/Class;");
        ib.areturn();
        ib.label(primitiveFloat);
        ib.getStatic("java/lang/Float", "TYPE", "Ljava/lang/Class;");
        ib.areturn();
        ib.label(primitiveLong);
        ib.getStatic("java/lang/Long", "TYPE", "Ljava/lang/Class;");
        ib.areturn();
        ib.label(primitiveDouble);
        ib.getStatic("java/lang/Double", "TYPE", "Ljava/lang/Class;");
        ib.areturn();
        ib.label(primitiveVoid);
        ib.getStatic("java/lang/Void", "TYPE", "Ljava/lang/Class;");
        ib.areturn();
        return method;
    }

    private MethodNode genRethrowMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                "rethrow",
                "(Ljava/lang/Throwable;)Ljava/lang/RuntimeException;");
        InsnBuilder ib = new InsnBuilder(method.instructions);
        ib.aload(0);
        ib.athrow();
        return method;
    }

    private MethodNode genMonitorForMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.SYNCHRONIZED},
                "monitorFor",
                "(Ljava/lang/Object;)Ljava/util/concurrent/locks/ReentrantLock;");
        InsnBuilder ib = new InsnBuilder(method.instructions);
        LabelNode notNull = new LabelNode();
        LabelNode existing = new LabelNode();

        ib.aload(0);
        ib.ifNonNull(notNull);
        ib.new_("java/lang/NullPointerException");
        ib.dup();
        ib.invokeSpecial("java/lang/NullPointerException", "<init>", "()V");
        ib.athrow();

        ib.label(notNull);
        vmLayout.monitors.getStatic(ib);
        ib.aload(0);
        ib.invokeInterface("java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;");
        ib.checkCast("java/util/concurrent/locks/ReentrantLock");
        ib.astore(1);

        ib.aload(1);
        ib.ifNonNull(existing);
        ib.new_("java/util/concurrent/locks/ReentrantLock");
        ib.dup();
        ib.invokeSpecial("java/util/concurrent/locks/ReentrantLock", "<init>", "()V");
        ib.astore(1);

        vmLayout.monitors.getStatic(ib);
        ib.aload(0);
        ib.aload(1);
        ib.invokeInterface(
                "java/util/Map",
                "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
        ib.pop();

        ib.label(existing);
        ib.aload(1);
        ib.areturn();
        return method;
    }

    private MethodNode genMonitorEnterMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.monitorEnter.name(),
                vmLayout.monitorEnter.descriptor());
        InsnBuilder ib = new InsnBuilder(method.instructions);
        ib.aload(0);
        vmLayout.monitorFor.invokeStatic(ib);
        ib.invokeVirtual("java/util/concurrent/locks/ReentrantLock", "lock", "()V");
        ib._return();
        return method;
    }

    private MethodNode genMonitorExitMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.monitorExit.name(),
                vmLayout.monitorExit.descriptor());
        InsnBuilder ib = new InsnBuilder(method.instructions);
        ib.aload(0);
        vmLayout.monitorFor.invokeStatic(ib);
        ib.invokeVirtual("java/util/concurrent/locks/ReentrantLock", "unlock", "()V");
        ib._return();
        return method;
    }

    private void emitCoerceArguments(
            InsnBuilder ib,
            int argumentsLocal,
            int methodTypeLocal,
            int indexLocal)
    {
        LabelNode loop = new LabelNode();
        LabelNode done = new LabelNode();

        ib.iconst0();
        ib.istore(indexLocal);
        ib.label(loop);
        ib.iload(indexLocal);
        ib.aload(argumentsLocal);
        ib.arrayLength();
        ib.ifIcmpGe(done);

        ib.aload(argumentsLocal);
        ib.iload(indexLocal);
        ib.aload(argumentsLocal);
        ib.iload(indexLocal);
        ib.aaload();
        ib.aload(methodTypeLocal);
        ib.iload(indexLocal);
        ib.invokeVirtual("java/lang/invoke/MethodType", "parameterType", "(I)Ljava/lang/Class;");
        ib.invokeStatic(
                className(),
                "coerceArgument",
                "(Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;");
        ib.aastore();

        ib.iinc(indexLocal, 1);
        ib.goto_(loop);
        ib.label(done);
    }

    private static void emitExceptionWithInt(
            InsnBuilder ib,
            String exceptionType,
            String prefix,
            int intLocal)
    {
        ib.new_(exceptionType);
        ib.dup();
        ib.new_("java/lang/StringBuilder");
        ib.dup();
        ib.ldc(prefix);
        ib.invokeSpecial("java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V");
        ib.iload(intLocal);
        ib.invokeVirtual("java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;");
        ib.invokeVirtual("java/lang/StringBuilder", "toString", "()Ljava/lang/String;");
        ib.invokeSpecial(exceptionType, "<init>", "(Ljava/lang/String;)V");
        ib.athrow();
    }

    private static void emitFieldHandleKey(InsnBuilder ib)
    {
        ib.new_("java/lang/StringBuilder");
        ib.dup();
        ib.aload(0);
        ib.invokeSpecial("java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V");
        appendString(ib, ".");
        ib.aload(1);
        ib.invokeVirtual("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        appendString(ib, ":");
        ib.aload(2);
        ib.invokeVirtual("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        appendString(ib, ":");
        ib.iload(3);
        ib.invokeVirtual("java/lang/StringBuilder", "append", "(Z)Ljava/lang/StringBuilder;");
        appendString(ib, ":");
        ib.iload(4);
        ib.invokeVirtual("java/lang/StringBuilder", "append", "(Z)Ljava/lang/StringBuilder;");
        ib.invokeVirtual("java/lang/StringBuilder", "toString", "()Ljava/lang/String;");
    }

    private static void emitMethodHandleKey(InsnBuilder ib)
    {
        ib.new_("java/lang/StringBuilder");
        ib.dup();
        ib.aload(0);
        ib.invokeSpecial("java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V");
        appendString(ib, ".");
        ib.aload(1);
        ib.invokeVirtual("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        ib.aload(2);
        ib.invokeVirtual("java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;");
        appendString(ib, ":");
        ib.iload(3);
        ib.invokeVirtual("java/lang/StringBuilder", "append", "(Z)Ljava/lang/StringBuilder;");
        ib.invokeVirtual("java/lang/StringBuilder", "toString", "()Ljava/lang/String;");
    }

    private static void appendString(InsnBuilder ib, String value)
    {
        ib.ldc(value);
        ib.invokeVirtual("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
    }

    private static void emitDropLeadingObjectArgument(InsnBuilder ib, int handleLocal)
    {
        ib.aload(handleLocal);
        ib.iconst0();
        ib.iconst1();
        ib.aneArray("java/lang/Class");
        ib.dup();
        ib.iconst0();
        emitObjectClass(ib);
        ib.aastore();
        ib.invokeStatic(
                "java/lang/invoke/MethodHandles",
                "dropArguments",
                "(Ljava/lang/invoke/MethodHandle;I[Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;");
        ib.astore(handleLocal);
    }

    private static void emitInvokerHandleType(InsnBuilder ib)
    {
        emitObjectClass(ib);
        emitObjectClass(ib);
        ib.iconst1();
        ib.aneArray("java/lang/Class");
        ib.dup();
        ib.iconst0();
        ib.ldc(org.objectweb.asm.Type.getType("[Ljava/lang/Object;"));
        ib.aastore();
        ib.invokeStatic(
                "java/lang/invoke/MethodType",
                "methodType",
                "(Ljava/lang/Class;Ljava/lang/Class;[Ljava/lang/Class;)Ljava/lang/invoke/MethodType;");
    }

    private static void emitGetterHandleType(InsnBuilder ib)
    {
        emitObjectClass(ib);
        emitObjectClass(ib);
        ib.invokeStatic(
                "java/lang/invoke/MethodType",
                "methodType",
                "(Ljava/lang/Class;Ljava/lang/Class;)Ljava/lang/invoke/MethodType;");
    }

    private static void emitSetterHandleType(InsnBuilder ib)
    {
        ib.getStatic("java/lang/Void", "TYPE", "Ljava/lang/Class;");
        emitObjectClass(ib);
        ib.iconst1();
        ib.aneArray("java/lang/Class");
        ib.dup();
        ib.iconst0();
        emitObjectClass(ib);
        ib.aastore();
        ib.invokeStatic(
                "java/lang/invoke/MethodType",
                "methodType",
                "(Ljava/lang/Class;Ljava/lang/Class;[Ljava/lang/Class;)Ljava/lang/invoke/MethodType;");
    }

    private static void emitObjectClass(InsnBuilder ib)
    {
        ib.ldc(org.objectweb.asm.Type.getType("Ljava/lang/Object;"));
    }

    private static void emitNoSuchField(InsnBuilder ib, int ownerClassLocal)
    {
        ib.new_("java/lang/NoSuchFieldException");
        ib.dup();
        ib.new_("java/lang/StringBuilder");
        ib.dup();
        ib.aload(ownerClassLocal);
        ib.invokeVirtual("java/lang/Class", "getName", "()Ljava/lang/String;");
        ib.invokeSpecial("java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V");
        appendString(ib, ".");
        ib.aload(1);
        ib.invokeVirtual("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        ib.invokeVirtual("java/lang/StringBuilder", "toString", "()Ljava/lang/String;");
        ib.invokeSpecial("java/lang/NoSuchFieldException", "<init>", "(Ljava/lang/String;)V");
        ib.athrow();
    }

    private static void emitNoSuchMethod(InsnBuilder ib, int ownerClassLocal)
    {
        ib.new_("java/lang/NoSuchMethodException");
        ib.dup();
        ib.new_("java/lang/StringBuilder");
        ib.dup();
        ib.aload(ownerClassLocal);
        ib.invokeVirtual("java/lang/Class", "getName", "()Ljava/lang/String;");
        ib.invokeSpecial("java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V");
        appendString(ib, ".");
        ib.aload(1);
        ib.invokeVirtual("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        ib.aload(2);
        ib.invokeVirtual("java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;");
        ib.invokeVirtual("java/lang/StringBuilder", "toString", "()Ljava/lang/String;");
        ib.invokeSpecial("java/lang/NoSuchMethodException", "<init>", "(Ljava/lang/String;)V");
        ib.athrow();
    }

    private MethodNode genClInitMethod(List<CodePoolGenerator> codePoolGenerators)
    {
        MethodNode initMethod = MethodUtils.newMethodNode(new Acc[]{Acc.STATIC}, "<clinit>", "()V");
        InsnBuilder ib = new InsnBuilder();

        ib.pushInt(codePoolGenerators.size());
        ib.aneArray(vmCodePoolGenerator.className());
        for (int i = 0; i < codePoolGenerators.size(); i++)
        {
            ib.dup();
            ib.pushInt(i);
            ib.getStatic(codePoolGenerators.get(i).className(), "INSTANCE", vmCodePoolGenerator.descriptor());
            ib.aastore();
        }
        ib.invokeStatic("java/util/Arrays", "asList", "([Ljava/lang/Object;)Ljava/util/List;");
        vmLayout.codePools.putStatic(ib);

        ib.new_("java/util/concurrent/ConcurrentHashMap");
        ib.dup();
        ib.invokeSpecial("java/util/concurrent/ConcurrentHashMap", "<init>", "()V");
        vmLayout.fieldHandles.putStatic(ib);

        ib.new_("java/util/concurrent/ConcurrentHashMap");
        ib.dup();
        ib.invokeSpecial("java/util/concurrent/ConcurrentHashMap", "<init>", "()V");
        vmLayout.methodHandles.putStatic(ib);

        ib.new_("java/util/concurrent/ConcurrentHashMap");
        ib.dup();
        ib.invokeSpecial("java/util/concurrent/ConcurrentHashMap", "<init>", "()V");
        vmLayout.methodTypes.putStatic(ib);

        ib.new_("java/util/WeakHashMap");
        ib.dup();
        ib.invokeSpecial("java/util/WeakHashMap", "<init>", "()V");
        ib.invokeStatic(
                "java/util/Collections",
                "synchronizedMap",
                "(Ljava/util/Map;)Ljava/util/Map;");
        vmLayout.monitors.putStatic(ib);

        ib._return();

        initMethod.instructions.add(ib.toInsnList());
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

    private static void validateBranches()
    {
        for (Opcs opcode : Opcs.values())
        {
            if (!branches.containsKey(opcode))
            {
                throw new IllegalStateException("No InterpretBranch for " + opcode);
            }
        }
    }
}
