package nhcm.bytecodevm.Generator.Virtualization;

import lombok.Getter;
import nhcm.bytecodevm.Enums.Acc;
import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Generator.Abstract.ClassObj;
import nhcm.bytecodevm.Generator.GlobalTool.MethodFrameGenerator;
import nhcm.bytecodevm.Generator.GlobalTool.VMCodePoolGenerator;
import nhcm.bytecodevm.Generator.GlobalTool.VMProgramGenerator;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Conversion.CompareBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Conversion.ConvertBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Control.FlowBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Control.GotoBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Control.SwitchBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Local.StoreLocalBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Stack.DuplicateBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Stack.PopBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Stack.SwapBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Constant.*;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Math.*;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Local.IncrementBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Local.LoadLocalBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Control.ReturnBranch;
import nhcm.bytecodevm.Tools.OpcMutator;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
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
    private static final Map<Opcs, InterpretBranch> branches = new EnumMap<>(Opcs.class);

    static
    {
        registerBranches();
        // validateBranches();
    }

    private static void registerBranches()
    {
        List<InterpretBranch> array = List.of();
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
                new ReturnBranch(),
                new SwitchBranch()
        );
        List<InterpretBranch> conversion = List.of(
                new CompareBranch(),
                new ConvertBranch()
        );
        List<InterpretBranch> field = List.of();
        List<InterpretBranch> invoke = List.of();
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
        List<InterpretBranch> object = List.of();
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

    public VMGenerator(String className, List<CodePoolGenerator> codePoolGenerators, OpcMutator opcMutator, MethodFrameGenerator methodFrameGenerator, VMProgramGenerator vmProgramGenerator, VMCodePoolGenerator vmCodePoolGenerator)
    {
        super(className);
        this.codePoolGenerators = List.copyOf(codePoolGenerators);
        this.opcMutator = opcMutator;
        this.methodFrameGenerator = methodFrameGenerator;
        this.vmProgramGenerator = vmProgramGenerator;
        this.vmCodePoolGenerator = vmCodePoolGenerator;
        ClassNode cn = ClassUtils.newClassNode(new Acc[]{Acc.PUBLIC, Acc.FINAL}, className);
        InsnUtils.addPrivateInit(cn);
        this.classNode = cn;
        String vmCodePoolSign = vmCodePoolGenerator.descriptor();
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, "CODE_POOLS", "Ljava/util/List;", "Ljava/util/List<" + vmCodePoolSign + ">;"));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, "FIELD_HANDLES", "Ljava/util/Map;", "Ljava/util/Map<Ljava/lang/String;Ljava/lang/invoke/MethodHandle;>;"));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, "METHOD_HANDLES", "Ljava/util/Map;", "Ljava/util/Map<Ljava/lang/String;Ljava/lang/invoke/MethodHandle;>;"));
        cn.methods.add(genInitMethod(codePoolGenerators));
        cn.methods.add(genExecuteMethod());
        cn.methods.add(genInterpretMethod());
        cn.methods.add(genResolveMethod());
        cn.methods.add(genNextTokenMethod());
        cn.methods.add(genConstantStringMethod());
        cn.methods.add(genGetFieldMethod());
        cn.methods.add(genSetFieldMethod());
        cn.methods.add(genFieldHandleMethod());
        cn.methods.add(genInvokeMethod());
        cn.methods.add(genLoadOwnerMethod());
        cn.methods.add(genRethrowMethod());
    }

    private MethodNode genInterpretMethod()
    {
        MethodNode methodNode = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, "interpret", "(" + vmProgramGenerator.descriptor() + methodFrameGenerator.descriptor() + ")V");
        InsnBuilder ib = new InsnBuilder(methodNode.instructions);
        // int[] code = program.code();
        ib.aload(InterpretContext.PROGRAM);
        ib.invokeVirtual(vmProgramGenerator.className(), "code", "()[I");
        ib.astore(InterpretContext.CODE);
        // Object[] constants = program.constants();
        ib.aload(InterpretContext.PROGRAM);
        ib.invokeVirtual(vmProgramGenerator.className(), "constants", "()[Ljava/lang/Object;");
        ib.astore(InterpretContext.CONSTANTS);

        LabelNode loopStart = new LabelNode();
        LabelNode loopEnd = new LabelNode();
        LabelNode unknownOpcode = new LabelNode();

        // while (!frame.returned)
        ib.label(loopStart);
        ib.aload(InterpretContext.FRAME);
        ib.getField(methodFrameGenerator.className(), "returned", "Z");
        ib.ifne(loopEnd);

        // int opcode = nextToken(code, frame);
        ib.aload(InterpretContext.CODE);
        ib.aload(InterpretContext.FRAME);
        ib.invokeStatic(className(), "nextToken", "([I" + methodFrameGenerator.descriptor() + ")I");
        ib.istore(InterpretContext.OPCODE);

        generateDispatch(
                methodNode,
                loopStart,
                unknownOpcode
        );

        ib.label(unknownOpcode);
        generateUnknownOpcode(ib);

        ib.label(loopEnd);
        ib._return();

        return methodNode;
    }

    private void generateDispatch(MethodNode method, LabelNode loopStart, LabelNode unknownOpcode)
    {
        List<Opcs> opcodes = new ArrayList<>(branches.keySet());

        opcodes.sort((left, right) -> Integer.compare(
                opcMutator.toMutated(left),
                opcMutator.toMutated(right)
        ));

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

        InterpretContext context = new InterpretContext(
                className(),
                methodFrameGenerator.className(),
                loopStart
        );

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
                ib.goto_(loopStart);
            }
        }
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
        ib.getField(methodFrameGenerator.className(), "programCounter", "I");
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
        ib.invokeStatic(className(), "resolve", "(I)" + vmProgramGenerator.descriptor());
        ib.astore(program);

        // MethodFrame frame = new MethodFrame(program.maxLocals(), program.maxStack());
        ib.new_(methodFrameGenerator.className());
        ib.dup();
        ib.aload(program);
        ib.invokeVirtual(vmProgramGenerator.className(), "maxLocals", "()I");
        ib.aload(program);
        ib.invokeVirtual(vmProgramGenerator.className(), "maxStack", "()I");
        ib.invokeSpecial(methodFrameGenerator.className(), "<init>", "(II)V");
        ib.astore(frame);

        LabelNode staticMethod = new LabelNode();
        LabelNode argumentsReady = new LabelNode();

        // Instance methods reserve locals[0] for the receiver.
        ib.aload(receiver);
        ib.ifNull(staticMethod);
        ib.aload(frame);
        ib.getField(methodFrameGenerator.className(), "locals", "[Ljava/lang/Object;");
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
        ib.getField(methodFrameGenerator.className(), "locals", "[Ljava/lang/Object;");
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
        ib.invokeStatic(
                className(),
                "interpret",
                "(" + vmProgramGenerator.descriptor() +
                        methodFrameGenerator.descriptor() + ")V");

        ib.aload(frame);
        ib.getField(methodFrameGenerator.className(), "returnValue", "Ljava/lang/Object;");
        ib.areturn();
        return methodNode;
    }

    private MethodNode genResolveMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                "resolve",
                "(I)" + vmProgramGenerator.descriptor());
        InsnBuilder ib = new InsnBuilder(method.instructions);
        int resolved = 1;
        int iterator = 2;
        int candidate = 3;

        ib.aconstNull();
        ib.astore(resolved);
        ib.getStatic(className(), "CODE_POOLS", "Ljava/util/List;");
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

    private MethodNode genNextTokenMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                "nextToken",
                "([I" + methodFrameGenerator.descriptor() + ")I");
        InsnBuilder ib = new InsnBuilder(method.instructions);
        ib.aload(0);
        ib.aload(1);
        ib.dup();
        ib.getField(methodFrameGenerator.className(), "programCounter", "I");
        ib.dupX1();
        ib.iconst1();
        ib.iadd();
        ib.putField(methodFrameGenerator.className(), "programCounter", "I");
        ib.iaload();
        ib.ireturn();
        return method;
    }

    private MethodNode genConstantStringMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                "constantString",
                "([Ljava/lang/Object;I)Ljava/lang/String;");
        InsnBuilder ib = new InsnBuilder(method.instructions);
        ib.aload(0);
        ib.iload(1);
        ib.aaload();
        ib.checkCast("java/lang/String");
        ib.areturn();
        return method;
    }

    private MethodNode genGetFieldMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                "getField",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZLjava/lang/Object;)Ljava/lang/Object;");
        InsnBuilder ib = new InsnBuilder(method.instructions);
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        LabelNode instanceField = new LabelNode();
        LabelNode argumentsReady = new LabelNode();
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
        ib.iload(3);
        ib.ifeq(instanceField);
        ib.invokeStatic("java/util/Collections", "emptyList", "()Ljava/util/List;");
        ib.goto_(argumentsReady);
        ib.label(instanceField);
        ib.aload(4);
        ib.invokeStatic(
                "java/util/Collections",
                "singletonList",
                "(Ljava/lang/Object;)Ljava/util/List;");
        ib.label(argumentsReady);
        ib.invokeVirtual(
                "java/lang/invoke/MethodHandle",
                "invokeWithArguments",
                "(Ljava/util/List;)Ljava/lang/Object;");
        ib.label(end);
        ib.areturn();

        ib.label(handler);
        ib.astore(5);
        ib.aload(5);
        ib.invokeStatic(className(), "rethrow", "(Ljava/lang/Throwable;)Ljava/lang/RuntimeException;");
        ib.athrow();
        return method;
    }

    private MethodNode genSetFieldMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                "setField",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZLjava/lang/Object;Ljava/lang/Object;)V");
        InsnBuilder ib = new InsnBuilder(method.instructions);
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        LabelNode instanceField = new LabelNode();
        LabelNode argumentsReady = new LabelNode();
        method.tryCatchBlocks.add(new org.objectweb.asm.tree.TryCatchBlockNode(
                start, end, handler, "java/lang/Throwable"));

        ib.label(start);
        ib.aload(0);
        ib.aload(1);
        ib.aload(2);
        ib.iload(3);
        ib.iconst1();
        ib.invokeStatic(
                className(),
                "fieldHandle",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZ)Ljava/lang/invoke/MethodHandle;");
        ib.iload(3);
        ib.ifeq(instanceField);
        ib.aload(5);
        ib.invokeStatic(
                "java/util/Collections",
                "singletonList",
                "(Ljava/lang/Object;)Ljava/util/List;");
        ib.goto_(argumentsReady);

        ib.label(instanceField);
        ib.iconst2();
        ib.aneArray("java/lang/Object");
        ib.dup();
        ib.iconst0();
        ib.aload(4);
        ib.aastore();
        ib.dup();
        ib.iconst1();
        ib.aload(5);
        ib.aastore();
        ib.invokeStatic("java/util/Arrays", "asList", "([Ljava/lang/Object;)Ljava/util/List;");

        ib.label(argumentsReady);
        ib.invokeVirtual(
                "java/lang/invoke/MethodHandle",
                "invokeWithArguments",
                "(Ljava/util/List;)Ljava/lang/Object;");
        ib.pop();
        ib.label(end);
        ib._return();

        ib.label(handler);
        ib.astore(6);
        ib.aload(6);
        ib.invokeStatic(className(), "rethrow", "(Ljava/lang/Throwable;)Ljava/lang/RuntimeException;");
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
        int lookup = 10;
        int handle = 11;
        int exception = 12;

        emitFieldHandleKey(ib);
        ib.astore(key);
        ib.getStatic(className(), "FIELD_HANDLES", "Ljava/util/Map;");
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
        LabelNode getter = new LabelNode();
        LabelNode handleReady = new LabelNode();
        method.tryCatchBlocks.add(new org.objectweb.asm.tree.TryCatchBlockNode(
                start, end, handler, "java/lang/ReflectiveOperationException"));

        ib.label(create);
        ib.label(start);
        ib.aload(0);
        ib.invokeStatic(className(), "loadOwner", "(Ljava/lang/String;)Ljava/lang/Class;");
        ib.astore(ownerClass);

        ib.new_("java/lang/StringBuilder");
        ib.dup();
        ib.ldc("()");
        ib.invokeSpecial("java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V");
        ib.aload(2);
        ib.invokeVirtual(
                "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        ib.invokeVirtual("java/lang/StringBuilder", "toString", "()Ljava/lang/String;");
        ib.aload(ownerClass);
        ib.invokeVirtual("java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;");
        ib.invokeStatic(
                "java/lang/invoke/MethodType",
                "fromMethodDescriptorString",
                "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;");
        ib.invokeVirtual("java/lang/invoke/MethodType", "returnType", "()Ljava/lang/Class;");
        ib.astore(fieldType);

        ib.aload(ownerClass);
        ib.aload(1);
        ib.invokeVirtual("java/lang/Class", "getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;");
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
        ib.invokeStatic(
                "java/lang/invoke/MethodHandles",
                "lookup",
                "()Ljava/lang/invoke/MethodHandles$Lookup;");
        ib.astore(lookup);
        ib.iload(4);
        ib.ifeq(getter);
        ib.aload(lookup);
        ib.aload(field);
        ib.invokeVirtual(
                "java/lang/invoke/MethodHandles$Lookup",
                "unreflectSetter",
                "(Ljava/lang/reflect/Field;)Ljava/lang/invoke/MethodHandle;");
        ib.goto_(handleReady);
        ib.label(getter);
        ib.aload(lookup);
        ib.aload(field);
        ib.invokeVirtual(
                "java/lang/invoke/MethodHandles$Lookup",
                "unreflectGetter",
                "(Ljava/lang/reflect/Field;)Ljava/lang/invoke/MethodHandle;");
        ib.label(handleReady);
        ib.astore(handle);
        ib.getStatic(className(), "FIELD_HANDLES", "Ljava/util/Map;");
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

    private MethodNode genInvokeMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                "invoke",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/invoke/MethodType;ZLjava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
        InsnBuilder ib = new InsnBuilder(method.instructions);
        int key = 6;
        int handle = 7;
        int ownerClass = 8;
        int target = 9;
        int invocationArguments = 10;
        int exception = 11;

        emitMethodHandleKey(ib);
        ib.astore(key);
        ib.getStatic(className(), "METHOD_HANDLES", "Ljava/util/Map;");
        ib.aload(key);
        ib.invokeInterface("java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;");
        ib.checkCast("java/lang/invoke/MethodHandle");
        ib.astore(handle);

        LabelNode handleReady = new LabelNode();
        ib.aload(handle);
        ib.ifNonNull(handleReady);

        LabelNode reflectionStart = new LabelNode();
        LabelNode reflectionEnd = new LabelNode();
        LabelNode reflectionHandler = new LabelNode();
        LabelNode returnTypeMatches = new LabelNode();
        LabelNode modifiersMatch = new LabelNode();
        method.tryCatchBlocks.add(new org.objectweb.asm.tree.TryCatchBlockNode(
                reflectionStart,
                reflectionEnd,
                reflectionHandler,
                "java/lang/ReflectiveOperationException"));

        ib.label(reflectionStart);
        ib.aload(0);
        ib.invokeStatic(className(), "loadOwner", "(Ljava/lang/String;)Ljava/lang/Class;");
        ib.astore(ownerClass);
        ib.aload(ownerClass);
        ib.aload(1);
        ib.aload(2);
        ib.invokeVirtual("java/lang/invoke/MethodType", "parameterArray", "()[Ljava/lang/Class;");
        ib.invokeVirtual(
                "java/lang/Class",
                "getDeclaredMethod",
                "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;");
        ib.astore(target);
        ib.aload(target);
        ib.iconst1();
        ib.invokeVirtual("java/lang/reflect/Method", "setAccessible", "(Z)V");

        ib.aload(target);
        ib.invokeVirtual("java/lang/reflect/Method", "getReturnType", "()Ljava/lang/Class;");
        ib.aload(2);
        ib.invokeVirtual("java/lang/invoke/MethodType", "returnType", "()Ljava/lang/Class;");
        ib.ifAcmpEq(returnTypeMatches);
        emitNoSuchMethod(ib, ownerClass);

        ib.label(returnTypeMatches);
        ib.aload(target);
        ib.invokeVirtual("java/lang/reflect/Method", "getModifiers", "()I");
        ib.invokeStatic("java/lang/reflect/Modifier", "isStatic", "(I)Z");
        ib.iload(3);
        ib.ifIcmpEq(modifiersMatch);
        emitNoSuchMethod(ib, ownerClass);

        ib.label(modifiersMatch);
        ib.invokeStatic(
                "java/lang/invoke/MethodHandles",
                "lookup",
                "()Ljava/lang/invoke/MethodHandles$Lookup;");
        ib.aload(target);
        ib.invokeVirtual(
                "java/lang/invoke/MethodHandles$Lookup",
                "unreflect",
                "(Ljava/lang/reflect/Method;)Ljava/lang/invoke/MethodHandle;");
        ib.astore(handle);
        ib.getStatic(className(), "METHOD_HANDLES", "Ljava/util/Map;");
        ib.aload(key);
        ib.aload(handle);
        ib.invokeInterface(
                "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
        ib.pop();
        ib.label(reflectionEnd);
        ib.goto_(handleReady);

        ib.label(reflectionHandler);
        ib.astore(exception);
        ib.new_("java/lang/IllegalStateException");
        ib.dup();
        ib.aload(exception);
        ib.invokeSpecial("java/lang/IllegalStateException", "<init>", "(Ljava/lang/Throwable;)V");
        ib.athrow();

        ib.label(handleReady);
        ib.new_("java/util/ArrayList");
        ib.dup();
        ib.invokeSpecial("java/util/ArrayList", "<init>", "()V");
        ib.astore(invocationArguments);
        LabelNode argumentsReady = new LabelNode();
        ib.iload(3);
        ib.ifne(argumentsReady);
        ib.aload(invocationArguments);
        ib.aload(4);
        ib.invokeInterface("java/util/List", "add", "(Ljava/lang/Object;)Z");
        ib.pop();
        ib.label(argumentsReady);
        ib.aload(invocationArguments);
        ib.aload(5);
        ib.invokeStatic("java/util/Arrays", "asList", "([Ljava/lang/Object;)Ljava/util/List;");
        ib.invokeInterface("java/util/List", "addAll", "(Ljava/util/Collection;)Z");
        ib.pop();

        LabelNode invokeStart = new LabelNode();
        LabelNode invokeEnd = new LabelNode();
        LabelNode invokeHandler = new LabelNode();
        method.tryCatchBlocks.add(new org.objectweb.asm.tree.TryCatchBlockNode(
                invokeStart, invokeEnd, invokeHandler, "java/lang/Throwable"));
        ib.label(invokeStart);
        ib.aload(handle);
        ib.aload(invocationArguments);
        ib.invokeVirtual(
                "java/lang/invoke/MethodHandle",
                "invokeWithArguments",
                "(Ljava/util/List;)Ljava/lang/Object;");
        ib.label(invokeEnd);
        ib.areturn();

        ib.label(invokeHandler);
        ib.astore(exception);
        ib.aload(exception);
        ib.invokeStatic(className(), "rethrow", "(Ljava/lang/Throwable;)Ljava/lang/RuntimeException;");
        ib.athrow();
        return method;
    }

    private MethodNode genLoadOwnerMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                "loadOwner",
                "(Ljava/lang/String;)Ljava/lang/Class;");
        InsnBuilder ib = new InsnBuilder(method.instructions);
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        method.tryCatchBlocks.add(new org.objectweb.asm.tree.TryCatchBlockNode(
                start, end, handler, "java/lang/ClassNotFoundException"));

        ib.label(start);
        ib.aload(0);
        ib.bipush('/');
        ib.bipush('.');
        ib.invokeVirtual("java/lang/String", "replace", "(CC)Ljava/lang/String;");
        ib.iconst0();
        ib.ldc(org.objectweb.asm.Type.getObjectType(className()));
        ib.invokeVirtual("java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;");
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
        return method;
    }

    private MethodNode genRethrowMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                "rethrow",
                "(Ljava/lang/Throwable;)Ljava/lang/RuntimeException;");
        InsnBuilder ib = new InsnBuilder(method.instructions);
        LabelNode notRuntime = new LabelNode();
        LabelNode notError = new LabelNode();

        ib.aload(0);
        ib.instanceOf("java/lang/RuntimeException");
        ib.ifeq(notRuntime);
        ib.aload(0);
        ib.checkCast("java/lang/RuntimeException");
        ib.areturn();

        ib.label(notRuntime);
        ib.aload(0);
        ib.instanceOf("java/lang/Error");
        ib.ifeq(notError);
        ib.aload(0);
        ib.checkCast("java/lang/Error");
        ib.athrow();

        ib.label(notError);
        ib.new_("java/lang/IllegalStateException");
        ib.dup();
        ib.aload(0);
        ib.invokeSpecial("java/lang/IllegalStateException", "<init>", "(Ljava/lang/Throwable;)V");
        ib.areturn();
        return method;
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

    private MethodNode genInitMethod(List<CodePoolGenerator> codePoolGenerators)
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
        ib.putStatic(className(), "CODE_POOLS", "Ljava/util/List;");

        ib.new_("java/util/concurrent/ConcurrentHashMap");
        ib.dup();
        ib.invokeSpecial("java/util/concurrent/ConcurrentHashMap", "<init>", "()V");
        ib.putStatic(className(), "FIELD_HANDLES", "Ljava/util/Map;");

        ib.new_("java/util/concurrent/ConcurrentHashMap");
        ib.dup();
        ib.invokeSpecial("java/util/concurrent/ConcurrentHashMap", "<init>", "()V");
        ib.putStatic(className(), "METHOD_HANDLES", "Ljava/util/Map;");

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
