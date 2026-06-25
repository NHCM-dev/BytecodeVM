package nhcm.bytecodevm.Generator.Virtualization;

import lombok.Getter;
import nhcm.bytecodevm.Config.BytecodeVMConfig;
import nhcm.bytecodevm.Enums.Acc;
import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.AdvInsn.Expr;
import nhcm.bytecodevm.AdvInsn.Local;
import nhcm.bytecodevm.AdvInsn.SwitchCase;
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
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Invoke.InvokeDynamicBranch;
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
import nhcm.bytecodevm.Utils.ClassUtils;
import nhcm.bytecodevm.Utils.FieldUtils;
import nhcm.bytecodevm.Utils.InsnUtils;
import nhcm.bytecodevm.Utils.MethodUtils;
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
    private final int dispatchSalt;

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
        this.dispatchSalt = nhcm.bytecodevm.Utils.RandomUtils.randomInt();
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
        cn.methods.add(genInstructionIndexMethod());
        cn.methods.add(genDecodeOpcodeMethod());
        cn.methods.add(genDecodeNextPcMethod());
        cn.methods.add(genDecodeOriginalPcMethod());
        cn.methods.add(genDecodeOperandMethod());
        cn.methods.add(genMixMethod());
        cn.methods.add(genLayoutValueMethod());
        cn.methods.add(genDispatchKeyMethod());
        cn.methods.add(genDecodeMaybeStringMethod());
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
        AdvInsnBuilder ib = new AdvInsnBuilder(methodNode);
        InterpretContext context = new InterpretContext(
                className(),
                frameLayout.owner,
                programLayout.owner,
                loopStart);

        // int[] code = program.opcodeStream();
        ib.set(context.code(), AdvInsnBuilder.callVirtual(
                context.program(),
                programLayout.owner,
                programLayout.opcodeStream.name(),
                "[I"));
        // Object[] constants = program.constants();
        ib.set(context.constants(), AdvInsnBuilder.callVirtual(
                context.program(),
                programLayout.owner,
                programLayout.constants.name(),
                "[Ljava/lang/Object;"));
        // int[] exceptionHandlers = program.exceptionHandlers();
        ib.set(context.exceptionHandlers(), AdvInsnBuilder.callVirtual(
                context.program(),
                programLayout.owner,
                programLayout.exceptionHandlers.name(),
                "[I"));

        // while (!frame.returned)
        ib.mark(loopStart, "loopStart");
        ib.ifCondition(AdvInsnBuilder.isTrue(context.frameReturned()), b -> b.gotoLabel(loopEnd));

        // int instructionPc = frame.programCounter;
        ib.set(context.instructionPc(), context.frameProgramCounter());
        ib.set(context.originalPc(), context.instructionPc());

        ib.mark(tryStart, "tryStart");
        ib.set(context.instructionIndex(), AdvInsnBuilder.callStatic(
                vmLayout.owner,
                vmLayout.instructionIndex.name(),
                "I",
                context.program(),
                context.instructionPc()));
        ib.set(context.operandIndex(), AdvInsnBuilder.constant(0));
        ib.set(context.opcode(), AdvInsnBuilder.callStatic(
                vmLayout.owner,
                vmLayout.decodeOpcode.name(),
                "I",
                context.program(),
                context.instructionIndex()));
        ib.set(context.frameProgramCounter(), AdvInsnBuilder.callStatic(
                vmLayout.owner,
                vmLayout.decodeNextPc.name(),
                "I",
                context.program(),
                context.instructionIndex()));
        ib.set(context.originalPc(), AdvInsnBuilder.callStatic(
                vmLayout.owner,
                vmLayout.decodeOriginalPc.name(),
                "I",
                context.program(),
                context.instructionIndex()));

        generateDispatch(
                ib,
                loopStart,
                unknownOpcode
        );
        ib.mark(tryEnd, "tryEnd");

        ib.mark(unknownOpcode, "unknownOpcode");
        generateUnknownOpcode(ib);

        ib.mark(exceptionHandler, "exceptionHandler");
        ib.storeTop(context.thrown());
        ib.set(context.handlerPc(), AdvInsnBuilder.callStatic(
                vmLayout.owner,
                vmLayout.findExceptionHandler.name(),
                "I",
                context.thrown(),
                context.exceptionHandlers(),
                context.originalPc(),
                AdvInsnBuilder.callVirtual(context.program(), programLayout.owner, programLayout.methodKey.name(), "I"),
                context.constants()));
        ib.ifCondition(AdvInsnBuilder.equal(context.handlerPc(), AdvInsnBuilder.constant(-1)), b -> b.gotoLabel(noHandler));
        ib.set(context.frameField(frameLayout.stackPointer), AdvInsnBuilder.constant(0));
        ib.directCall(AdvInsnBuilder.callVirtual(
                context.frame(),
                frameLayout.owner,
                frameLayout.push.name(),
                "V",
                AdvInsnBuilder.cast(context.thrown(), "java/lang/Object")));
        ib.set(context.frameProgramCounter(), context.handlerPc());
        ib.gotoLabel(loopStart);

        ib.mark(noHandler, "noHandler");
        ib.throwValue(AdvInsnBuilder.callStatic(
                vmLayout.owner,
                vmLayout.rethrow.name(),
                "java/lang/RuntimeException",
                context.thrown()));

        ib.mark(loopEnd, "loopEnd");
        ib.returnVoid();

        return methodNode;
    }

    private MethodNode genInstructionIndexMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, vmLayout.instructionIndex.name(), vmLayout.instructionIndex.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local program = ib.getLocal("program", programLayout.owner, 0);
        Local pc = ib.getLocal("pc", "I", 1);
        Local index = ib.getLocal("index", "I", 2);
        Local count = ib.getLocal("count", "I", 3);

        ib.set(count, AdvInsnBuilder.divide(
                AdvInsnBuilder.arrayLength(callProgramArray(program, programLayout.layoutStream.name())),
                AdvInsnBuilder.constant(ProtectedVMMethod.RECORD_SIZE)));
        ib.forLoop(
                b -> b.set(index, AdvInsnBuilder.constant(0)),
                AdvInsnBuilder.lessThan(index, count),
                b -> b.increment(index, 1),
                b -> b.ifCondition(
                        AdvInsnBuilder.equal(
                                layoutValue(program, index, AdvInsnBuilder.constant(ProtectedVMMethod.LAYOUT_PC)),
                                pc),
                        found -> found.returnValue(index)));
        ib.forLoop(
                b -> b.set(index, AdvInsnBuilder.constant(0)),
                AdvInsnBuilder.lessThan(index, count),
                b -> b.increment(index, 1),
                b -> b.ifCondition(
                        AdvInsnBuilder.equal(
                                layoutValue(program, index, AdvInsnBuilder.constant(ProtectedVMMethod.LAYOUT_ORIGINAL_PC)),
                                pc),
                        found -> found.returnValue(index)));
        ib.throwValue(AdvInsnBuilder.newObject(
                "java/lang/IllegalStateException",
                stringConcat(AdvInsnBuilder.constant("Unknown VM pc "), pc)));
        return method;
    }

    private MethodNode genDecodeOpcodeMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, vmLayout.decodeOpcode.name(), vmLayout.decodeOpcode.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local program = ib.getLocal("program", programLayout.owner, 0);
        Local index = ib.getLocal("instructionIndex", "I", 1);
        Local key = ib.getLocal("methodKey", "I", 2);
        Local virtualPc = ib.getLocal("virtualPc", "I", 3);
        Local virtualOpcode = ib.getLocal("virtualOpcode", "I", 4);
        Local mappedOpcode = ib.getLocal("mappedOpcode", "I", 5);

        ib.set(key, callProgramInt(program, programLayout.methodKey.name()));
        ib.set(virtualPc, layoutValue(program, index, AdvInsnBuilder.constant(ProtectedVMMethod.LAYOUT_PC)));
        ib.set(virtualOpcode, AdvInsnBuilder.arrayAt(callProgramArray(program, programLayout.opcodeStream.name()), index));
        if (config.perMethodOpcodeMap)
        {
            ib.ifCondition(
                    AdvInsnBuilder.notEqual(key, AdvInsnBuilder.constant(0)),
                    b -> b.set(virtualOpcode, AdvInsnBuilder.bitXor(virtualOpcode, mixCall(key, virtualPc, index, AdvInsnBuilder.constant(ProtectedVMMethod.SALT_OPCODE)))));
        }
        ib.set(mappedOpcode, AdvInsnBuilder.arrayAt(callProgramArray(program, programLayout.opcodeMap.name()), virtualOpcode));
        if (config.perMethodOpcodeMap)
        {
            ib.ifCondition(
                    AdvInsnBuilder.notEqual(key, AdvInsnBuilder.constant(0)),
                    b -> b.set(mappedOpcode, AdvInsnBuilder.bitXor(mappedOpcode, mixCall(key, virtualOpcode, AdvInsnBuilder.constant(ProtectedVMMethod.SALT_OPCODE_MAP), AdvInsnBuilder.constant(0)))));
        }
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
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local program = ib.getLocal("program", programLayout.owner, 0);
        Local index = ib.getLocal("instructionIndex", "I", 1);
        ib.returnValue(layoutValue(program, index, AdvInsnBuilder.constant(field)));
        return method;
    }

    private MethodNode genDecodeOperandMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, vmLayout.decodeOperand.name(), vmLayout.decodeOperand.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local program = ib.getLocal("program", programLayout.owner, 0);
        Local instructionIndex = ib.getLocal("instructionIndex", "I", 1);
        Local operandIndex = ib.getLocal("operandIndex", "I", 2);
        Local opcode = ib.getLocal("opcode", "I", 3);
        Local key = ib.getLocal("methodKey", "I", 4);
        Local virtualPc = ib.getLocal("virtualPc", "I", 5);
        Local operandStart = ib.getLocal("operandStart", "I", 6);
        Local operandCount = ib.getLocal("operandCount", "I", 7);
        Local operandPosition = ib.getLocal("operandPosition", "I", 8);
        Local constantMask = ib.getLocal("constantMask", "I", 9);
        Local value = ib.getLocal("value", "I", 10);

        ib.set(key, callProgramInt(program, programLayout.methodKey.name()));
        ib.set(virtualPc, layoutValue(program, instructionIndex, AdvInsnBuilder.constant(ProtectedVMMethod.LAYOUT_PC)));
        ib.set(operandStart, layoutValue(program, instructionIndex, AdvInsnBuilder.constant(ProtectedVMMethod.LAYOUT_OPERAND_START)));
        ib.set(operandCount, layoutValue(program, instructionIndex, AdvInsnBuilder.constant(ProtectedVMMethod.LAYOUT_OPERAND_COUNT)));
        ib.ifCondition(
                AdvInsnBuilder.greaterOrEqual(operandIndex, operandCount),
                b -> b.throwValue(AdvInsnBuilder.newObject(
                        "java/lang/IllegalStateException",
                        stringConcat(AdvInsnBuilder.constant("Operand out of range "), operandIndex))));
        ib.set(operandPosition, AdvInsnBuilder.plus(operandStart, operandIndex));
        ib.set(value, AdvInsnBuilder.arrayAt(callProgramArray(program, programLayout.operandStream.name()), operandPosition));
        if (config.encryptOperands || config.bindConstantsToOperands)
        {
            ib.ifCondition(
                    AdvInsnBuilder.notEqual(key, AdvInsnBuilder.constant(0)),
                    b -> {
                        if (config.encryptOperands)
                        {
                            b.set(value, AdvInsnBuilder.bitXor(
                                    value,
                                    mixCall(
                                            AdvInsnBuilder.bitXor(key, opcode),
                                            virtualPc,
                                            operandIndex,
                                            AdvInsnBuilder.bitXor(AdvInsnBuilder.constant(ProtectedVMMethod.SALT_OPERAND), operandPosition))));
                        }
                        if (config.bindConstantsToOperands)
                        {
                            b.set(constantMask, layoutValue(program, instructionIndex, AdvInsnBuilder.constant(ProtectedVMMethod.LAYOUT_CONSTANT_MASK)));
                            b.ifCondition(
                                    AdvInsnBuilder.notEqual(
                                            AdvInsnBuilder.bitAnd(constantMask, AdvInsnBuilder.shiftLeft(AdvInsnBuilder.constant(1), operandIndex)),
                                            AdvInsnBuilder.constant(0)),
                                    constant -> constant.set(value, AdvInsnBuilder.bitXor(
                                            value,
                                            mixCall(
                                                    AdvInsnBuilder.bitXor(key, opcode),
                                                    virtualPc,
                                                    operandIndex,
                                                    AdvInsnBuilder.constant(ProtectedVMMethod.SALT_CONSTANT)))));
                        }
                    });
        }
        ib.returnValue(value);
        return method;
    }

    private MethodNode genLayoutValueMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, "layoutValue", "(" + vmProgramGenerator.descriptor() + "II)I");
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local program = ib.getLocal("program", programLayout.owner, 0);
        Local instructionIndex = ib.getLocal("instructionIndex", "I", 1);
        Local field = ib.getLocal("field", "I", 2);
        Local key = ib.getLocal("methodKey", "I", 3);
        Local raw = ib.getLocal("raw", "I", 4);

        ib.set(key, callProgramInt(program, programLayout.methodKey.name()));
        ib.set(raw, AdvInsnBuilder.arrayAt(
                callProgramArray(program, programLayout.layoutStream.name()),
                AdvInsnBuilder.plus(
                        AdvInsnBuilder.multiply(instructionIndex, AdvInsnBuilder.constant(ProtectedVMMethod.RECORD_SIZE)),
                        field)));
        ib.ifCondition(AdvInsnBuilder.equal(key, AdvInsnBuilder.constant(0)), b -> b.returnValue(raw));
        ib.returnValue(AdvInsnBuilder.bitXor(raw, mixCall(key, instructionIndex, field, AdvInsnBuilder.constant(ProtectedVMMethod.SALT_LAYOUT))));
        return method;
    }

    private MethodNode genMixMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, "mix", "(IIII)I");
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local key = ib.getLocal("key", "I", 0);
        Local a = ib.getLocal("a", "I", 1);
        Local b = ib.getLocal("b", "I", 2);
        Local c = ib.getLocal("c", "I", 3);
        Local x = ib.getLocal("x", "I", 4);
        ib.set(x, AdvInsnBuilder.bitXor(key, AdvInsnBuilder.constant(0x9e3779b9)));
        mixRound(ib, x, a, 0x7f4a7c15);
        mixRound(ib, x, b, 0x94d049bb);
        mixRound(ib, x, c, 0x2545f491);
        ib.set(x, AdvInsnBuilder.bitXor(x, AdvInsnBuilder.unsignedShiftRight(x, AdvInsnBuilder.constant(16))));
        ib.set(x, AdvInsnBuilder.multiply(x, AdvInsnBuilder.constant(0x7feb352d)));
        ib.set(x, AdvInsnBuilder.bitXor(x, AdvInsnBuilder.unsignedShiftRight(x, AdvInsnBuilder.constant(15))));
        ib.set(x, AdvInsnBuilder.multiply(x, AdvInsnBuilder.constant(0x846ca68b)));
        ib.set(x, AdvInsnBuilder.bitXor(x, AdvInsnBuilder.unsignedShiftRight(x, AdvInsnBuilder.constant(16))));
        ib.returnValue(x);
        return method;
    }

    private MethodNode genDispatchKeyMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, "dispatchKey", "(I)I");
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local opcode = ib.getLocal("opcode", "I", 0);
        ib.returnValue(dispatchKeyExpr(opcode));
        return method;
    }

    private MethodNode genDecodeMaybeStringMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, "decodeMaybeString", "(Ljava/lang/Object;)Ljava/lang/String;");
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local value = ib.getLocal("value", "java/lang/Object", 0);
        Local encoded = ib.getLocal("encoded", "[Ljava/lang/Object;", 1);
        Local chars = ib.getLocal("chars", "[I", 2);
        Local key = ib.getLocal("key", "I", 3);
        Local decoded = ib.getLocal("decoded", "[C", 4);
        Local index = ib.getLocal("index", "I", 5);

        ib.ifCondition(AdvInsnBuilder.isInstanceOf(value, "java/lang/String"), b -> b.returnValue(AdvInsnBuilder.cast(value, "java/lang/String")));
        ib.ifCondition(AdvInsnBuilder.not(AdvInsnBuilder.isInstanceOf(value, "[Ljava/lang/Object;")), b -> b.returnValue(AdvInsnBuilder.cast(value, "java/lang/String")));
        ib.set(encoded, AdvInsnBuilder.cast(value, "[Ljava/lang/Object;"));
        ib.ifCondition(AdvInsnBuilder.notEqual(AdvInsnBuilder.arrayLength(encoded), AdvInsnBuilder.constant(2)), b -> b.returnValue(AdvInsnBuilder.cast(value, "java/lang/String")));
        ib.ifCondition(AdvInsnBuilder.not(AdvInsnBuilder.isInstanceOf(AdvInsnBuilder.arrayAt(encoded, AdvInsnBuilder.constant(0)), "[I")), b -> b.returnValue(AdvInsnBuilder.cast(value, "java/lang/String")));
        ib.ifCondition(AdvInsnBuilder.not(AdvInsnBuilder.isInstanceOf(AdvInsnBuilder.arrayAt(encoded, AdvInsnBuilder.constant(1)), "java/lang/Integer")), b -> b.returnValue(AdvInsnBuilder.cast(value, "java/lang/String")));
        ib.set(chars, AdvInsnBuilder.cast(AdvInsnBuilder.arrayAt(encoded, AdvInsnBuilder.constant(0)), "[I"));
        ib.set(key, AdvInsnBuilder.unbox(AdvInsnBuilder.arrayAt(encoded, AdvInsnBuilder.constant(1)), "I"));
        ib.set(decoded, AdvInsnBuilder.newArray("char", AdvInsnBuilder.arrayLength(chars)));
        ib.forLoop(
                b -> b.set(index, AdvInsnBuilder.constant(0)),
                AdvInsnBuilder.lessThan(index, AdvInsnBuilder.arrayLength(chars)),
                b -> b.increment(index, 1),
                b -> b.setArray(
                        decoded,
                        index,
                        AdvInsnBuilder.cast(
                                AdvInsnBuilder.bitXor(
                                        AdvInsnBuilder.arrayAt(chars, index),
                                        mixCall(key, index, AdvInsnBuilder.constant(ProtectedVMMethod.SALT_STRING), AdvInsnBuilder.constant(0))),
                                "C")));
        ib.returnValue(AdvInsnBuilder.newObject("java/lang/String", decoded));
        return method;
    }

    private void generateDispatch(AdvInsnBuilder ib, LabelNode loopStart, LabelNode unknownOpcode)
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

        InterpretContext context = new InterpretContext(
                className(),
                frameLayout.owner,
                programLayout.owner,
                loopStart);
        SwitchCase[] cases = new SwitchCase[opcodes.size()];
        for (int i = 0; i < opcodes.size(); i++)
        {
            int chunkIndex = i / INTERPRET_CHUNK_SIZE;
            int opcodeIndex = i % INTERPRET_CHUNK_SIZE;
            int mutatedOpcode = opcMutator.toMutated(opcodes.get(i));
            int dispatchKey = config.obfuscateDispatch
                    ? dispatchKey(mutatedOpcode)
                    : mutatedOpcode;
            cases[i] = AdvInsnBuilder.switchCase(dispatchKey, b -> {
                b.directCall(AdvInsnBuilder.callStatic(
                        className(),
                        interpretChunkName(chunkIndex),
                        "V",
                        context.program(),
                        context.frame(),
                        context.code(),
                        context.constants(),
                        context.opcode(),
                        AdvInsnBuilder.constant(opcodeIndex),
                        context.instructionIndex()));
                b.gotoLabel(loopStart);
            });
        }
        Expr selector = config.obfuscateDispatch
                ? AdvInsnBuilder.callStatic(className(), "dispatchKey", "I", context.opcode())
                : context.opcode();
        ib.switchLookup(selector, b -> b.gotoLabel(unknownOpcode), cases);
    }

    private MethodNode genInterpretChunkMethod(int chunkIndex, List<Opcs> opcodes)
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                interpretChunkName(chunkIndex),
                interpretChunkDescriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local opcodeIndex = ib.getLocal("opcodeIndex", "I", InterpretContext.RIGHT_VALUE);
        Local passedInstructionIndex = ib.getLocal("passedInstructionIndex", "I", 6);
        InterpretContext context = new InterpretContext(
                className(),
                frameLayout.owner,
                programLayout.owner,
                null);
        ib.set(context.instructionIndex(), passedInstructionIndex);
        ib.set(context.operandIndex(), AdvInsnBuilder.constant(0));
        @SuppressWarnings("unchecked")
        java.util.function.Consumer<AdvInsnBuilder>[] cases = new java.util.function.Consumer[opcodes.size()];
        for (int i = 0; i < opcodes.size(); i++)
        {
            Opcs opcode = opcodes.get(i);
            InterpretBranch branch = branches.get(opcode);
            if (branch == null)
            {
                throw new IllegalStateException("Missing interpret branch: " + opcode);
            }
            cases[i] = b -> branch.generate(b, context, opcode);
        }

        ib.switchTable(
                opcodeIndex,
                0,
                this::generateUnknownOpcode,
                cases);
        ib.returnVoid();
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
                "[I[Ljava/lang/Object;III)V";
    }

    private void generateUnknownOpcode(AdvInsnBuilder ib)
    {
        Local frame = AdvInsnBuilder.local("frame", frameLayout.owner, InterpretContext.FRAME);
        Local opcode = AdvInsnBuilder.local("opcode", "I", InterpretContext.OPCODE);
        ib.throwValue(AdvInsnBuilder.newObject(
                "java/lang/IllegalStateException",
                stringConcat(
                        AdvInsnBuilder.constant("Unknown VM opcode "),
                        AdvInsnBuilder.callStatic("java/lang/Integer", "toHexString", "java/lang/String", opcode),
                        AdvInsnBuilder.constant(" at pc "),
                        AdvInsnBuilder.minus(AdvInsnBuilder.field(frame, frameLayout.programCounter), AdvInsnBuilder.constant(1)))));
    }

    private MethodNode genExecuteMethod()
    {
        MethodNode methodNode = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC, Acc.STATIC, Acc.VARARGS},
                "execute",
                "(ILjava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;",
                "<T:Ljava/lang/Object;>(ILjava/lang/Object;[Ljava/lang/Object;)TT;",
                null);
        AdvInsnBuilder ib = new AdvInsnBuilder(methodNode);
        Local codeId = ib.getLocal("codeId", "I", 0);
        Local receiver = ib.getLocal("receiver", "java/lang/Object", 1);
        Local arguments = ib.getLocal("arguments", "[Ljava/lang/Object;", 2);
        Local program = ib.getLocal("program", programLayout.owner, 3);
        Local frame = ib.getLocal("frame", frameLayout.owner, 4);
        Local argumentOffset = ib.getLocal("argumentOffset", "I", 5);

        // VMProgram program = resolve(codeId);
        ib.set(program, AdvInsnBuilder.callStatic(
                vmLayout.owner,
                vmLayout.resolve.name(),
                programLayout.owner,
                codeId));

        // MethodFrame frame = new MethodFrame(program.maxLocals(), program.maxStack());
        ib.set(frame, AdvInsnBuilder.newObject(
                frameLayout.owner,
                AdvInsnBuilder.callVirtual(program, programLayout.owner, programLayout.maxLocals.name(), "I"),
                AdvInsnBuilder.callVirtual(program, programLayout.owner, programLayout.maxStack.name(), "I")));

        // Instance methods reserve locals[0] for the receiver.
        ib.ifElse(
                AdvInsnBuilder.notNull(receiver),
                b -> {
                    b.setArray(AdvInsnBuilder.field(frame, frameLayout.locals), AdvInsnBuilder.constant(0), receiver);
                    b.set(argumentOffset, AdvInsnBuilder.constant(1));
                },
                b -> b.set(argumentOffset, AdvInsnBuilder.constant(0)));

        // System.arraycopy(arguments, 0, frame.locals, argumentOffset, arguments.length);
        ib.directCall(AdvInsnBuilder.callStatic(
                "java/lang/System",
                "arraycopy",
                "V",
                AdvInsnBuilder.cast(arguments, "java/lang/Object"),
                AdvInsnBuilder.constant(0),
                AdvInsnBuilder.cast(AdvInsnBuilder.field(frame, frameLayout.locals), "java/lang/Object"),
                argumentOffset,
                AdvInsnBuilder.arrayLength(arguments)));

        // interpret(program, frame);
        ib.directCall(AdvInsnBuilder.callStatic(
                vmLayout.owner,
                vmLayout.interpret.name(),
                "V",
                program,
                frame));

        ib.returnValue(AdvInsnBuilder.field(frame, frameLayout.returnValue));
        return methodNode;
    }

    private MethodNode genResolveMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.resolve.name(),
                vmLayout.resolve.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local codeId = ib.getLocal("codeId", "I", 0);
        Local resolved = ib.getLocal("resolved", programLayout.owner, 1);
        Local iterator = ib.getLocal("iterator", "java/util/Iterator", 2);
        Local candidate = ib.getLocal("candidate", programLayout.owner, 3);

        ib.set(resolved, AdvInsnBuilder.nullValue(programLayout.owner));
        ib.set(iterator, AdvInsnBuilder.callInterface(
                AdvInsnBuilder.staticField(vmLayout.codePools),
                "java/util/List",
                "iterator",
                "java/util/Iterator"));

        ib.whileLoop(
                AdvInsnBuilder.isTrue(AdvInsnBuilder.callInterface(
                        iterator,
                        "java/util/Iterator",
                        "hasNext",
                        "Z")),
                b -> {
                    Expr pool = AdvInsnBuilder.cast(
                            AdvInsnBuilder.callInterface(
                                    iterator,
                                    "java/util/Iterator",
                                    "next",
                                    "java/lang/Object"),
                            vmCodePoolGenerator.className());
                    b.set(candidate, AdvInsnBuilder.callInterface(
                            pool,
                            vmCodePoolGenerator.className(),
                            "find",
                            programLayout.owner,
                            codeId));
                    b.ifCondition(
                            AdvInsnBuilder.notNull(candidate),
                            found -> {
                                found.ifCondition(
                                        AdvInsnBuilder.notNull(resolved),
                                        duplicate -> throwExceptionWithInt(
                                                duplicate,
                                                "java/lang/IllegalStateException",
                                                "Duplicate code id: ",
                                                codeId));
                                found.set(resolved, candidate);
                            });
                });

        ib.ifCondition(
                AdvInsnBuilder.isNull(resolved),
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
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local constants = ib.getLocal("constants", "[Ljava/lang/Object;", 0);
        Local index = ib.getLocal("index", "I", 1);
        ib.returnValue(decodeMaybeString(AdvInsnBuilder.arrayAt(constants, index)));
        return method;
    }

    private MethodNode genMethodTypeMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.methodType.name(),
                vmLayout.methodType.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local descriptor = ib.getLocal("descriptor", "java/lang/String", 0);
        Local cached = ib.getLocal("cached", "java/lang/invoke/MethodType", 1);

        ib.set(cached, AdvInsnBuilder.cast(mapGet(AdvInsnBuilder.staticField(vmLayout.methodTypes), descriptor), "java/lang/invoke/MethodType"));
        ib.ifCondition(AdvInsnBuilder.notNull(cached), b -> b.returnValue(cached));

        ib.set(cached, AdvInsnBuilder.callStatic(
                "java/lang/invoke/MethodType",
                "fromMethodDescriptorString",
                "java/lang/invoke/MethodType",
                descriptor,
                AdvInsnBuilder.callVirtual(
                        AdvInsnBuilder.constant(org.objectweb.asm.Type.getObjectType(className())),
                        "java/lang/Class",
                        "getClassLoader",
                        "java/lang/ClassLoader")));
        ib.directCall(mapPut(AdvInsnBuilder.staticField(vmLayout.methodTypes), descriptor, cached));
        ib.returnValue(cached);
        return method;
    }

    private MethodNode genResolveConstantMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.resolveConstant.name(),
                vmLayout.resolveConstant.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local value = ib.getLocal("value", "java/lang/Object", 0);
        Local frame = ib.getLocal("frame", frameLayout.owner, 1);
        Local encoded = ib.getLocal("encoded", "[Ljava/lang/Object;", 2);
        Local descriptor = ib.getLocal("descriptor", "java/lang/String", 3);
        Local loader = ib.getLocal("loader", "java/lang/ClassLoader", 4);
        Local receiver = ib.getLocal("receiver", "java/lang/Object", 5);
        Local marker = ib.getLocal("marker", "java/lang/String", 6);

        ib.ifCondition(AdvInsnBuilder.not(AdvInsnBuilder.isInstanceOf(value, "[Ljava/lang/Object;")), b -> b.returnValue(value));
        ib.set(encoded, AdvInsnBuilder.cast(value, "[Ljava/lang/Object;"));
        ib.ifCondition(AdvInsnBuilder.notEqual(AdvInsnBuilder.arrayLength(encoded), AdvInsnBuilder.constant(2)), b -> b.returnValue(value));
        ib.ifCondition(
                AdvInsnBuilder.and(
                        AdvInsnBuilder.isInstanceOf(AdvInsnBuilder.arrayAt(encoded, AdvInsnBuilder.constant(0)), "[I"),
                        AdvInsnBuilder.isInstanceOf(AdvInsnBuilder.arrayAt(encoded, AdvInsnBuilder.constant(1)), "java/lang/Integer")),
                b -> b.returnValue(decodeMaybeString(value)));
        ib.ifCondition(
                AdvInsnBuilder.and(
                        AdvInsnBuilder.not(AdvInsnBuilder.isInstanceOf(AdvInsnBuilder.arrayAt(encoded, AdvInsnBuilder.constant(0)), "java/lang/String")),
                        AdvInsnBuilder.not(AdvInsnBuilder.isInstanceOf(AdvInsnBuilder.arrayAt(encoded, AdvInsnBuilder.constant(0)), "[Ljava/lang/Object;"))),
                b -> b.returnValue(value));
        ib.set(marker, decodeMaybeString(AdvInsnBuilder.arrayAt(encoded, AdvInsnBuilder.constant(0))));
        ib.ifCondition(
                AdvInsnBuilder.isFalse(AdvInsnBuilder.callVirtual(
                        AdvInsnBuilder.constant("__BytecodeVM_TYPE__"),
                        "java/lang/String",
                        "equals",
                        "Z",
                        AdvInsnBuilder.cast(marker, "java/lang/Object"))),
                b -> b.returnValue(value));
        ib.ifCondition(
                AdvInsnBuilder.and(
                        AdvInsnBuilder.not(AdvInsnBuilder.isInstanceOf(AdvInsnBuilder.arrayAt(encoded, AdvInsnBuilder.constant(1)), "java/lang/String")),
                        AdvInsnBuilder.not(AdvInsnBuilder.isInstanceOf(AdvInsnBuilder.arrayAt(encoded, AdvInsnBuilder.constant(1)), "[Ljava/lang/Object;"))),
                b -> b.returnValue(value));

        ib.set(descriptor, decodeMaybeString(AdvInsnBuilder.arrayAt(encoded, AdvInsnBuilder.constant(1))));
        ib.set(loader, AdvInsnBuilder.callVirtual(
                AdvInsnBuilder.constant(org.objectweb.asm.Type.getObjectType(className())),
                "java/lang/Class",
                "getClassLoader",
                "java/lang/ClassLoader"));
        ib.ifCondition(
                AdvInsnBuilder.greaterThan(AdvInsnBuilder.arrayLength(AdvInsnBuilder.field(frame, frameLayout.locals)), AdvInsnBuilder.constant(0)),
                b -> {
                    b.set(receiver, AdvInsnBuilder.arrayAt(AdvInsnBuilder.field(frame, frameLayout.locals), AdvInsnBuilder.constant(0)));
                    b.ifCondition(
                            AdvInsnBuilder.notNull(receiver),
                            bb -> bb.set(loader, AdvInsnBuilder.callVirtual(
                                    AdvInsnBuilder.callVirtual(receiver, "java/lang/Object", "getClass", "java/lang/Class"),
                                    "java/lang/Class",
                                    "getClassLoader",
                                    "java/lang/ClassLoader")));
                });

        ib.ifCondition(
                AdvInsnBuilder.equal(
                        AdvInsnBuilder.callVirtual(descriptor, "java/lang/String", "length", "I"),
                        AdvInsnBuilder.constant(0)),
                b -> b.throwValue(AdvInsnBuilder.newObject(
                        "java/lang/IllegalStateException",
                        AdvInsnBuilder.constant("Invalid encoded VM type constant"))));
        ib.ifElse(
                AdvInsnBuilder.equal(
                        AdvInsnBuilder.callVirtual(descriptor, "java/lang/String", "charAt", "C", AdvInsnBuilder.constant(0)),
                        AdvInsnBuilder.constant('(')),
                b -> b.returnValue(AdvInsnBuilder.callStatic(
                        "java/lang/invoke/MethodType",
                        "fromMethodDescriptorString",
                        "java/lang/invoke/MethodType",
                        descriptor,
                        loader)),
                b -> b.returnValue(AdvInsnBuilder.callStatic(
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
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local throwable = ib.getLocal("throwable", "java/lang/Throwable", 0);
        Local handlers = ib.getLocal("handlers", "[I", 1);
        Local instructionPc = ib.getLocal("instructionPc", "I", 2);
        Local methodKey = ib.getLocal("methodKey", "I", 3);
        Local constants = ib.getLocal("constants", "[Ljava/lang/Object;", 4);
        Local index = ib.getLocal("index", "I", 5);
        Local handlerSlot = ib.getLocal("handlerSlot", "I", 6);
        Local startPc = ib.getLocal("startPc", "I", 7);
        Local endPc = ib.getLocal("endPc", "I", 8);
        Local handlerPc = ib.getLocal("handlerPc", "I", 9);
        Local typeIndex = ib.getLocal("typeIndex", "I", 10);

        ib.forLoop(
                b -> b.set(index, AdvInsnBuilder.constant(0)),
                AdvInsnBuilder.lessThan(index, AdvInsnBuilder.arrayLength(handlers)),
                b -> b.increment(index, 4),
                b -> {
                    b.set(handlerSlot, AdvInsnBuilder.divide(index, AdvInsnBuilder.constant(ProtectedVMMethod.HANDLER_SIZE)));
                    b.set(startPc, AdvInsnBuilder.arrayAt(handlers, index));
                    b.set(endPc, AdvInsnBuilder.arrayAt(handlers, AdvInsnBuilder.plus(index, AdvInsnBuilder.constant(1))));
                    b.set(handlerPc, AdvInsnBuilder.arrayAt(handlers, AdvInsnBuilder.plus(index, AdvInsnBuilder.constant(2))));
                    b.set(typeIndex, AdvInsnBuilder.arrayAt(handlers, AdvInsnBuilder.plus(index, AdvInsnBuilder.constant(3))));
                    b.ifCondition(
                            AdvInsnBuilder.notEqual(methodKey, AdvInsnBuilder.constant(0)),
                            decode -> {
                                decode.set(startPc, AdvInsnBuilder.bitXor(startPc, handlerMixCall(methodKey, handlerSlot, 0)));
                                decode.set(endPc, AdvInsnBuilder.bitXor(endPc, handlerMixCall(methodKey, handlerSlot, 1)));
                                decode.set(handlerPc, AdvInsnBuilder.bitXor(handlerPc, handlerMixCall(methodKey, handlerSlot, 2)));
                                decode.set(typeIndex, AdvInsnBuilder.bitXor(typeIndex, handlerMixCall(methodKey, handlerSlot, 3)));
                            });
                    b.ifCondition(
                            AdvInsnBuilder.and(
                                    AdvInsnBuilder.greaterOrEqual(instructionPc, startPc),
                                    AdvInsnBuilder.lessThan(instructionPc, endPc)),
                            inRange -> {
                                inRange.ifCondition(AdvInsnBuilder.lessThan(typeIndex, AdvInsnBuilder.constant(0)), catchAll -> catchAll.returnValue(handlerPc));
                                inRange.ifCondition(
                                        AdvInsnBuilder.isTrue(AdvInsnBuilder.callVirtual(
                                                AdvInsnBuilder.callStatic(
                                                        vmLayout.owner,
                                                        vmLayout.loadOwner.name(),
                                                        "java/lang/Class",
                                                        AdvInsnBuilder.callStatic(
                                                                vmLayout.owner,
                                                                vmLayout.constantString.name(),
                                                                "java/lang/String",
                                                                constants,
                                                                typeIndex)),
                                                "java/lang/Class",
                                                "isInstance",
                                                "Z",
                                                AdvInsnBuilder.cast(throwable, "java/lang/Object"))),
                                        typeMatches -> typeMatches.returnValue(handlerPc));
                            });
                });
        ib.returnValue(AdvInsnBuilder.constant(-1));
        return method;
    }

    private MethodNode genGetFieldMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.getField.name(),
                vmLayout.getField.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local owner = ib.getLocal("owner", "java/lang/String", 0);
        Local name = ib.getLocal("name", "java/lang/String", 1);
        Local descriptor = ib.getLocal("descriptor", "java/lang/String", 2);
        Local isStatic = ib.getLocal("isStatic", "Z", 3);
        Local receiver = ib.getLocal("receiver", "java/lang/Object", 4);
        ib.tryCatch(
                b -> b.returnValue(AdvInsnBuilder.callVirtual(
                        fieldHandle(owner, name, descriptor, isStatic, AdvInsnBuilder.constant(false)),
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
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local owner = ib.getLocal("owner", "java/lang/String", 0);
        Local name = ib.getLocal("name", "java/lang/String", 1);
        Local descriptor = ib.getLocal("descriptor", "java/lang/String", 2);
        Local isStatic = ib.getLocal("isStatic", "Z", 3);
        Local receiver = ib.getLocal("receiver", "java/lang/Object", 4);
        Local value = ib.getLocal("value", "java/lang/Object", 5);
        ib.tryCatch(
                b -> {
                    b.set(value, AdvInsnBuilder.callStatic(
                            vmLayout.owner,
                            vmLayout.coerceArgument.name(),
                            "java/lang/Object",
                            value,
                            AdvInsnBuilder.callStatic(vmLayout.owner, vmLayout.loadOwner.name(), "java/lang/Class", descriptor)));
                    b.directCall(AdvInsnBuilder.callVirtual(
                            fieldHandle(owner, name, descriptor, isStatic, AdvInsnBuilder.constant(true)),
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
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
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
        ib.set(cached, AdvInsnBuilder.cast(mapGet(AdvInsnBuilder.staticField(vmLayout.fieldHandles), key), "java/lang/invoke/MethodHandle"));
        ib.ifCondition(AdvInsnBuilder.notNull(cached), b -> b.returnValue(cached));

        ib.tryCatch(
                b -> {
                    b.set(ownerClass, AdvInsnBuilder.callStatic(vmLayout.owner, vmLayout.loadOwner.name(), "java/lang/Class", owner));
                    b.set(fieldType, AdvInsnBuilder.callStatic(vmLayout.owner, vmLayout.loadOwner.name(), "java/lang/Class", descriptor));
                    b.set(field, AdvInsnBuilder.callStatic(vmLayout.owner, vmLayout.findField.name(), "java/lang/reflect/Field", ownerClass, name));
                    b.directCall(AdvInsnBuilder.callVirtual(field, "java/lang/reflect/Field", "setAccessible", "V", AdvInsnBuilder.constant(true)));

                    b.ifCondition(
                            AdvInsnBuilder.notEqual(
                                    AdvInsnBuilder.callVirtual(field, "java/lang/reflect/Field", "getType", "java/lang/Class"),
                                    fieldType),
                            mismatch -> throwNoSuchField(mismatch, ownerClass, name));
                    b.ifCondition(
                            AdvInsnBuilder.notEqual(
                                    AdvInsnBuilder.callStatic(
                                            "java/lang/reflect/Modifier",
                                            "isStatic",
                                            "Z",
                                            AdvInsnBuilder.callVirtual(field, "java/lang/reflect/Field", "getModifiers", "I")),
                                    isStatic),
                            mismatch -> throwNoSuchField(mismatch, ownerClass, name));

                    b.set(handle, AdvInsnBuilder.callStatic(
                            vmLayout.owner,
                            vmLayout.adaptFieldHandle.name(),
                            "java/lang/invoke/MethodHandle",
                            field,
                            isStatic,
                            setter));
                    b.directCall(mapPut(AdvInsnBuilder.staticField(vmLayout.fieldHandles), key, handle));
                    b.returnValue(handle);
                },
                "java/lang/ReflectiveOperationException",
                "exception",
                b -> b.throwValue(AdvInsnBuilder.newObject("java/lang/IllegalStateException", b.getLocal("exception"))));
        return method;
    }

    private MethodNode genAdaptFieldHandleMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.adaptFieldHandle.name(),
                vmLayout.adaptFieldHandle.descriptor(),
                new String[]{"java/lang/IllegalAccessException"});
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local field = ib.getLocal("field", "java/lang/reflect/Field", 0);
        Local isStatic = ib.getLocal("isStatic", "Z", 1);
        Local setter = ib.getLocal("setter", "Z", 2);
        Local handle = ib.getLocal("handle", "java/lang/invoke/MethodHandle", 3);

        ib.ifElse(
                AdvInsnBuilder.isFalse(setter),
                b -> {
                    b.set(handle, AdvInsnBuilder.callVirtual(
                            AdvInsnBuilder.callStatic("java/lang/invoke/MethodHandles", "lookup", "java/lang/invoke/MethodHandles$Lookup"),
                            "java/lang/invoke/MethodHandles$Lookup",
                            "unreflectGetter",
                            "java/lang/invoke/MethodHandle",
                            field));
                    b.ifCondition(AdvInsnBuilder.isTrue(isStatic), bb -> dropLeadingObjectArgument(bb, handle));
                    b.returnValue(AdvInsnBuilder.callVirtual(
                            handle,
                            "java/lang/invoke/MethodHandle",
                            "asType",
                            "java/lang/invoke/MethodHandle",
                            getterHandleType()));
                },
                b -> {
                    b.set(handle, AdvInsnBuilder.callVirtual(
                            AdvInsnBuilder.callStatic("java/lang/invoke/MethodHandles", "lookup", "java/lang/invoke/MethodHandles$Lookup"),
                            "java/lang/invoke/MethodHandles$Lookup",
                            "unreflectSetter",
                            "java/lang/invoke/MethodHandle",
                            field));
                    b.ifCondition(AdvInsnBuilder.isTrue(isStatic), bb -> dropLeadingObjectArgument(bb, handle));
                    b.returnValue(AdvInsnBuilder.callVirtual(
                            handle,
                            "java/lang/invoke/MethodHandle",
                            "asType",
                            "java/lang/invoke/MethodHandle",
                            methodType(voidClass(), objectClass(), classArray(b, "setterParameters", objectClass()))));
                });
        return method;
    }

    private MethodNode genFindFieldMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.findField.name(),
                vmLayout.findField.descriptor(),
                new String[]{"java/lang/NoSuchFieldException"});
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local ownerClass = ib.getLocal("ownerClass", "java/lang/Class", 0);
        Local name = ib.getLocal("name", "java/lang/String", 1);
        Local interfaces = ib.getLocal("interfaces", "[Ljava/lang/Class;", 2);
        Local index = ib.getLocal("index", "I", 3);
        Local superClass = ib.getLocal("superClass", "java/lang/Class", 5);

        ib.tryCatch(
                b -> b.returnValue(AdvInsnBuilder.callVirtual(
                        ownerClass,
                        "java/lang/Class",
                        "getDeclaredField",
                        "java/lang/reflect/Field",
                        name)),
                "java/lang/NoSuchFieldException",
                "ignored",
                b -> {});

        ib.set(interfaces, AdvInsnBuilder.callVirtual(ownerClass, "java/lang/Class", "getInterfaces", "[Ljava/lang/Class;"));
        ib.forLoop(
                b -> b.set(index, AdvInsnBuilder.constant(0)),
                AdvInsnBuilder.lessThan(index, AdvInsnBuilder.arrayLength(interfaces)),
                b -> b.increment(index, 1),
                b -> b.tryCatch(
                        tryFind -> tryFind.returnValue(AdvInsnBuilder.callStatic(
                                vmLayout.owner,
                                vmLayout.findField.name(),
                                "java/lang/reflect/Field",
                                AdvInsnBuilder.arrayAt(interfaces, index),
                                name)),
                        "java/lang/NoSuchFieldException",
                        "ignored",
                        ignored -> {}));

        ib.set(superClass, AdvInsnBuilder.callVirtual(ownerClass, "java/lang/Class", "getSuperclass", "java/lang/Class"));
        ib.ifCondition(
                AdvInsnBuilder.notNull(superClass),
                b -> b.returnValue(AdvInsnBuilder.callStatic(vmLayout.owner, vmLayout.findField.name(), "java/lang/reflect/Field", superClass, name)));
        ib.throwValue(AdvInsnBuilder.newObject("java/lang/NoSuchFieldException", name));
        return method;
    }

    private MethodNode genFindMethodMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.findMethod.name(),
                vmLayout.findMethod.descriptor(),
                new String[]{"java/lang/NoSuchMethodException"});
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local ownerClass = ib.getLocal("ownerClass", "java/lang/Class", 0);
        Local name = ib.getLocal("name", "java/lang/String", 1);
        Local parameterTypes = ib.getLocal("parameterTypes", "[Ljava/lang/Class;", 2);
        Local interfaces = ib.getLocal("interfaces", "[Ljava/lang/Class;", 3);
        Local index = ib.getLocal("index", "I", 4);
        Local superClass = ib.getLocal("superClass", "java/lang/Class", 6);

        ib.tryCatch(
                b -> b.returnValue(AdvInsnBuilder.callVirtual(
                        ownerClass,
                        "java/lang/Class",
                        "getDeclaredMethod",
                        "java/lang/reflect/Method",
                        name,
                        parameterTypes)),
                "java/lang/NoSuchMethodException",
                "ignored",
                b -> {});

        ib.set(interfaces, AdvInsnBuilder.callVirtual(ownerClass, "java/lang/Class", "getInterfaces", "[Ljava/lang/Class;"));
        ib.forLoop(
                b -> b.set(index, AdvInsnBuilder.constant(0)),
                AdvInsnBuilder.lessThan(index, AdvInsnBuilder.arrayLength(interfaces)),
                b -> b.increment(index, 1),
                b -> b.tryCatch(
                        tryFind -> tryFind.returnValue(AdvInsnBuilder.callStatic(
                                vmLayout.owner,
                                vmLayout.findMethod.name(),
                                "java/lang/reflect/Method",
                                AdvInsnBuilder.arrayAt(interfaces, index),
                                name,
                                parameterTypes)),
                        "java/lang/NoSuchMethodException",
                        "ignored",
                        ignored -> {}));

        ib.set(superClass, AdvInsnBuilder.callVirtual(ownerClass, "java/lang/Class", "getSuperclass", "java/lang/Class"));
        ib.ifCondition(
                AdvInsnBuilder.notNull(superClass),
                b -> b.returnValue(AdvInsnBuilder.callStatic(vmLayout.owner, vmLayout.findMethod.name(), "java/lang/reflect/Method", superClass, name, parameterTypes)));
        ib.throwValue(AdvInsnBuilder.newObject(
                "java/lang/NoSuchMethodException",
                stringConcat(
                        AdvInsnBuilder.callVirtual(ownerClass, "java/lang/Class", "getName", "java/lang/String"),
                        AdvInsnBuilder.constant("."),
                        name)));
        return method;
    }

    private MethodNode genInvokeMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                "invoke",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/invoke/MethodType;ZLjava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
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

        ib.ifCondition(
                AdvInsnBuilder.and(
                        AdvInsnBuilder.isFalse(isStatic),
                        AdvInsnBuilder.and(
                                AdvInsnBuilder.isTrue(AdvInsnBuilder.callVirtual(
                                        name,
                                        "java/lang/String",
                                        "equals",
                                        "Z",
                                        AdvInsnBuilder.cast(AdvInsnBuilder.constant("clone"), "java/lang/Object"))),
                                AdvInsnBuilder.and(
                                        AdvInsnBuilder.equal(
                                                AdvInsnBuilder.callVirtual(methodType, "java/lang/invoke/MethodType", "parameterCount", "I"),
                                                AdvInsnBuilder.constant(0)),
                                        AdvInsnBuilder.isTrue(AdvInsnBuilder.callVirtual(
                                                AdvInsnBuilder.callVirtual(receiver, "java/lang/Object", "getClass", "java/lang/Class"),
                                                "java/lang/Class",
                                                "isArray",
                                                "Z"))))),
                b -> b.returnValue(AdvInsnBuilder.callStatic(vmLayout.owner, vmLayout.cloneArray.name(), "java/lang/Object", receiver)));

        ib.set(key, methodHandleKey(owner, name, methodType, isStatic));
        ib.set(target, AdvInsnBuilder.cast(mapGet(AdvInsnBuilder.staticField(vmLayout.methodHandles), key), "java/lang/invoke/MethodHandle"));

        ib.ifCondition(
                AdvInsnBuilder.isNull(target),
                b -> {
                    b.set(ownerClass, AdvInsnBuilder.nullValue("java/lang/Class"));
                    b.set(exception, AdvInsnBuilder.nullValue("java/lang/Throwable"));
                    b.tryCatch(
                            tryReflect -> {
                                tryReflect.set(ownerClass, AdvInsnBuilder.callStatic(vmLayout.owner, vmLayout.loadOwner.name(), "java/lang/Class", owner));
                                tryReflect.set(reflectedMethod, AdvInsnBuilder.callStatic(
                                        vmLayout.owner,
                                        vmLayout.findMethod.name(),
                                        "java/lang/reflect/Method",
                                        ownerClass,
                                        name,
                                        AdvInsnBuilder.callVirtual(methodType, "java/lang/invoke/MethodType", "parameterArray", "[Ljava/lang/Class;")));
                                cacheAdaptedMethodHandle(tryReflect, reflectedMethod, ownerClass, name, methodType, isStatic, key, target);
                            },
                            "java/lang/Throwable",
                            "caught",
                            caught -> caught.set(exception, caught.getLocal("caught")));

                    b.ifCondition(
                            AdvInsnBuilder.and(AdvInsnBuilder.isNull(target), AdvInsnBuilder.isInstanceOf(exception, "java/lang/NoSuchMethodException")),
                            publicLookup -> publicLookup.tryCatch(
                                    tryPublic -> {
                                        tryPublic.set(reflectedMethod, AdvInsnBuilder.callVirtual(
                                                ownerClass,
                                                "java/lang/Class",
                                                "getMethod",
                                                "java/lang/reflect/Method",
                                                name,
                                                AdvInsnBuilder.callVirtual(methodType, "java/lang/invoke/MethodType", "parameterArray", "[Ljava/lang/Class;")));
                                        cacheAdaptedMethodHandle(tryPublic, reflectedMethod, ownerClass, name, methodType, isStatic, key, target);
                                    },
                                    "java/lang/ReflectiveOperationException",
                                    "caught",
                                    caught -> caught.set(exception, caught.getLocal("caught"))));

                    b.ifCondition(
                            AdvInsnBuilder.isNull(target),
                            miss -> miss.ifElse(
                                    AdvInsnBuilder.isInstanceOf(exception, "java/lang/reflect/InaccessibleObjectException"),
                                    direct -> {
                                        direct.set(target, AdvInsnBuilder.callStatic(
                                                vmLayout.owner,
                                                vmLayout.adaptDirectMethodHandle.name(),
                                                "java/lang/invoke/MethodHandle",
                                                ownerClass,
                                                name,
                                                methodType,
                                                isStatic));
                                        direct.directCall(mapPut(AdvInsnBuilder.staticField(vmLayout.methodHandles), key, target));
                                    },
                                    failure -> failure.throwValue(AdvInsnBuilder.newObject("java/lang/IllegalStateException", exception))));
                });

        coerceArguments(ib, arguments, methodType);
        ib.tryCatch(
                b -> b.returnValue(AdvInsnBuilder.callVirtual(
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

    private MethodNode genConstructMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                vmLayout.construct.name(),
                vmLayout.construct.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local owner = ib.getLocal("owner", "java/lang/String", 0);
        Local methodType = ib.getLocal("methodType", "java/lang/invoke/MethodType", 1);
        Local arguments = ib.getLocal("arguments", "[Ljava/lang/Object;", 2);
        Local key = ib.getLocal("key", "java/lang/String", 3);
        Local target = ib.getLocal("target", "java/lang/invoke/MethodHandle", 4);
        Local ownerClass = ib.getLocal("ownerClass", "java/lang/Class", 5);
        Local constructor = ib.getLocal("constructor", "java/lang/reflect/Constructor", 6);

        ib.set(key, stringConcat(AdvInsnBuilder.constant("<init>:"), owner, methodType));
        ib.set(target, AdvInsnBuilder.cast(mapGet(AdvInsnBuilder.staticField(vmLayout.methodHandles), key), "java/lang/invoke/MethodHandle"));

        ib.ifCondition(
                AdvInsnBuilder.isNull(target),
                b -> b.tryCatch(
                        resolve -> {
                            resolve.set(ownerClass, AdvInsnBuilder.callStatic(vmLayout.owner, vmLayout.loadOwner.name(), "java/lang/Class", owner));
                            resolve.set(constructor, AdvInsnBuilder.callVirtual(
                                    ownerClass,
                                    "java/lang/Class",
                                    "getDeclaredConstructor",
                                    "java/lang/reflect/Constructor",
                                    AdvInsnBuilder.callVirtual(methodType, "java/lang/invoke/MethodType", "parameterArray", "[Ljava/lang/Class;")));
                            resolve.directCall(AdvInsnBuilder.callVirtual(constructor, "java/lang/reflect/Constructor", "setAccessible", "V", AdvInsnBuilder.constant(true)));
                            resolve.set(target, AdvInsnBuilder.callStatic(
                                    vmLayout.owner,
                                    vmLayout.adaptConstructorHandle.name(),
                                    "java/lang/invoke/MethodHandle",
                                    constructor,
                                    AdvInsnBuilder.callVirtual(methodType, "java/lang/invoke/MethodType", "parameterCount", "I")));
                            resolve.directCall(mapPut(AdvInsnBuilder.staticField(vmLayout.methodHandles), key, target));
                        },
                        "java/lang/Throwable",
                        "throwable",
                        caught -> caught.throwValue(rethrow(caught.getLocal("throwable")))));

        coerceArguments(ib, arguments, methodType);
        ib.tryCatch(
                b -> b.returnValue(AdvInsnBuilder.callVirtual(
                        target,
                        "java/lang/invoke/MethodHandle",
                        "invokeExact",
                        "java/lang/Object",
                        AdvInsnBuilder.nullValue("java/lang/Object"),
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
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local methodRef = ib.getLocal("method", "java/lang/reflect/Method", 0);
        Local isStatic = ib.getLocal("isStatic", "Z", 1);
        Local parameterCount = ib.getLocal("parameterCount", "I", 2);
        Local handle = ib.getLocal("handle", "java/lang/invoke/MethodHandle", 3);

        ib.set(handle, AdvInsnBuilder.callVirtual(
                AdvInsnBuilder.callVirtual(
                        AdvInsnBuilder.callVirtual(
                                AdvInsnBuilder.callStatic("java/lang/invoke/MethodHandles", "lookup", "java/lang/invoke/MethodHandles$Lookup"),
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
        ib.ifCondition(AdvInsnBuilder.isTrue(isStatic), b -> dropLeadingObjectArgument(b, handle));
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
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local ownerClass = ib.getLocal("ownerClass", "java/lang/Class", 0);
        Local name = ib.getLocal("name", "java/lang/String", 1);
        Local methodType = ib.getLocal("methodType", "java/lang/invoke/MethodType", 2);
        Local isStatic = ib.getLocal("isStatic", "Z", 3);
        Local lookup = ib.getLocal("lookup", "java/lang/invoke/MethodHandles$Lookup", 4);
        Local handle = ib.getLocal("handle", "java/lang/invoke/MethodHandle", 5);

        ib.set(lookup, AdvInsnBuilder.callStatic(
                "java/lang/invoke/MethodHandles",
                "privateLookupIn",
                "java/lang/invoke/MethodHandles$Lookup",
                ownerClass,
                AdvInsnBuilder.callStatic("java/lang/invoke/MethodHandles", "lookup", "java/lang/invoke/MethodHandles$Lookup")));
        ib.ifElse(
                AdvInsnBuilder.isTrue(isStatic),
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
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local constructor = ib.getLocal("constructor", "java/lang/reflect/Constructor", 0);
        Local parameterCount = ib.getLocal("parameterCount", "I", 1);
        Local handle = ib.getLocal("handle", "java/lang/invoke/MethodHandle", 2);

        ib.set(handle, AdvInsnBuilder.callVirtual(
                AdvInsnBuilder.callVirtual(
                        AdvInsnBuilder.callVirtual(
                                AdvInsnBuilder.callStatic("java/lang/invoke/MethodHandles", "lookup", "java/lang/invoke/MethodHandles$Lookup"),
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
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local value = ib.getLocal("value", "java/lang/Object", 0);
        Local targetType = ib.getLocal("targetType", "java/lang/Class", 1);

        ib.ifCondition(AdvInsnBuilder.equal(targetType, primitiveType("java/lang/Boolean")), b -> b.returnValue(AdvInsnBuilder.callStatic(
                "java/lang/Boolean", "valueOf", "java/lang/Boolean", AdvInsnBuilder.unbox(value, "Z"))));
        ib.ifCondition(AdvInsnBuilder.equal(targetType, primitiveType("java/lang/Character")), b -> b.returnValue(AdvInsnBuilder.callStatic(
                "java/lang/Character", "valueOf", "java/lang/Character", AdvInsnBuilder.cast(AdvInsnBuilder.unbox(value, "I"), "C"))));
        ib.ifCondition(AdvInsnBuilder.equal(targetType, primitiveType("java/lang/Byte")), b -> b.returnValue(AdvInsnBuilder.callStatic(
                "java/lang/Byte", "valueOf", "java/lang/Byte", AdvInsnBuilder.cast(AdvInsnBuilder.unbox(value, "I"), "B"))));
        ib.ifCondition(AdvInsnBuilder.equal(targetType, primitiveType("java/lang/Short")), b -> b.returnValue(AdvInsnBuilder.callStatic(
                "java/lang/Short", "valueOf", "java/lang/Short", AdvInsnBuilder.cast(AdvInsnBuilder.unbox(value, "I"), "S"))));
        ib.ifCondition(AdvInsnBuilder.equal(targetType, primitiveType("java/lang/Integer")), b -> b.returnValue(AdvInsnBuilder.callStatic(
                "java/lang/Integer", "valueOf", "java/lang/Integer", AdvInsnBuilder.unbox(value, "I"))));
        ib.ifCondition(AdvInsnBuilder.equal(targetType, primitiveType("java/lang/Long")), b -> b.returnValue(AdvInsnBuilder.callStatic(
                "java/lang/Long", "valueOf", "java/lang/Long", AdvInsnBuilder.callVirtual(AdvInsnBuilder.cast(value, "java/lang/Number"), "java/lang/Number", "longValue", "J"))));
        ib.ifCondition(AdvInsnBuilder.equal(targetType, primitiveType("java/lang/Float")), b -> b.returnValue(AdvInsnBuilder.callStatic(
                "java/lang/Float", "valueOf", "java/lang/Float", AdvInsnBuilder.callVirtual(AdvInsnBuilder.cast(value, "java/lang/Number"), "java/lang/Number", "floatValue", "F"))));
        ib.ifCondition(AdvInsnBuilder.equal(targetType, primitiveType("java/lang/Double")), b -> b.returnValue(AdvInsnBuilder.callStatic(
                "java/lang/Double", "valueOf", "java/lang/Double", AdvInsnBuilder.callVirtual(AdvInsnBuilder.cast(value, "java/lang/Number"), "java/lang/Number", "doubleValue", "D"))));
        ib.returnValue(value);
        return method;
    }

    private MethodNode genCloneArrayMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                "cloneArray",
                "(Ljava/lang/Object;)Ljava/lang/Object;");
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local array = ib.getLocal("array", "java/lang/Object", 0);
        Local length = ib.getLocal("length", "I", 1);
        Local clone = ib.getLocal("clone", "java/lang/Object", 2);

        ib.set(length, AdvInsnBuilder.callStatic("java/lang/reflect/Array", "getLength", "I", array));
        ib.set(clone, AdvInsnBuilder.callStatic(
                "java/lang/reflect/Array",
                "newInstance",
                "java/lang/Object",
                AdvInsnBuilder.callVirtual(
                        AdvInsnBuilder.callVirtual(array, "java/lang/Object", "getClass", "java/lang/Class"),
                        "java/lang/Class",
                        "getComponentType",
                        "java/lang/Class"),
                length));
        ib.directCall(AdvInsnBuilder.callStatic(
                "java/lang/System",
                "arraycopy",
                "V",
                array,
                AdvInsnBuilder.constant(0),
                clone,
                AdvInsnBuilder.constant(0),
                length));
        ib.returnValue(clone);
        return method;
    }

    private MethodNode genLoadOwnerMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                "loadOwner",
                "(Ljava/lang/String;)Ljava/lang/Class;");
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local owner = ib.getLocal("owner", "java/lang/String", 0);
        ib.returnValue(AdvInsnBuilder.callStatic(
                vmLayout.owner,
                vmLayout.loadOwnerWithLoader.name(),
                "java/lang/Class",
                owner,
                AdvInsnBuilder.callVirtual(
                        AdvInsnBuilder.constant(org.objectweb.asm.Type.getObjectType(className())),
                        "java/lang/Class",
                        "getClassLoader",
                        "java/lang/ClassLoader")));
        return method;
    }

    private MethodNode genLoadOwnerWithLoaderMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                "loadOwner",
                "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/Class;");
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local owner = ib.getLocal("owner", "java/lang/String", 0);
        Local loader = ib.getLocal("loader", "java/lang/ClassLoader", 1);

        ib.ifCondition(
                AdvInsnBuilder.equal(AdvInsnBuilder.callVirtual(owner, "java/lang/String", "length", "I"), AdvInsnBuilder.constant(1)),
                b -> b.switchLookup(
                        AdvInsnBuilder.callVirtual(owner, "java/lang/String", "charAt", "C", AdvInsnBuilder.constant(0)),
                        null,
                        AdvInsnBuilder.switchCase('Z', bb -> bb.returnValue(primitiveType("java/lang/Boolean"))),
                        AdvInsnBuilder.switchCase('C', bb -> bb.returnValue(primitiveType("java/lang/Character"))),
                        AdvInsnBuilder.switchCase('B', bb -> bb.returnValue(primitiveType("java/lang/Byte"))),
                        AdvInsnBuilder.switchCase('S', bb -> bb.returnValue(primitiveType("java/lang/Short"))),
                        AdvInsnBuilder.switchCase('I', bb -> bb.returnValue(primitiveType("java/lang/Integer"))),
                        AdvInsnBuilder.switchCase('F', bb -> bb.returnValue(primitiveType("java/lang/Float"))),
                        AdvInsnBuilder.switchCase('J', bb -> bb.returnValue(primitiveType("java/lang/Long"))),
                        AdvInsnBuilder.switchCase('D', bb -> bb.returnValue(primitiveType("java/lang/Double"))),
                        AdvInsnBuilder.switchCase('V', bb -> bb.returnValue(primitiveType("java/lang/Void")))));

        ib.ifCondition(
                AdvInsnBuilder.and(
                        AdvInsnBuilder.isTrue(AdvInsnBuilder.callVirtual(owner, "java/lang/String", "startsWith", "Z", AdvInsnBuilder.constant("L"))),
                        AdvInsnBuilder.isTrue(AdvInsnBuilder.callVirtual(owner, "java/lang/String", "endsWith", "Z", AdvInsnBuilder.constant(";")))),
                b -> b.set(owner, AdvInsnBuilder.callVirtual(
                        owner,
                        "java/lang/String",
                        "substring",
                        "java/lang/String",
                        AdvInsnBuilder.constant(1),
                        AdvInsnBuilder.minus(
                                AdvInsnBuilder.callVirtual(owner, "java/lang/String", "length", "I"),
                                AdvInsnBuilder.constant(1)))));

        ib.tryCatch(
                b -> b.returnValue(AdvInsnBuilder.callStatic(
                        "java/lang/Class",
                        "forName",
                        "java/lang/Class",
                        AdvInsnBuilder.callVirtual(owner, "java/lang/String", "replace", "java/lang/String", AdvInsnBuilder.constant('/'), AdvInsnBuilder.constant('.')),
                        AdvInsnBuilder.constant(false),
                        loader)),
                "java/lang/ClassNotFoundException",
                "exception",
                b -> b.throwValue(AdvInsnBuilder.newObject("java/lang/IllegalStateException", b.getLocal("exception"))));
        return method;
    }

    private MethodNode genRethrowMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                "rethrow",
                "(Ljava/lang/Throwable;)Ljava/lang/RuntimeException;");
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        ib.throwValue(ib.getLocal("throwable", "java/lang/Throwable", 0));
        return method;
    }

    private MethodNode genMonitorForMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.SYNCHRONIZED},
                "monitorFor",
                "(Ljava/lang/Object;)Ljava/util/concurrent/locks/ReentrantLock;");
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local monitor = ib.getLocal("monitor", "java/lang/Object", 0);
        Local lock = ib.getLocal("lock", "java/util/concurrent/locks/ReentrantLock", 1);

        ib.ifCondition(AdvInsnBuilder.isNull(monitor), b -> b.throwValue(AdvInsnBuilder.newObject("java/lang/NullPointerException")));
        ib.set(lock, AdvInsnBuilder.cast(mapGet(AdvInsnBuilder.staticField(vmLayout.monitors), monitor), "java/util/concurrent/locks/ReentrantLock"));
        ib.ifCondition(
                AdvInsnBuilder.isNull(lock),
                b -> {
                    b.set(lock, AdvInsnBuilder.newObject("java/util/concurrent/locks/ReentrantLock"));
                    b.directCall(mapPut(AdvInsnBuilder.staticField(vmLayout.monitors), monitor, lock));
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
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local monitor = ib.getLocal("monitor", "java/lang/Object", 0);
        ib.directCall(AdvInsnBuilder.callVirtual(
                AdvInsnBuilder.callStatic(vmLayout.owner, vmLayout.monitorFor.name(), "java/util/concurrent/locks/ReentrantLock", monitor),
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
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local monitor = ib.getLocal("monitor", "java/lang/Object", 0);
        ib.directCall(AdvInsnBuilder.callVirtual(
                AdvInsnBuilder.callStatic(vmLayout.owner, vmLayout.monitorFor.name(), "java/util/concurrent/locks/ReentrantLock", monitor),
                "java/util/concurrent/locks/ReentrantLock",
                "unlock",
                "V"));
        ib.returnVoid();
        return method;
    }

    private MethodNode genClInitMethod(List<CodePoolGenerator> codePoolGenerators)
    {
        MethodNode initMethod = MethodUtils.newMethodNode(new Acc[]{Acc.STATIC}, "<clinit>", "()V");
        AdvInsnBuilder ib = new AdvInsnBuilder(initMethod);

        Local codePools = ib.var("codePools", "[Ljava/lang/Object;");
        ib.set(codePools, AdvInsnBuilder.newArray("java/lang/Object", AdvInsnBuilder.constant(codePoolGenerators.size())));
        for (int i = 0; i < codePoolGenerators.size(); i++)
        {
            ib.setArray(
                    codePools,
                    AdvInsnBuilder.constant(i),
                    AdvInsnBuilder.staticField(codePoolGenerators.get(i).layout.instance));
        }
        ib.set(AdvInsnBuilder.staticField(vmLayout.codePools), AdvInsnBuilder.callStatic(
                "java/util/Arrays",
                "asList",
                "java/util/List",
                codePools));

        ib.set(AdvInsnBuilder.staticField(vmLayout.fieldHandles), AdvInsnBuilder.newObject("java/util/concurrent/ConcurrentHashMap"));

        ib.set(AdvInsnBuilder.staticField(vmLayout.methodHandles), AdvInsnBuilder.newObject("java/util/concurrent/ConcurrentHashMap"));

        ib.set(AdvInsnBuilder.staticField(vmLayout.methodTypes), AdvInsnBuilder.newObject("java/util/concurrent/ConcurrentHashMap"));

        ib.set(AdvInsnBuilder.staticField(vmLayout.monitors), AdvInsnBuilder.callStatic(
                "java/util/Collections",
                "synchronizedMap",
                "java/util/Map",
                AdvInsnBuilder.cast(AdvInsnBuilder.newObject("java/util/WeakHashMap"), "java/util/Map")));
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
        return AdvInsnBuilder.callInterface(
                map,
                "java/util/Map",
                "get",
                "java/lang/Object",
                AdvInsnBuilder.cast(key, "java/lang/Object"));
    }

    private static Expr mapPut(Expr map, Expr key, Expr value)
    {
        return AdvInsnBuilder.callInterface(
                map,
                "java/util/Map",
                "put",
                "java/lang/Object",
                AdvInsnBuilder.cast(key, "java/lang/Object"),
                AdvInsnBuilder.cast(value, "java/lang/Object"));
    }

    private Expr callProgramArray(Expr program, String methodName)
    {
        return AdvInsnBuilder.callVirtual(program, programLayout.owner, methodName, "[I");
    }

    private Expr callProgramInt(Expr program, String methodName)
    {
        return AdvInsnBuilder.callVirtual(program, programLayout.owner, methodName, "I");
    }

    private Expr layoutValue(Expr program, Expr instructionIndex, Expr field)
    {
        return AdvInsnBuilder.callStatic(
                className(),
                "layoutValue",
                "I",
                program,
                instructionIndex,
                field);
    }

    private Expr mixCall(Expr key, Expr a, Expr b, Expr c)
    {
        return AdvInsnBuilder.callStatic(className(), "mix", "I", key, a, b, c);
    }

    private Expr handlerMixCall(Expr methodKey, Expr handlerSlot, int field)
    {
        return mixCall(
                methodKey,
                handlerSlot,
                AdvInsnBuilder.constant(field),
                AdvInsnBuilder.constant(ProtectedVMMethod.SALT_HANDLER));
    }

    private Expr dispatchKeyExpr(Expr opcode)
    {
        return mixCall(
                AdvInsnBuilder.constant(dispatchSalt),
                opcode,
                AdvInsnBuilder.constant(0x3d7a91c5),
                AdvInsnBuilder.constant(0));
    }

    private int dispatchKey(int opcode)
    {
        return ProtectedVMMethod.mix(dispatchSalt, opcode, 0x3d7a91c5, 0);
    }

    private Expr decodeMaybeString(Expr value)
    {
        return AdvInsnBuilder.callStatic(className(), "decodeMaybeString", "java/lang/String", value);
    }

    private static void mixRound(AdvInsnBuilder ib, Local x, Expr value, int salt)
    {
        ib.set(x, AdvInsnBuilder.bitXor(
                x,
                add(
                        value,
                        AdvInsnBuilder.constant(salt),
                        AdvInsnBuilder.shiftLeft(x, AdvInsnBuilder.constant(6)),
                        AdvInsnBuilder.unsignedShiftRight(x, AdvInsnBuilder.constant(2)))));
    }

    private static Expr add(Expr first, Expr... rest)
    {
        Expr result = first;
        for (Expr value : rest)
        {
            result = AdvInsnBuilder.plus(result, value);
        }
        return result;
    }

    private Expr fieldHandle(Expr owner, Expr name, Expr descriptor, Expr isStatic, Expr setter)
    {
        return AdvInsnBuilder.callStatic(
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
                AdvInsnBuilder.constant("."),
                name,
                AdvInsnBuilder.constant(":"),
                descriptor,
                AdvInsnBuilder.constant(":"),
                isStatic,
                AdvInsnBuilder.constant(":"),
                setter);
    }

    private static Expr methodHandleKey(Expr owner, Expr name, Expr methodType, Expr isStatic)
    {
        return stringConcat(
                owner,
                AdvInsnBuilder.constant("."),
                name,
                methodType,
                AdvInsnBuilder.constant(":"),
                isStatic);
    }

    private Expr rethrow(Expr throwable)
    {
        return AdvInsnBuilder.callStatic(
                vmLayout.owner,
                vmLayout.rethrow.name(),
                "java/lang/RuntimeException",
                throwable);
    }

    private static void throwNoSuchField(AdvInsnBuilder ib, Expr ownerClass, Expr fieldName)
    {
        ib.throwValue(AdvInsnBuilder.newObject(
                "java/lang/NoSuchFieldException",
                stringConcat(
                        AdvInsnBuilder.callVirtual(ownerClass, "java/lang/Class", "getName", "java/lang/String"),
                        AdvInsnBuilder.constant("."),
                        fieldName)));
    }

    private static void throwNoSuchMethod(AdvInsnBuilder ib, Expr ownerClass, Expr methodName, Expr methodType)
    {
        ib.throwValue(AdvInsnBuilder.newObject(
                "java/lang/NoSuchMethodException",
                stringConcat(
                        AdvInsnBuilder.callVirtual(ownerClass, "java/lang/Class", "getName", "java/lang/String"),
                        AdvInsnBuilder.constant("."),
                        methodName,
                        methodType)));
    }

    private static void throwExceptionWithInt(AdvInsnBuilder ib, String exceptionType, String prefix, Expr value)
    {
        ib.throwValue(AdvInsnBuilder.newObject(
                exceptionType,
                stringConcat(AdvInsnBuilder.constant(prefix), value)));
    }

    private void cacheAdaptedMethodHandle(
            AdvInsnBuilder ib,
            Local reflectedMethod,
            Local ownerClass,
            Local name,
            Local methodType,
            Local isStatic,
            Local key,
            Local target)
    {
        ib.directCall(AdvInsnBuilder.callVirtual(reflectedMethod, "java/lang/reflect/Method", "setAccessible", "V", AdvInsnBuilder.constant(true)));
        ib.ifCondition(
                AdvInsnBuilder.notEqual(
                        AdvInsnBuilder.callVirtual(reflectedMethod, "java/lang/reflect/Method", "getReturnType", "java/lang/Class"),
                        AdvInsnBuilder.callVirtual(methodType, "java/lang/invoke/MethodType", "returnType", "java/lang/Class")),
                b -> throwNoSuchMethod(b, ownerClass, name, methodType));
        ib.ifCondition(
                AdvInsnBuilder.notEqual(
                        AdvInsnBuilder.callStatic(
                                "java/lang/reflect/Modifier",
                                "isStatic",
                                "Z",
                                AdvInsnBuilder.callVirtual(reflectedMethod, "java/lang/reflect/Method", "getModifiers", "I")),
                        isStatic),
                b -> throwNoSuchMethod(b, ownerClass, name, methodType));
        ib.set(target, AdvInsnBuilder.callStatic(
                vmLayout.owner,
                vmLayout.adaptMethodHandle.name(),
                "java/lang/invoke/MethodHandle",
                reflectedMethod,
                isStatic,
                AdvInsnBuilder.callVirtual(methodType, "java/lang/invoke/MethodType", "parameterCount", "I")));
        ib.directCall(mapPut(AdvInsnBuilder.staticField(vmLayout.methodHandles), key, target));
    }

    private static Expr directHandle(Expr lookup, Expr ownerClass, Expr name, Expr methodType, boolean staticMethod)
    {
        return AdvInsnBuilder.callVirtual(
                AdvInsnBuilder.callVirtual(
                        AdvInsnBuilder.callVirtual(
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
                AdvInsnBuilder.callVirtual(methodType, "java/lang/invoke/MethodType", "parameterCount", "I"));
    }

    private static Expr asInvokerHandle(Expr handle, Expr parameterTypes)
    {
        return AdvInsnBuilder.callVirtual(
                handle,
                "java/lang/invoke/MethodHandle",
                "asType",
                "java/lang/invoke/MethodHandle",
                methodType(objectClass(), objectClass(), parameterTypes));
    }

    private void coerceArguments(AdvInsnBuilder ib, Local arguments, Local methodType)
    {
        Local index = ib.var("argumentIndex", "I");
        ib.forLoop(
                b -> b.set(index, AdvInsnBuilder.constant(0)),
                AdvInsnBuilder.lessThan(index, AdvInsnBuilder.arrayLength(arguments)),
                b -> b.increment(index, 1),
                b -> b.setArray(
                        arguments,
                        index,
                        AdvInsnBuilder.callStatic(
                                vmLayout.owner,
                                vmLayout.coerceArgument.name(),
                                "java/lang/Object",
                                AdvInsnBuilder.arrayAt(arguments, index),
                                AdvInsnBuilder.callVirtual(methodType, "java/lang/invoke/MethodType", "parameterType", "java/lang/Class", index))));
    }

    private static Expr stringConcat(Expr first, Expr... rest)
    {
        Expr builder = AdvInsnBuilder.newObject("java/lang/StringBuilder", first);
        for (Expr value : rest)
        {
            if ((value.type().getSort() == org.objectweb.asm.Type.OBJECT || value.type().getSort() == org.objectweb.asm.Type.ARRAY)
                && !value.type().equals(org.objectweb.asm.Type.getType(String.class)))
            {
                value = AdvInsnBuilder.cast(value, "java/lang/Object");
            }
            builder = AdvInsnBuilder.callVirtual(
                    builder,
                    "java/lang/StringBuilder",
                    "append",
                    "java/lang/StringBuilder",
                    value);
        }
        return AdvInsnBuilder.callVirtual(
                builder,
                "java/lang/StringBuilder",
                "toString",
                "java/lang/String");
    }

    private static Expr objectClass()
    {
        return AdvInsnBuilder.constant(org.objectweb.asm.Type.getType("Ljava/lang/Object;"));
    }

    private static Expr objectArrayClass()
    {
        return AdvInsnBuilder.constant(org.objectweb.asm.Type.getType("[Ljava/lang/Object;"));
    }

    private static Expr voidClass()
    {
        return AdvInsnBuilder.staticField("java/lang/Void", "TYPE", "java/lang/Class");
    }

    private static Expr primitiveType(String wrapper)
    {
        return AdvInsnBuilder.staticField(wrapper, "TYPE", "java/lang/Class");
    }

    private static Local classArray(AdvInsnBuilder ib, String name, Expr... values)
    {
        Local array = ib.var(name, "[Ljava/lang/Class;");
        ib.set(array, AdvInsnBuilder.newArray("java/lang/Class", AdvInsnBuilder.constant(values.length)));
        for (int i = 0; i < values.length; i++)
        {
            ib.setArray(array, AdvInsnBuilder.constant(i), values[i]);
        }
        return array;
    }

    private static Expr getterHandleType()
    {
        return AdvInsnBuilder.callStatic(
                "java/lang/invoke/MethodType",
                "methodType",
                "java/lang/invoke/MethodType",
                objectClass(),
                objectClass());
    }

    private static Expr methodType(Expr returnType, Expr leadingParameter, Expr trailingParameters)
    {
        return AdvInsnBuilder.callStatic(
                "java/lang/invoke/MethodType",
                "methodType",
                "java/lang/invoke/MethodType",
                returnType,
                leadingParameter,
                trailingParameters);
    }

    private static void dropLeadingObjectArgument(AdvInsnBuilder ib, Local handle)
    {
        Local leadingObject = classArray(ib, "leadingObject", objectClass());
        ib.set(handle, AdvInsnBuilder.callStatic(
                "java/lang/invoke/MethodHandles",
                "dropArguments",
                "java/lang/invoke/MethodHandle",
                handle,
                AdvInsnBuilder.constant(0),
                leadingObject));
    }

    private static void validateBranches()
    {
        for (Opcs opcode : Opcs.values())
        {
            if(opcode == Opcs.INVOKEDYNAMIC)
            {
                continue;
            }
            if (!branches.containsKey(opcode))
            {
                throw new IllegalStateException("No InterpretBranch for " + opcode);
            }
        }
    }
}
