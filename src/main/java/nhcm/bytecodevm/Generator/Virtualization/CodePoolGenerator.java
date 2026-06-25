package nhcm.bytecodevm.Generator.Virtualization;

import lombok.Getter;
import nhcm.bytecodevm.Config.BytecodeVMConfig;
import nhcm.bytecodevm.Data.CompiledMethod;
import nhcm.bytecodevm.Data.VMInsn.VMInstruction;
import nhcm.bytecodevm.Data.VMInsn.VMMethod;
import nhcm.bytecodevm.Enums.Acc;
import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.AdvInsn.Expr;
import nhcm.bytecodevm.AdvInsn.Local;
import nhcm.bytecodevm.AdvInsn.SwitchCase;
import nhcm.bytecodevm.Generator.Abstract.ClassObj;
import nhcm.bytecodevm.Generator.GlobalClass.VMCodePoolGenerator;
import nhcm.bytecodevm.Generator.GlobalClass.VMProgramGenerator;
import nhcm.bytecodevm.Utils.*;
import nhcm.bytecodevm.Utils.Builder.FieldRef;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;
import java.util.function.Function;

public class CodePoolGenerator extends ClassObj
{
    @Getter
    public final ClassNode classNode;
    @Getter
    public final CodePoolLayout layout;

    private final List<CompiledMethod> compiledMethods;
    private final List<CompiledMethod> methodsByCodeIndex;
    private final List<CompiledMethod> methodsByOperandIndex;
    private final List<CompiledMethod> methodsByLayoutIndex;
    private final List<CompiledMethod> methodsByConstantsIndex;
    private final List<CompiledMethod> methodsByExceptionHandlersIndex;
    private final List<CompiledMethod> methodsByOpcodeMapIndex;
    private final List<CompiledMethod> methodsByMethodKeyIndex;
    private final List<CompiledMethod> methodsByMaxLocalsIndex;
    private final List<CompiledMethod> methodsByMaxStackIndex;
    private final Map<Integer, Integer> codeIndexById;
    private final Map<Integer, Integer> operandIndexById;
    private final Map<Integer, Integer> layoutIndexById;
    private final Map<Integer, Integer> constantsIndexById;
    private final Map<Integer, Integer> exceptionHandlersIndexById;
    private final Map<Integer, Integer> opcodeMapIndexById;
    private final Map<Integer, Integer> methodKeyIndexById;
    private final Map<Integer, Integer> maxLocalsIndexById;
    private final Map<Integer, Integer> maxStackIndexById;
    private final Map<Integer, ProtectedVMMethod> protectedMethodById;
    private final VMProgramGenerator vmProgramGenerator;
    private final BytecodeVMConfig config;

    public CodePoolGenerator(
            String className,
            List<CompiledMethod> compiledMethods,
            VMProgramGenerator vmProgramGenerator,
            VMCodePoolGenerator vmCodePoolGenerator,
            BytecodeVMConfig config,
            boolean shuffleMethods)
    {
        super(className);
        this.config = Objects.requireNonNull(config, "config");
        this.vmProgramGenerator = vmProgramGenerator;
        this.layout = new CodePoolLayout(className, vmCodePoolGenerator.descriptor(), vmProgramGenerator.descriptor());
        if (vmCodePoolGenerator.vmProgramGenerator != vmProgramGenerator)
        {
            throw new IllegalArgumentException("VMCodePoolGenerator uses a different VMProgramGenerator");
        }
        validateUniqueCodeIds(compiledMethods);
        this.compiledMethods = List.copyOf(compiledMethods);
        this.protectedMethodById = protectMethods(this.compiledMethods, this.config);
        this.methodsByCodeIndex = createLayout(compiledMethods, shuffleMethods);
        this.methodsByOperandIndex = createLayout(compiledMethods, shuffleMethods);
        this.methodsByLayoutIndex = createLayout(compiledMethods, shuffleMethods);
        this.methodsByConstantsIndex = createLayout(compiledMethods, shuffleMethods);
        this.methodsByExceptionHandlersIndex = createLayout(compiledMethods, shuffleMethods);
        this.methodsByOpcodeMapIndex = createLayout(compiledMethods, shuffleMethods);
        this.methodsByMethodKeyIndex = createLayout(compiledMethods, shuffleMethods);
        this.methodsByMaxLocalsIndex = createLayout(compiledMethods, shuffleMethods);
        this.methodsByMaxStackIndex = createLayout(compiledMethods, shuffleMethods);
        this.codeIndexById = indexByCodeId(methodsByCodeIndex);
        this.operandIndexById = indexByCodeId(methodsByOperandIndex);
        this.layoutIndexById = indexByCodeId(methodsByLayoutIndex);
        this.constantsIndexById = indexByCodeId(methodsByConstantsIndex);
        this.exceptionHandlersIndexById = indexByCodeId(methodsByExceptionHandlersIndex);
        this.opcodeMapIndexById = indexByCodeId(methodsByOpcodeMapIndex);
        this.methodKeyIndexById = indexByCodeId(methodsByMethodKeyIndex);
        this.maxLocalsIndexById = indexByCodeId(methodsByMaxLocalsIndex);
        this.maxStackIndexById = indexByCodeId(methodsByMaxStackIndex);

        ClassNode cn = ClassUtils.newClassNode(new Acc[]{Acc.PUBLIC, Acc.FINAL}, className);
        this.classNode = cn;
        cn.interfaces.add(vmCodePoolGenerator.className());
        if (containsConstantDynamic(this.compiledMethods))
        {
            throw new IllegalArgumentException(
                    "ConstantDynamic cannot be emitted in a Java 8 CodePool");
        }
        InsnUtils.addPrivateInit(cn);
        cn.fields.add(FieldUtils.newFieldNode(
                new Acc[]{Acc.PUBLIC, Acc.STATIC, Acc.FINAL},
                layout.instance.name(),
                layout.instance.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, layout.opcodeStreams.name(), layout.opcodeStreams.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, layout.operandStreams.name(), layout.operandStreams.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, layout.layoutStreams.name(), layout.layoutStreams.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, layout.constants.name(), layout.constants.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, layout.exceptionHandlers.name(), layout.exceptionHandlers.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, layout.opcodeMaps.name(), layout.opcodeMaps.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, layout.methodKeys.name(), layout.methodKeys.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, layout.maxLocals.name(), layout.maxLocals.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, layout.maxStack.name(), layout.maxStack.descriptor()));

        MethodNode clinit = MethodUtils.newMethodNode(new Acc[]{Acc.STATIC}, "<clinit>", "()V");
        clinit.instructions.add(initOPCODE_STREAMS());
        clinit.instructions.add(initOPERAND_STREAMS());
        clinit.instructions.add(initLAYOUT_STREAMS());
        clinit.instructions.add(initCONSTANTS());
        clinit.instructions.add(initEXCEPTION_HANDLERS());
        clinit.instructions.add(initOPCODE_MAPS());
        clinit.instructions.add(initMETHOD_KEYS());
        clinit.instructions.add(initMAX_LOCALS_MAX_STACK());
        AdvInsnBuilder ib = new AdvInsnBuilder(0);
        ib.set(AdvInsnBuilder.staticField(layout.instance), AdvInsnBuilder.newObject(layout.owner));
        ib.returnVoid();
        clinit.instructions.add(ib.toInsnList());
        cn.methods.add(clinit);

        cn.methods.add(generateFind());
        cn.methods.add(genMixMethod());
        cn.methods.add(genArrayMixMethod());
        cn.methods.add(genUnpackIntsMethod());
    }

    public List<Opcs> getUsedOpcodes()
    {
        Set<Opcs> usedOpcodes = new LinkedHashSet<>();
        for(CompiledMethod method : compiledMethods)
        {
            VMMethod vmMethod = method.vmMethod;
            for(VMInstruction insn : vmMethod)
            {
                usedOpcodes.add(insn.opcode);
            }
        }
        return List.copyOf(usedOpcodes);
    }

    public static void boxIfPrimitive(InsnBuilder ib, Type type)
    {
        switch (type.getSort())
        {
            case Type.BOOLEAN -> ib.invokeStatic("java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;");
            case Type.BYTE -> ib.invokeStatic("java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;");
            case Type.CHAR -> ib.invokeStatic("java/lang/Character", "valueOf", "(C)Ljava/lang/Character;");
            case Type.SHORT -> ib.invokeStatic("java/lang/Short", "valueOf", "(S)Ljava/lang/Short;");
            case Type.INT -> ib.invokeStatic("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;");
            case Type.FLOAT -> ib.invokeStatic("java/lang/Float", "valueOf", "(F)Ljava/lang/Float;");
            case Type.LONG -> ib.invokeStatic("java/lang/Long", "valueOf", "(J)Ljava/lang/Long;");
            case Type.DOUBLE -> ib.invokeStatic("java/lang/Double", "valueOf", "(D)Ljava/lang/Double;");
            case Type.ARRAY, Type.OBJECT -> { }
            default -> throw new IllegalArgumentException("Unsupported constant descriptor: " + type.getDescriptor());
        }
    }

    private MethodNode genMixMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                layout.mix.name(), // mix
                layout.mix.descriptor() // (IIII)I
        );
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

    private MethodNode genArrayMixMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                layout.arrayMix.name(), // arrayMix
                layout.arrayMix.descriptor() // (II)I
        );
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local key = ib.getLocal("key", "I", 0);
        Local index = ib.getLocal("index", "I", 1);
        ib.returnValue(AdvInsnBuilder.callStatic(
                layout.owner,
                "mix",
                "I",
                key,
                index,
                AdvInsnBuilder.constant(ProtectedVMMethod.SALT_ARRAY),
                AdvInsnBuilder.constant(0)));
        return method;
    }

    private MethodNode genUnpackIntsMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                layout.unpackInts.name(), // unpackInts
                layout.unpackInts.descriptor() // ([JII)[I
        );
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local packed = ib.getLocal("packed", "[J", 0);
        Local length = ib.getLocal("length", "I", 1);
        Local key = ib.getLocal("key", "I", 2);
        Local result = ib.getLocal("result", "[I", 3);
        Local pair = ib.getLocal("pair", "I", 4);
        Local outIndex = ib.getLocal("outIndex", "I", 5);
        Local word = ib.getLocal("word", "J", 6);

        ib.set(result, AdvInsnBuilder.newArray("int", length));
        ib.set(pair, AdvInsnBuilder.constant(0));
        ib.set(outIndex, AdvInsnBuilder.constant(0));
        ib.whileLoop(
                AdvInsnBuilder.lessThan(pair, AdvInsnBuilder.arrayLength(packed)),
                b -> {
                    b.set(word, AdvInsnBuilder.arrayAt(packed, pair));
                    b.setArray(
                            result,
                            outIndex,
                            AdvInsnBuilder.bitXor(
                                    AdvInsnBuilder.cast(AdvInsnBuilder.unsignedShiftRight(word, AdvInsnBuilder.constant(32)), "I"),
                                    AdvInsnBuilder.callStatic(layout.owner, "arrayMix", "I", key, outIndex)));
                    b.ifCondition(
                            AdvInsnBuilder.lessThan(AdvInsnBuilder.plus(outIndex, AdvInsnBuilder.constant(1)), length),
                            odd -> odd.setArray(
                                    result,
                                    AdvInsnBuilder.plus(outIndex, AdvInsnBuilder.constant(1)),
                                    AdvInsnBuilder.bitXor(
                                            AdvInsnBuilder.cast(word, "I"),
                                            AdvInsnBuilder.callStatic(
                                                    layout.owner,
                                                    "arrayMix",
                                                    "I",
                                                    key,
                                                    AdvInsnBuilder.plus(outIndex, AdvInsnBuilder.constant(1))))));
                    b.increment(pair, 1);
                    b.increment(outIndex, 2);
                });
        ib.returnValue(result);
        return method;
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

    private InsnList initOPCODE_STREAMS()
    {
        return initIntRows(
                "opcodeStreams",
                layout.opcodeStreams,
                methodsByCodeIndex,
                method -> protectedMethodById.get(method.codeId).opcodeStream);
    }

    private InsnList initOPERAND_STREAMS()
    {
        return initIntRows(
                "operandStreams",
                layout.operandStreams,
                methodsByOperandIndex,
                method -> protectedMethodById.get(method.codeId).operandStream);
    }

    private InsnList initLAYOUT_STREAMS()
    {
        return initIntRows(
                "layoutStreams",
                layout.layoutStreams,
                methodsByLayoutIndex,
                method -> protectedMethodById.get(method.codeId).layoutStream);
    }

    private InsnList initOPCODE_MAPS()
    {
        return initIntRows(
                "opcodeMaps",
                layout.opcodeMaps,
                methodsByOpcodeMapIndex,
                method -> protectedMethodById.get(method.codeId).opcodeMap);
    }

    private InsnList initMETHOD_KEYS()
    {
        AdvInsnBuilder ib = new AdvInsnBuilder(0);
        int[] keys = new int[methodsByMethodKeyIndex.size()];
        for (int slot = 0; slot < methodsByMethodKeyIndex.size(); slot++)
        {
            keys[slot] = protectedMethodById.get(methodsByMethodKeyIndex.get(slot).codeId).methodKey;
        }
        Local methodKeys = emitIntArray(ib, "methodKeys", keys);
        ib.set(AdvInsnBuilder.staticField(layout.methodKeys), methodKeys);
        return ib.toInsnList();
    }

    private InsnList initIntRows(
            String localName,
            FieldRef target,
            List<CompiledMethod> methods,
            Function<CompiledMethod, int[]> data)
    {
        AdvInsnBuilder ib = new AdvInsnBuilder(0);
        Local table = ib.var(localName, "[[I");
        ib.set(table, AdvInsnBuilder.newMultiArray("[[I", 1, AdvInsnBuilder.constant(methods.size())));
        for (int slot = 0; slot < methods.size(); slot++)
        {
            Local row = emitIntArray(ib, localName + slot, data.apply(methods.get(slot)));
            ib.setArray(table, AdvInsnBuilder.constant(slot), row);
        }
        ib.set(AdvInsnBuilder.staticField(target), table);
        return ib.toInsnList();
    }

    private Local emitIntArray(AdvInsnBuilder ib, String name, int[] values)
    {
        if (config.dynamicCodePoolBuild)
        {
            int key = RandomUtils.randomInt();
            long[] packedValues = packInts(values, key);
            Local packed = ib.var(name + "Packed", "[J");
            ib.set(packed, AdvInsnBuilder.newArray("long", AdvInsnBuilder.constant(packedValues.length)));
            for (int index = 0; index < packedValues.length; index++)
            {
                ib.setArray(packed, AdvInsnBuilder.constant(index), AdvInsnBuilder.constant(packedValues[index]));
            }
            Local result = ib.var(name, "[I");
            ib.set(result, AdvInsnBuilder.callStatic(
                    layout.owner,
                    "unpackInts",
                    "[I",
                    packed,
                    AdvInsnBuilder.constant(values.length),
                    AdvInsnBuilder.constant(key)));
            return result;
        }

        Local result = ib.var(name, "[I");
        ib.set(result, AdvInsnBuilder.newArray("int", AdvInsnBuilder.constant(values.length)));
        for (int index = 0; index < values.length; index++)
        {
            ib.setArray(result, AdvInsnBuilder.constant(index), AdvInsnBuilder.constant(values[index]));
        }
        return result;
    }

    private static long[] packInts(int[] values, int key)
    {
        long[] packed = new long[(values.length + 1) / 2];
        for (int pair = 0; pair < packed.length; pair++)
        {
            int leftIndex = pair * 2;
            int left = values[leftIndex] ^ ProtectedVMMethod.arrayMix(key, leftIndex);
            int rightIndex = leftIndex + 1;
            int right = rightIndex < values.length
                    ? values[rightIndex] ^ ProtectedVMMethod.arrayMix(key, rightIndex)
                    : RandomUtils.randomInt();
            packed[pair] = ((long) left << 32) | (right & 0xffffffffL);
        }
        return packed;
    }

    private InsnList initCONSTANTS()
    {
        AdvInsnBuilder ib = new AdvInsnBuilder(0);
        Local constantsTable = ib.var("constants", "[[Ljava/lang/Object;");
        ib.set(constantsTable, AdvInsnBuilder.newMultiArray(layout.constants.descriptor(), 1, AdvInsnBuilder.constant(methodsByConstantsIndex.size())));

        for (int slot = 0;
             slot < methodsByConstantsIndex.size();
             slot++)
        {
            Object[] constants = protectedMethodById.get(methodsByConstantsIndex.get(slot).codeId).constants;

            Local constantRow = ib.var("constants" + slot, "[Ljava/lang/Object;");
            ib.set(constantRow, AdvInsnBuilder.newArray("java/lang/Object", AdvInsnBuilder.constant(constants.length)));

            for (int constantIndex = 0;
                 constantIndex < constants.length;
                 constantIndex++)
            {
                emitConstant(ib, constantRow, constantIndex, constants[constantIndex]);
            }

            ib.setArray(constantsTable, AdvInsnBuilder.constant(slot), constantRow);
        }

        ib.set(AdvInsnBuilder.staticField(layout.constants), constantsTable);

        return ib.toInsnList();
    }

    private InsnList initEXCEPTION_HANDLERS()
    {
        AdvInsnBuilder ib = new AdvInsnBuilder(0);
        Local exceptionHandlers = ib.var("exceptionHandlers", "[[I");
        ib.set(exceptionHandlers, AdvInsnBuilder.newMultiArray(layout.exceptionHandlers.descriptor(), 1, AdvInsnBuilder.constant(methodsByExceptionHandlersIndex.size())));
        for (int slot = 0; slot < methodsByExceptionHandlersIndex.size(); slot++)
        {
            int[] handlers = protectedMethodById.get(methodsByExceptionHandlersIndex.get(slot).codeId).exceptionHandlers;

            Local handlerRow = emitIntArray(ib, "exceptionHandlers" + slot, handlers);
            ib.setArray(exceptionHandlers, AdvInsnBuilder.constant(slot), handlerRow);
        }

        ib.set(AdvInsnBuilder.staticField(layout.exceptionHandlers), exceptionHandlers);
        return ib.toInsnList();
    }

    private void emitConstant(AdvInsnBuilder ib, Expr constants, int constantIndex, Object value)
    {
        switch (value)
        {
            case null -> ib.setArray(constants, AdvInsnBuilder.constant(constantIndex), AdvInsnBuilder.constant(null));
            case ProtectedVMMethod.EncodedString encoded -> ib.setArray(constants, AdvInsnBuilder.constant(constantIndex), encodedString(ib, encoded));
            case ProtectedVMMethod.EncodedType encoded -> ib.setArray(constants, AdvInsnBuilder.constant(constantIndex), encodedType(ib, encoded));
            case String ignored -> ib.setArray(constants, AdvInsnBuilder.constant(constantIndex), AdvInsnBuilder.constant(value));
            case Integer integer -> ib.setArray(constants, AdvInsnBuilder.constant(constantIndex), boxedInteger(integer));
            case Long number -> ib.setArray(constants, AdvInsnBuilder.constant(constantIndex), boxedLong(number));
            case Float number -> ib.setArray(constants, AdvInsnBuilder.constant(constantIndex), boxedFloat(number));
            case Double number -> ib.setArray(constants, AdvInsnBuilder.constant(constantIndex), boxedDouble(number));
            case Type type -> ib.setArray(constants, AdvInsnBuilder.constant(constantIndex), typeConstant(ib, type));
            case Handle ignored -> ib.setArray(
                    constants,
                    AdvInsnBuilder.constant(constantIndex),
                    Type.getType(Object.class),
                    b -> b.raw(raw -> raw.ldc(value)),
                    "handle(" + value + ")");
            case ConstantDynamic dynamic -> ib.setArray(
                    constants,
                    AdvInsnBuilder.constant(constantIndex),
                    boxedDynamicType(dynamic),
                    b -> {
                        b.raw(raw -> raw.ldc(dynamic));
                        boxIfPrimitive(b.rawBuilder(), Type.getType(dynamic.getDescriptor()));
                    },
                    "constantDynamic(" + dynamic.getName() + ")");
            default -> throw new IllegalArgumentException("Unsupported constant: " + value.getClass().getName());
        }
    }

    private Expr encodedType(AdvInsnBuilder ib, ProtectedVMMethod.EncodedType value)
    {
        Local encoded = ib.var("encodedType" + Math.abs(value.descriptor().hashCode()) + RandomUtils.randomInt(Integer.MAX_VALUE), "[Ljava/lang/Object;");
        ib.set(encoded, AdvInsnBuilder.newArray("java/lang/Object", AdvInsnBuilder.constant(2)));
        ib.setArray(encoded, AdvInsnBuilder.constant(0), encodedString(ib, ProtectedVMMethod.encodeString("__BytecodeVM_TYPE__")));
        ib.setArray(encoded, AdvInsnBuilder.constant(1), encodedString(ib, ProtectedVMMethod.encodeString(value.descriptor())));
        return encoded;
    }

    private Expr encodedString(AdvInsnBuilder ib, ProtectedVMMethod.EncodedString value)
    {
        Local encoded = ib.var("encodedString" + Math.abs(Arrays.hashCode(value.chars())) + RandomUtils.randomInt(Integer.MAX_VALUE), "[Ljava/lang/Object;");
        ib.set(encoded, AdvInsnBuilder.newArray("java/lang/Object", AdvInsnBuilder.constant(2)));
        ib.setArray(encoded, AdvInsnBuilder.constant(0), emitIntArray(ib, "encodedChars" + RandomUtils.randomInt(Integer.MAX_VALUE), value.chars()));
        ib.setArray(encoded, AdvInsnBuilder.constant(1), boxedInteger(value.key()));
        return encoded;
    }

    private Expr typeConstant(AdvInsnBuilder ib, Type type)
    {
        Local value = ib.var("typeConstant" + ib.rawBuilder().hashCode() + "_" + Math.abs(type.getDescriptor().hashCode()), "[Ljava/lang/Object;");
        ib.set(value, AdvInsnBuilder.newArray("java/lang/Object", AdvInsnBuilder.constant(2)));
        ib.setArray(value, AdvInsnBuilder.constant(0), AdvInsnBuilder.constant("__BytecodeVM_TYPE__"));
        ib.setArray(value, AdvInsnBuilder.constant(1), AdvInsnBuilder.constant(type.getDescriptor()));
        return value;
    }

    private InsnList initMAX_LOCALS_MAX_STACK()
    {
        AdvInsnBuilder ib = new AdvInsnBuilder(0);
        Local maxLocals = ib.var("maxLocals", "[I");
        ib.set(maxLocals, AdvInsnBuilder.newArray("int", AdvInsnBuilder.constant(methodsByMaxLocalsIndex.size())));
        for (int slot = 0; slot < methodsByMaxLocalsIndex.size(); slot++)
        {
            VMMethod vmMethod = methodsByMaxLocalsIndex.get(slot).vmMethod;
            ib.setArray(maxLocals, AdvInsnBuilder.constant(slot), AdvInsnBuilder.constant(vmMethod.maxLocals));
        }
        ib.set(AdvInsnBuilder.staticField(layout.maxLocals), maxLocals);

        Local maxStack = ib.var("maxStack", "[I");
        ib.set(maxStack, AdvInsnBuilder.newArray("int", AdvInsnBuilder.constant(methodsByMaxStackIndex.size())));
        for (int slot = 0; slot < methodsByMaxStackIndex.size(); slot++)
        {
            VMMethod vmMethod = methodsByMaxStackIndex.get(slot).vmMethod;
            ib.setArray(maxStack, AdvInsnBuilder.constant(slot), AdvInsnBuilder.constant(vmMethod.maxStack));
        }
        ib.set(AdvInsnBuilder.staticField(layout.maxStack), maxStack);
        return ib.toInsnList();
    }

    private MethodNode generateFind()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC},
                layout.find.name(),
                layout.find.descriptor());
        AdvInsnBuilder ib = new AdvInsnBuilder(method);
        Local codeIdLocal = ib.getLocal("codeId", "I", 1);

        List<CompiledMethod> methodsByCodeId = new ArrayList<>(compiledMethods);
        methodsByCodeId.sort((left, right) -> Integer.compare(left.codeId, right.codeId));
        SwitchCase[] cases = new SwitchCase[methodsByCodeId.size()];
        for (int index = 0; index < methodsByCodeId.size(); index++)
        {
            int codeId = methodsByCodeId.get(index).codeId;
            cases[index] = AdvInsnBuilder.switchCase(codeId, b -> b.returnValue(programFor(codeId)));
        }

        ib.switchLookup(
                codeIdLocal,
                b -> b.returnValue(AdvInsnBuilder.nullValue(vmProgramGenerator.className())),
                cases);
        return method;
    }

    private Expr programFor(int codeId)
    {
        return AdvInsnBuilder.newObject(
                vmProgramGenerator.layout.owner,
                opcodeStreamRow(codeId),
                operandStreamRow(codeId),
                layoutStreamRow(codeId),
                constantRow(codeId),
                exceptionHandlerRow(codeId),
                opcodeMapRow(codeId),
                methodKeyValue(codeId),
                maxLocalsValue(codeId),
                maxStackValue(codeId));
    }

    private Expr opcodeStreamRow(int codeId)
    {
        return AdvInsnBuilder.arrayAt(
                AdvInsnBuilder.staticField(layout.opcodeStreams),
                AdvInsnBuilder.constant(codeIndexById.get(codeId)));
    }

    private Expr operandStreamRow(int codeId)
    {
        return AdvInsnBuilder.arrayAt(
                AdvInsnBuilder.staticField(layout.operandStreams),
                AdvInsnBuilder.constant(operandIndexById.get(codeId)));
    }

    private Expr layoutStreamRow(int codeId)
    {
        return AdvInsnBuilder.arrayAt(
                AdvInsnBuilder.staticField(layout.layoutStreams),
                AdvInsnBuilder.constant(layoutIndexById.get(codeId)));
    }

    private Expr constantRow(int codeId)
    {
        return AdvInsnBuilder.arrayAt(
                AdvInsnBuilder.staticField(layout.constants),
                AdvInsnBuilder.constant(constantsIndexById.get(codeId)));
    }

    private Expr exceptionHandlerRow(int codeId)
    {
        return AdvInsnBuilder.arrayAt(
                AdvInsnBuilder.staticField(layout.exceptionHandlers),
                AdvInsnBuilder.constant(exceptionHandlersIndexById.get(codeId)));
    }

    private Expr opcodeMapRow(int codeId)
    {
        return AdvInsnBuilder.arrayAt(
                AdvInsnBuilder.staticField(layout.opcodeMaps),
                AdvInsnBuilder.constant(opcodeMapIndexById.get(codeId)));
    }

    private Expr methodKeyValue(int codeId)
    {
        return AdvInsnBuilder.arrayAt(
                AdvInsnBuilder.staticField(layout.methodKeys),
                AdvInsnBuilder.constant(methodKeyIndexById.get(codeId)));
    }

    private Expr maxLocalsValue(int codeId)
    {
        return AdvInsnBuilder.arrayAt(
                AdvInsnBuilder.staticField(layout.maxLocals),
                AdvInsnBuilder.constant(maxLocalsIndexById.get(codeId)));
    }

    private Expr maxStackValue(int codeId)
    {
        return AdvInsnBuilder.arrayAt(
                AdvInsnBuilder.staticField(layout.maxStack),
                AdvInsnBuilder.constant(maxStackIndexById.get(codeId)));
    }

    private static Expr boxedInteger(Integer value)
    {
        return AdvInsnBuilder.callStatic(
                "java/lang/Integer",
                "valueOf",
                "java/lang/Integer",
                AdvInsnBuilder.constant(value));
    }

    private static Expr boxedLong(Long value)
    {
        return AdvInsnBuilder.callStatic(
                "java/lang/Long",
                "valueOf",
                "java/lang/Long",
                AdvInsnBuilder.constant(value));
    }

    private static Expr boxedFloat(Float value)
    {
        return AdvInsnBuilder.callStatic(
                "java/lang/Float",
                "valueOf",
                "java/lang/Float",
                AdvInsnBuilder.constant(value));
    }

    private static Expr boxedDouble(Double value)
    {
        return AdvInsnBuilder.callStatic(
                "java/lang/Double",
                "valueOf",
                "java/lang/Double",
                AdvInsnBuilder.constant(value));
    }

    private static Type boxedDynamicType(ConstantDynamic dynamic)
    {
        Type type = Type.getType(dynamic.getDescriptor());
        return switch (type.getSort())
        {
            case Type.BOOLEAN -> Type.getType(Boolean.class);
            case Type.BYTE -> Type.getType(Byte.class);
            case Type.CHAR -> Type.getType(Character.class);
            case Type.SHORT -> Type.getType(Short.class);
            case Type.INT -> Type.getType(Integer.class);
            case Type.FLOAT -> Type.getType(Float.class);
            case Type.LONG -> Type.getType(Long.class);
            case Type.DOUBLE -> Type.getType(Double.class);
            case Type.ARRAY, Type.OBJECT -> type;
            default -> throw new IllegalArgumentException("Unsupported constant descriptor: " + type.getDescriptor());
        };
    }

    private static List<CompiledMethod> createLayout(
            List<CompiledMethod> methods,
            boolean shuffle)
    {
        List<CompiledMethod> layout = new ArrayList<>(methods);
        if (shuffle)
        {
            RandomUtils.shuffle(layout);
        }
        return List.copyOf(layout);
    }

    private static Map<Integer, Integer> indexByCodeId(List<CompiledMethod> layout)
    {
        Map<Integer, Integer> indexes = new HashMap<>();
        for (int index = 0; index < layout.size(); index++)
        {
            indexes.put(layout.get(index).codeId, index);
        }
        return Map.copyOf(indexes);
    }

    private static Map<Integer, ProtectedVMMethod> protectMethods(
            List<CompiledMethod> methods,
            BytecodeVMConfig config)
    {
        Map<Integer, ProtectedVMMethod> protectedMethods = new HashMap<>();
        for (CompiledMethod method : methods)
        {
            protectedMethods.put(method.codeId, ProtectedVMMethod.from(method, config));
        }
        return Map.copyOf(protectedMethods);
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

    public int getMaxGeneratedMethodSize()
    {
        int maximum = 0;
        for (MethodNode method : classNode.methods)
        {
            maximum = Math.max(maximum, FileUtils.estimateMaxSize(method));
        }
        return maximum;
    }

    private static void validateUniqueCodeIds(List<CompiledMethod> methods)
    {
        Set<Integer> codeIds = new HashSet<>();
        for (CompiledMethod method : methods)
        {
            if (!codeIds.add(method.codeId))
            {
                throw new IllegalArgumentException("Duplicate code id: " + method.codeId);
            }
        }
    }

    private static boolean containsConstantDynamic(List<CompiledMethod> methods)
    {
        for (CompiledMethod method : methods)
        {
            for (Object constant : method.vmMethod.constants)
            {
                if (constant instanceof ConstantDynamic)
                {
                    return true;
                }
            }
        }
        return false;
    }
}
