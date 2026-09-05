package nhcm.bytecodevm.generator.virtualization;

import lombok.Getter;
import nhcm.bytecodevm.advInsn.AdvIBdr;
import nhcm.bytecodevm.advInsn.Expr;
import nhcm.bytecodevm.advInsn.Local;
import nhcm.bytecodevm.advInsn.SwitchCase;
import nhcm.bytecodevm.config.BytecodeVMConfig;
import nhcm.bytecodevm.data.CompiledMethod;
import nhcm.bytecodevm.data.vminsn.VMInstruction;
import nhcm.bytecodevm.data.vminsn.VMMethod;
import nhcm.bytecodevm.enums.Acc;
import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.generator.abstracts.ClassObj;
import nhcm.bytecodevm.generator.GeneratedMemberNamer;
import nhcm.bytecodevm.generator.globalclass.VMCodePoolGenerator;
import nhcm.bytecodevm.generator.globalclass.VMProgramGenerator;
import nhcm.bytecodevm.generator.virtualization.superinstruction.SuperInstructionCombiner;
import nhcm.bytecodevm.generator.virtualization.superinstruction.SuperInstructionRegistry;
import nhcm.bytecodevm.utils.builder.FieldRef;
import nhcm.bytecodevm.utils.builder.InsnBuilder;
import nhcm.bytecodevm.utils.*;
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
    private static final int INT_STRING_CHUNK_CHARS = 4096;

    @Getter
    public final ClassNode classNode;
    @Getter
    public final CodePoolLayout layout;

    private final List<CompiledMethod> compiledMethods;
    private final List<CompiledMethod> methodsByCodeIndex;
    private final List<CompiledMethod> methodsByOperandIndex;
    private final List<CompiledMethod> methodsByLayoutIndex;
    private final List<CompiledMethod> methodsByBlockIndex;
    private final List<CompiledMethod> methodsByConstantsIndex;
    private final List<CompiledMethod> methodsByExceptionHandlersIndex;
    private final List<CompiledMethod> methodsByOpcodeMapIndex;
    private final List<CompiledMethod> methodsByMethodKeyIndex;
    private final List<CompiledMethod> methodsByFeatureFlagsIndex;
    private final List<CompiledMethod> methodsByMaxLocalsIndex;
    private final List<CompiledMethod> methodsByMaxStackIndex;
    private final Map<Integer, Integer> codeIndexById;
    private final Map<Integer, Integer> operandIndexById;
    private final Map<Integer, Integer> layoutIndexById;
    private final Map<Integer, Integer> blockIndexById;
    private final Map<Integer, Integer> constantsIndexById;
    private final Map<Integer, Integer> exceptionHandlersIndexById;
    private final Map<Integer, Integer> opcodeMapIndexById;
    private final Map<Integer, Integer> methodKeyIndexById;
    private final Map<Integer, Integer> featureFlagsIndexById;
    private final Map<Integer, Integer> maxLocalsIndexById;
    private final Map<Integer, Integer> maxStackIndexById;
    private final Map<Integer, ProtectedVMMethod> protectedMethodById;
    private final VMProgramGenerator vmProgramGenerator;
    private final BytecodeVMConfig config;
    private final GeneratedMemberNamer namer;
    private final VMObfProfile profile;
    private final SuperInstructionRegistry superInstructions;

    public CodePoolGenerator(
            String className,
            List<CompiledMethod> compiledMethods,
            VMProgramGenerator vmProgramGenerator,
            VMCodePoolGenerator vmCodePoolGenerator,
            BytecodeVMConfig config,
            boolean shuffleMethods)
    {
        this(className, compiledMethods, vmProgramGenerator, vmCodePoolGenerator, config, shuffleMethods, GeneratedMemberNamer.DISABLED, VMObfProfile.random());
    }

    public CodePoolGenerator(
            String className,
            List<CompiledMethod> compiledMethods,
            VMProgramGenerator vmProgramGenerator,
            VMCodePoolGenerator vmCodePoolGenerator,
            BytecodeVMConfig config,
            boolean shuffleMethods,
            GeneratedMemberNamer namer)
    {
        this(className, compiledMethods, vmProgramGenerator, vmCodePoolGenerator, config, shuffleMethods, namer, VMObfProfile.random());
    }

    public CodePoolGenerator(
            String className,
            List<CompiledMethod> compiledMethods,
            VMProgramGenerator vmProgramGenerator,
            VMCodePoolGenerator vmCodePoolGenerator,
            BytecodeVMConfig config,
            boolean shuffleMethods,
            GeneratedMemberNamer namer,
            VMObfProfile profile)
    {
        this(className, compiledMethods, vmProgramGenerator, vmCodePoolGenerator, config, shuffleMethods, namer, profile, new SuperInstructionRegistry(config.superInstructionMaxHandlers));
    }

    public CodePoolGenerator(
            String className,
            List<CompiledMethod> compiledMethods,
            VMProgramGenerator vmProgramGenerator,
            VMCodePoolGenerator vmCodePoolGenerator,
            BytecodeVMConfig config,
            boolean shuffleMethods,
            GeneratedMemberNamer namer,
            VMObfProfile profile,
            SuperInstructionRegistry superInstructions)
    {
        super(className);
        this.config = Objects.requireNonNull(config, "config");
        this.namer = Objects.requireNonNull(namer, "namer");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.superInstructions = Objects.requireNonNull(superInstructions, "superInstructions");
        this.vmProgramGenerator = vmProgramGenerator;
        this.layout = new CodePoolLayout(
                className,
                vmCodePoolGenerator.descriptor(),
                vmProgramGenerator.descriptor(),
                namer,
                vmCodePoolGenerator.find.name());
        if (vmCodePoolGenerator.vmProgramGenerator != vmProgramGenerator)
        {
            throw new IllegalArgumentException("VMCodePoolGenerator uses a different VMProgramGenerator");
        }
        validateUniqueCodeIds(compiledMethods);
        this.compiledMethods = List.copyOf(compiledMethods);
        SuperInstructionCombiner.prepare(this.compiledMethods, this.config, this.superInstructions);
        this.protectedMethodById = protectMethods(this.compiledMethods, this.config, this.profile, this.superInstructions);
        this.methodsByCodeIndex = createLayout(compiledMethods, shuffleMethods);
        this.methodsByOperandIndex = createLayout(compiledMethods, shuffleMethods);
        this.methodsByLayoutIndex = createLayout(compiledMethods, shuffleMethods);
        this.methodsByBlockIndex = createLayout(compiledMethods, shuffleMethods);
        this.methodsByConstantsIndex = createLayout(compiledMethods, shuffleMethods);
        this.methodsByExceptionHandlersIndex = createLayout(compiledMethods, shuffleMethods);
        this.methodsByOpcodeMapIndex = createLayout(compiledMethods, shuffleMethods);
        this.methodsByMethodKeyIndex = createLayout(compiledMethods, shuffleMethods);
        this.methodsByFeatureFlagsIndex = createLayout(compiledMethods, shuffleMethods);
        this.methodsByMaxLocalsIndex = createLayout(compiledMethods, shuffleMethods);
        this.methodsByMaxStackIndex = createLayout(compiledMethods, shuffleMethods);
        this.codeIndexById = indexByCodeId(methodsByCodeIndex);
        this.operandIndexById = indexByCodeId(methodsByOperandIndex);
        this.layoutIndexById = indexByCodeId(methodsByLayoutIndex);
        this.blockIndexById = indexByCodeId(methodsByBlockIndex);
        this.constantsIndexById = indexByCodeId(methodsByConstantsIndex);
        this.exceptionHandlersIndexById = indexByCodeId(methodsByExceptionHandlersIndex);
        this.opcodeMapIndexById = indexByCodeId(methodsByOpcodeMapIndex);
        this.methodKeyIndexById = indexByCodeId(methodsByMethodKeyIndex);
        this.featureFlagsIndexById = indexByCodeId(methodsByFeatureFlagsIndex);
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
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, layout.blockStreams.name(), layout.blockStreams.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, layout.constants.name(), layout.constants.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, layout.exceptionHandlers.name(), layout.exceptionHandlers.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, layout.opcodeMaps.name(), layout.opcodeMaps.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, layout.methodKeys.name(), layout.methodKeys.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, layout.featureFlags.name(), layout.featureFlags.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, layout.maxLocals.name(), layout.maxLocals.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, layout.maxStack.name(), layout.maxStack.descriptor()));

        MethodNode clinit = MethodUtils.newMethodNode(new Acc[]{Acc.STATIC}, "<clinit>", "()V");
        AdvIBdr clinitBuilder = new AdvIBdr(0, false);
        addClinitHelper(cn, clinitBuilder, 0, initOPCODE_STREAMS());
        addClinitHelper(cn, clinitBuilder, 1, initOPERAND_STREAMS());
        addClinitHelper(cn, clinitBuilder, 2, initLAYOUT_STREAMS());
        addClinitHelper(cn, clinitBuilder, 3, initBLOCK_STREAMS());
        addClinitHelper(cn, clinitBuilder, 4, initCONSTANTS());
        addClinitHelper(cn, clinitBuilder, 5, initEXCEPTION_HANDLERS());
        addClinitHelper(cn, clinitBuilder, 6, initOPCODE_MAPS());
        addClinitHelper(cn, clinitBuilder, 7, initMETHOD_KEYS());
        addClinitHelper(cn, clinitBuilder, 8, initFEATURE_FLAGS());
        addClinitHelper(cn, clinitBuilder, 9, initMAX_LOCALS_MAX_STACK());
        clinitBuilder.set(AdvIBdr.staticField(layout.instance), AdvIBdr.newObject(layout.owner));
        clinitBuilder.returnVoid();
        clinit.instructions.add(clinitBuilder.toInsnList());
        cn.methods.add(clinit);

        cn.methods.add(generateFind());
        cn.methods.add(genMixMethod());
        cn.methods.add(genArrayMixMethod());
        cn.methods.add(genUnpackIntsMethod());
        cn.methods.add(genUnpackStringIntsMethod());
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
        if (protectedMethodById.values().stream().anyMatch(method -> method.usesSuperInstructions))
        {
            usedOpcodes.add(Opcs.SUPER_INSTRUCTION);
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

    private void addClinitHelper(ClassNode classNode, AdvIBdr clinit, int index, InsnList body)
    {
        String name = namer.method(classNode.name, "codePoolInit$" + index, "()V");
        MethodNode helper = MethodUtils.newMethodNode(new Acc[]{Acc.PRIVATE, Acc.STATIC}, name, "()V");
        helper.instructions.add(body);
        AdvIBdr helperEnd = new AdvIBdr(0, false);
        helperEnd.returnVoid();
        helper.instructions.add(helperEnd.toInsnList());
        classNode.methods.add(helper);
        clinit.directCall(AdvIBdr.callStatic(layout.owner, name, "V"));
    }

    private MethodNode genMixMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                layout.mix.name(), // mix
                layout.mix.descriptor() // (IIII)I
        );
        AdvIBdr ib = new AdvIBdr(method, false);
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

    private MethodNode genArrayMixMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                layout.arrayMix.name(), // arrayMix
                layout.arrayMix.descriptor() // (II)I
        );
        AdvIBdr ib = new AdvIBdr(method, false);
        Local key = ib.getLocal("key", "I", 0);
        Local index = ib.getLocal("index", "I", 1);
        ib.returnValue(AdvIBdr.callStatic(
                layout.owner,
                layout.mix.name(),
                "I",
                key,
                index,
                AdvIBdr.constant(profile.saltArray),
                AdvIBdr.constant(0)));
        return method;
    }

    private MethodNode genUnpackIntsMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                layout.unpackInts.name(), // unpackInts
                layout.unpackInts.descriptor() // ([JII)[I
        );
        AdvIBdr ib = new AdvIBdr(method, false);
        Local packed = ib.getLocal("packed", "[J", 0);
        Local length = ib.getLocal("length", "I", 1);
        Local key = ib.getLocal("key", "I", 2);
        Local result = ib.getLocal("result", "[I", 3);
        Local pair = ib.getLocal("pair", "I", 4);
        Local outIndex = ib.getLocal("outIndex", "I", 5);
        Local word = ib.getLocal("word", "J", 6);

        ib.set(result, AdvIBdr.newArray("int", length));
        ib.set(pair, AdvIBdr.constant(0));
        ib.set(outIndex, AdvIBdr.constant(0));
        ib.whileLoop(
                AdvIBdr.lessThan(pair, AdvIBdr.arrayLength(packed)),
                b -> {
                    b.set(word, AdvIBdr.arrayAt(packed, pair));
                    b.setArray(
                            result,
                            outIndex,
                            AdvIBdr.bitXor(
                                    AdvIBdr.cast(AdvIBdr.unsignedShiftRight(word, AdvIBdr.constant(32)), "I"),
                                    AdvIBdr.callStatic(layout.owner, layout.arrayMix.name(), "I", key, outIndex)));
                    b.ifCondition(
                            AdvIBdr.lessThan(AdvIBdr.plus(outIndex, AdvIBdr.constant(1)), length),
                            odd -> odd.setArray(
                                    result,
                                    AdvIBdr.plus(outIndex, AdvIBdr.constant(1)),
                                    AdvIBdr.bitXor(
                                            AdvIBdr.cast(word, "I"),
                                            AdvIBdr.callStatic(
                                                    layout.owner,
                                                    layout.arrayMix.name(),
                                                    "I",
                                                    key,
                                                    AdvIBdr.plus(outIndex, AdvIBdr.constant(1))))));
                    b.increment(pair, 1);
                    b.increment(outIndex, 2);
                });
        ib.returnValue(result);
        return method;
    }

    private MethodNode genUnpackStringIntsMethod()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC},
                layout.unpackStringInts.name(),
                layout.unpackStringInts.descriptor());
        AdvIBdr ib = new AdvIBdr(method, false);
        Local chunks = ib.getLocal("chunks", "[Ljava/lang/String;", 0);
        Local length = ib.getLocal("length", "I", 1);
        Local key = ib.getLocal("key", "I", 2);
        Local result = ib.getLocal("result", "[I", 3);
        Local chunkIndex = ib.getLocal("chunkIndex", "I", 4);
        Local outputIndex = ib.getLocal("outputIndex", "I", 5);
        Local charIndex = ib.getLocal("charIndex", "I", 6);
        Local chunk = ib.getLocal("chunk", "Ljava/lang/String;", 7);
        Local encoded = ib.getLocal("encoded", "I", 8);

        ib.set(result, AdvIBdr.newArray("int", length));
        ib.set(chunkIndex, AdvIBdr.constant(0));
        ib.set(outputIndex, AdvIBdr.constant(0));
        ib.whileLoop(
                AdvIBdr.lessThan(chunkIndex, AdvIBdr.arrayLength(chunks)),
                outer -> {
                    outer.set(chunk, AdvIBdr.arrayAt(chunks, chunkIndex));
                    outer.set(charIndex, AdvIBdr.constant(0));
                    outer.whileLoop(
                            AdvIBdr.lessThan(
                                    charIndex,
                                    AdvIBdr.callVirtual(chunk, "java/lang/String", "length", "I")),
                            inner -> {
                                inner.set(encoded, AdvIBdr.bitOr(
                                        AdvIBdr.shiftLeft(
                                                AdvIBdr.callVirtual(
                                                        chunk,
                                                        "java/lang/String",
                                                        "charAt",
                                                        "C",
                                                        charIndex),
                                                AdvIBdr.constant(16)),
                                        AdvIBdr.callVirtual(
                                                chunk,
                                                "java/lang/String",
                                                "charAt",
                                                "C",
                                                AdvIBdr.plus(charIndex, AdvIBdr.constant(1)))));
                                inner.setArray(
                                        result,
                                        outputIndex,
                                        AdvIBdr.bitXor(
                                                encoded,
                                                AdvIBdr.callStatic(
                                                        layout.owner,
                                                        layout.arrayMix.name(),
                                                        "I",
                                                        key,
                                                        outputIndex)));
                                inner.increment(charIndex, 2);
                                inner.increment(outputIndex, 1);
                            });
                    outer.increment(chunkIndex, 1);
                });
        ib.returnValue(result);
        return method;
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

    private InsnList initBLOCK_STREAMS()
    {
        return initIntRows(
                "blockStreams",
                layout.blockStreams,
                methodsByBlockIndex,
                method -> protectedMethodById.get(method.codeId).blockStream);
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
        AdvIBdr ib = new AdvIBdr(0, false);
        int[] keys = new int[methodsByMethodKeyIndex.size()];
        for (int slot = 0; slot < methodsByMethodKeyIndex.size(); slot++)
        {
            keys[slot] = protectedMethodById.get(methodsByMethodKeyIndex.get(slot).codeId).methodKey;
        }
        Local methodKeys = emitIntArray(ib, "methodKeys", keys);
        ib.set(AdvIBdr.staticField(layout.methodKeys), methodKeys);
        return ib.toInsnList();
    }

    private InsnList initFEATURE_FLAGS()
    {
        AdvIBdr ib = new AdvIBdr(0, false);
        int[] flags = new int[methodsByFeatureFlagsIndex.size()];
        for (int slot = 0; slot < methodsByFeatureFlagsIndex.size(); slot++)
        {
            flags[slot] = protectedMethodById.get(methodsByFeatureFlagsIndex.get(slot).codeId).featureFlags;
        }
        Local featureFlags = emitIntArray(ib, "featureFlags", flags);
        ib.set(AdvIBdr.staticField(layout.featureFlags), featureFlags);
        return ib.toInsnList();
    }

    private InsnList initIntRows(
            String localName,
            FieldRef target,
            List<CompiledMethod> methods,
            Function<CompiledMethod, int[]> data)
    {
        AdvIBdr ib = new AdvIBdr(0, false);
        Local table = ib.var(localName, "[[I");
        ib.set(table, AdvIBdr.newMultiArray("[[I", 1, AdvIBdr.constant(methods.size())));
        for (int slot = 0; slot < methods.size(); slot++)
        {
            CompiledMethod method = methods.get(slot);
            Local row = emitIntArray(
                    ib,
                    localName + slot,
                    data.apply(method),
                    methodConfig(method).dynamicCodePoolBuild);
            ib.setArray(table, AdvIBdr.constant(slot), row);
        }
        ib.set(AdvIBdr.staticField(target), table);
        return ib.toInsnList();
    }

    private Local emitIntArray(AdvIBdr ib, String name, int[] values)
    {
        return emitIntArray(ib, name, values, config.dynamicCodePoolBuild);
    }

    private Local emitIntArray(AdvIBdr ib, String name, int[] values, boolean dynamicCodePoolBuild)
    {
        if (dynamicCodePoolBuild && values.length > 0)
        {
            return emitChunkedIntArray(ib, name, values);
        }
        if (dynamicCodePoolBuild)
        {
            int key = RandomUtils.randomInt();
            long[] packedValues = packInts(values, key, profile);
            Local packed = ib.var(name + "Packed", "[J");
            ib.set(packed, AdvIBdr.newArray("long", AdvIBdr.constant(packedValues.length)));
            for (int index = 0; index < packedValues.length; index++)
            {
                ib.setArray(packed, AdvIBdr.constant(index), AdvIBdr.constant(packedValues[index]));
            }
            Local result = ib.var(name, "[I");
            ib.set(result, AdvIBdr.callStatic(
                    layout.owner,
                    layout.unpackInts.name(),
                    "[I",
                    packed,
                    AdvIBdr.constant(values.length),
                    AdvIBdr.constant(key)));
            return result;
        }

        Local result = ib.var(name, "[I");
        ib.set(result, AdvIBdr.newArray("int", AdvIBdr.constant(values.length)));
        for (int index = 0; index < values.length; index++)
        {
            ib.setArray(result, AdvIBdr.constant(index), AdvIBdr.constant(values[index]));
        }
        return result;
    }

    private Local emitChunkedIntArray(AdvIBdr ib, String name, int[] values)
    {
        int key;
        do
        {
            key = RandomUtils.randomInt();
        }
        while (key == 0);
        String[] encodedChunks = encodeIntChunks(values, key, profile);
        Local chunks = ib.var(name + "Chunks", "[Ljava/lang/String;");
        ib.set(chunks, AdvIBdr.newArray(
                "java/lang/String",
                AdvIBdr.constant(encodedChunks.length)));
        for (int index = 0; index < encodedChunks.length; index++)
        {
            ib.setArray(
                    chunks,
                    AdvIBdr.constant(index),
                    AdvIBdr.constant(encodedChunks[index]));
        }
        Local result = ib.var(name, "[I");
        ib.set(result, AdvIBdr.callStatic(
                layout.owner,
                layout.unpackStringInts.name(),
                "[I",
                chunks,
                AdvIBdr.constant(values.length),
                AdvIBdr.constant(key)));
        return result;
    }

    private static String[] encodeIntChunks(int[] values, int key, VMObfProfile profile)
    {
        char[] encoded = new char[Math.multiplyExact(values.length, 2)];
        for (int index = 0; index < values.length; index++)
        {
            int value = values[index] ^ profile.arrayMix(key, index);
            encoded[index * 2] = (char) (value >>> 16);
            encoded[index * 2 + 1] = (char) value;
        }
        String[] chunks = new String[(encoded.length + INT_STRING_CHUNK_CHARS - 1) /
                INT_STRING_CHUNK_CHARS];
        for (int index = 0; index < chunks.length; index++)
        {
            int from = index * INT_STRING_CHUNK_CHARS;
            int length = Math.min(INT_STRING_CHUNK_CHARS, encoded.length - from);
            chunks[index] = new String(encoded, from, length);
        }
        return chunks;
    }

    private static long[] packInts(int[] values, int key, VMObfProfile profile)
    {
        long[] packed = new long[(values.length + 1) / 2];
        for (int pair = 0; pair < packed.length; pair++)
        {
            int leftIndex = pair * 2;
            int left = values[leftIndex] ^ profile.arrayMix(key, leftIndex);
            int rightIndex = leftIndex + 1;
            int right = rightIndex < values.length
                    ? values[rightIndex] ^ profile.arrayMix(key, rightIndex)
                    : RandomUtils.randomInt();
            packed[pair] = ((long) left << 32) | (right & 0xffffffffL);
        }
        return packed;
    }

    private InsnList initCONSTANTS()
    {
        AdvIBdr ib = new AdvIBdr(0, false);
        Local constantsTable = ib.var("constants", "[[Ljava/lang/Object;");
        ib.set(constantsTable, AdvIBdr.newMultiArray(layout.constants.descriptor(), 1, AdvIBdr.constant(methodsByConstantsIndex.size())));

        for (int slot = 0;
             slot < methodsByConstantsIndex.size();
             slot++)
        {
            Object[] constants = protectedMethodById.get(methodsByConstantsIndex.get(slot).codeId).constants;

            Local constantRow = ib.var("constants" + slot, "[Ljava/lang/Object;");
            ib.set(constantRow, AdvIBdr.newArray("java/lang/Object", AdvIBdr.constant(constants.length)));

            for (int constantIndex = 0;
                 constantIndex < constants.length;
                 constantIndex++)
            {
                emitConstant(ib, constantRow, constantIndex, constants[constantIndex]);
            }

            ib.setArray(constantsTable, AdvIBdr.constant(slot), constantRow);
        }

        ib.set(AdvIBdr.staticField(layout.constants), constantsTable);

        return ib.toInsnList();
    }

    private InsnList initEXCEPTION_HANDLERS()
    {
        AdvIBdr ib = new AdvIBdr(0, false);
        Local exceptionHandlers = ib.var("exceptionHandlers", "[[I");
        ib.set(exceptionHandlers, AdvIBdr.newMultiArray(layout.exceptionHandlers.descriptor(), 1, AdvIBdr.constant(methodsByExceptionHandlersIndex.size())));
        for (int slot = 0; slot < methodsByExceptionHandlersIndex.size(); slot++)
        {
            int[] handlers = protectedMethodById.get(methodsByExceptionHandlersIndex.get(slot).codeId).exceptionHandlers;

            Local handlerRow = emitIntArray(
                    ib,
                    "exceptionHandlers" + slot,
                    handlers,
                    methodConfig(methodsByExceptionHandlersIndex.get(slot)).dynamicCodePoolBuild);
            ib.setArray(exceptionHandlers, AdvIBdr.constant(slot), handlerRow);
        }

        ib.set(AdvIBdr.staticField(layout.exceptionHandlers), exceptionHandlers);
        return ib.toInsnList();
    }

    private void emitConstant(AdvIBdr ib, Expr constants, int constantIndex, Object value)
    {
        switch (value)
        {
            case null -> ib.setArray(constants, AdvIBdr.constant(constantIndex), AdvIBdr.constant(null));
            case ProtectedVMMethod.EncodedConstant encoded -> ib.setArray(constants, AdvIBdr.constant(constantIndex), encodedConstant(ib, encoded));
            case String ignored -> ib.setArray(constants, AdvIBdr.constant(constantIndex), AdvIBdr.constant(value));
            case Integer integer -> ib.setArray(constants, AdvIBdr.constant(constantIndex), boxedInteger(integer));
            case Long number -> ib.setArray(constants, AdvIBdr.constant(constantIndex), boxedLong(number));
            case Float number -> ib.setArray(constants, AdvIBdr.constant(constantIndex), boxedFloat(number));
            case Double number -> ib.setArray(constants, AdvIBdr.constant(constantIndex), boxedDouble(number));
            case Type type -> ib.setArray(constants, AdvIBdr.constant(constantIndex), typeConstant(ib, type));
            case Handle ignored -> ib.setArray(
                    constants,
                    AdvIBdr.constant(constantIndex),
                    Type.getType(Object.class),
                    b -> b.raw(raw -> raw.ldc(value)),
                    "handle(" + value + ")");
            case ConstantDynamic dynamic -> ib.setArray(
                    constants,
                    AdvIBdr.constant(constantIndex),
                    boxedDynamicType(dynamic),
                    b -> {
                        b.raw(raw -> raw.ldc(dynamic));
                        boxIfPrimitive(b.rawBuilder(), Type.getType(dynamic.getDescriptor()));
                    },
                    "constantDynamic(" + dynamic.getName() + ")");
            default -> throw new IllegalArgumentException("Unsupported constant: " + value.getClass().getName());
        }
    }

    private Expr encodedConstant(AdvIBdr ib, ProtectedVMMethod.EncodedConstant value)
    {
        Local encoded = ib.var(
                "encodedConstant" + Math.abs(Arrays.deepHashCode(value.variants())) + RandomUtils.randomInt(Integer.MAX_VALUE),
                "[[I");
        ib.set(encoded, AdvIBdr.newMultiArray(
                "[[I",
                1,
                AdvIBdr.constant(value.variants().length)));
        for (int index = 0; index < value.variants().length; index++)
        {
            ib.setArray(encoded, AdvIBdr.constant(index), emitIntArray(
                    ib,
                    "constantVariant" + RandomUtils.randomInt(Integer.MAX_VALUE),
                    value.variants()[index]));
        }
        return encoded;
    }

    private Expr typeConstant(AdvIBdr ib, Type type)
    {
        Local encoded = ib.var(
                "typeDescriptor" + Math.abs(type.getDescriptor().hashCode()) + RandomUtils.randomInt(Integer.MAX_VALUE),
                "[Ljava/lang/String;");
        ib.set(encoded, AdvIBdr.newArray("java/lang/String", AdvIBdr.constant(1)));
        ib.setArray(encoded, AdvIBdr.constant(0), AdvIBdr.constant(type.getDescriptor()));
        return encoded;
    }

    private InsnList initMAX_LOCALS_MAX_STACK()
    {
        AdvIBdr ib = new AdvIBdr(0, false);
        Local maxLocals = ib.var("maxLocals", "[I");
        ib.set(maxLocals, AdvIBdr.newArray("int", AdvIBdr.constant(methodsByMaxLocalsIndex.size())));
        for (int slot = 0; slot < methodsByMaxLocalsIndex.size(); slot++)
        {
            VMMethod vmMethod = methodsByMaxLocalsIndex.get(slot).vmMethod;
            ib.setArray(maxLocals, AdvIBdr.constant(slot), AdvIBdr.constant(vmMethod.maxLocals));
        }
        ib.set(AdvIBdr.staticField(layout.maxLocals), maxLocals);

        Local maxStack = ib.var("maxStack", "[I");
        ib.set(maxStack, AdvIBdr.newArray("int", AdvIBdr.constant(methodsByMaxStackIndex.size())));
        for (int slot = 0; slot < methodsByMaxStackIndex.size(); slot++)
        {
            VMMethod vmMethod = methodsByMaxStackIndex.get(slot).vmMethod;
            ib.setArray(maxStack, AdvIBdr.constant(slot), AdvIBdr.constant(vmMethod.maxStack));
        }
        ib.set(AdvIBdr.staticField(layout.maxStack), maxStack);
        return ib.toInsnList();
    }

    private MethodNode generateFind()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC},
                layout.find.name(),
                layout.find.descriptor());
        AdvIBdr ib = new AdvIBdr(method, false);
        Local codeIdLocal = ib.getLocal("codeId", "I", 1);

        List<CompiledMethod> methodsByCodeId = new ArrayList<>(compiledMethods);
        methodsByCodeId.sort((left, right) -> Integer.compare(left.codeId, right.codeId));
        SwitchCase[] cases = new SwitchCase[methodsByCodeId.size()];
        for (int index = 0; index < methodsByCodeId.size(); index++)
        {
            int codeId = methodsByCodeId.get(index).codeId;
            cases[index] = AdvIBdr.switchCase(codeId, b -> b.returnValue(programFor(codeId)));
        }

        ib.switchLookup(
                codeIdLocal,
                b -> b.returnValue(AdvIBdr.nullValue(vmProgramGenerator.className())),
                cases);
        return method;
    }

    private Expr programFor(int codeId)
    {
        return AdvIBdr.newObject(
                vmProgramGenerator.layout.owner,
                opcodeStreamRow(codeId),
                operandStreamRow(codeId),
                layoutStreamRow(codeId),
                blockStreamRow(codeId),
                constantRow(codeId),
                exceptionHandlerRow(codeId),
                opcodeMapRow(codeId),
                methodKeyValue(codeId),
                featureFlagsValue(codeId),
                maxLocalsValue(codeId),
                maxStackValue(codeId));
    }

    private Expr opcodeStreamRow(int codeId)
    {
        return AdvIBdr.arrayAt(
                AdvIBdr.staticField(layout.opcodeStreams),
                AdvIBdr.constant(codeIndexById.get(codeId)));
    }

    private Expr operandStreamRow(int codeId)
    {
        return AdvIBdr.arrayAt(
                AdvIBdr.staticField(layout.operandStreams),
                AdvIBdr.constant(operandIndexById.get(codeId)));
    }

    private Expr layoutStreamRow(int codeId)
    {
        return AdvIBdr.arrayAt(
                AdvIBdr.staticField(layout.layoutStreams),
                AdvIBdr.constant(layoutIndexById.get(codeId)));
    }

    private Expr blockStreamRow(int codeId)
    {
        return AdvIBdr.arrayAt(
                AdvIBdr.staticField(layout.blockStreams),
                AdvIBdr.constant(blockIndexById.get(codeId)));
    }

    private Expr constantRow(int codeId)
    {
        return AdvIBdr.arrayAt(
                AdvIBdr.staticField(layout.constants),
                AdvIBdr.constant(constantsIndexById.get(codeId)));
    }

    private Expr exceptionHandlerRow(int codeId)
    {
        return AdvIBdr.arrayAt(
                AdvIBdr.staticField(layout.exceptionHandlers),
                AdvIBdr.constant(exceptionHandlersIndexById.get(codeId)));
    }

    private Expr opcodeMapRow(int codeId)
    {
        return AdvIBdr.arrayAt(
                AdvIBdr.staticField(layout.opcodeMaps),
                AdvIBdr.constant(opcodeMapIndexById.get(codeId)));
    }

    private Expr methodKeyValue(int codeId)
    {
        return AdvIBdr.arrayAt(
                AdvIBdr.staticField(layout.methodKeys),
                AdvIBdr.constant(methodKeyIndexById.get(codeId)));
    }

    private Expr featureFlagsValue(int codeId)
    {
        return AdvIBdr.arrayAt(
                AdvIBdr.staticField(layout.featureFlags),
                AdvIBdr.constant(featureFlagsIndexById.get(codeId)));
    }

    private Expr maxLocalsValue(int codeId)
    {
        return AdvIBdr.arrayAt(
                AdvIBdr.staticField(layout.maxLocals),
                AdvIBdr.constant(maxLocalsIndexById.get(codeId)));
    }

    private Expr maxStackValue(int codeId)
    {
        return AdvIBdr.arrayAt(
                AdvIBdr.staticField(layout.maxStack),
                AdvIBdr.constant(maxStackIndexById.get(codeId)));
    }

    private static Expr boxedInteger(Integer value)
    {
        return AdvIBdr.callStatic(
                "java/lang/Integer",
                "valueOf",
                "java/lang/Integer",
                AdvIBdr.constant(value));
    }

    private static Expr boxedLong(Long value)
    {
        return AdvIBdr.callStatic(
                "java/lang/Long",
                "valueOf",
                "java/lang/Long",
                AdvIBdr.constant(value));
    }

    private static Expr boxedFloat(Float value)
    {
        return AdvIBdr.callStatic(
                "java/lang/Float",
                "valueOf",
                "java/lang/Float",
                AdvIBdr.constant(value));
    }

    private static Expr boxedDouble(Double value)
    {
        return AdvIBdr.callStatic(
                "java/lang/Double",
                "valueOf",
                "java/lang/Double",
                AdvIBdr.constant(value));
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
            BytecodeVMConfig config,
            VMObfProfile profile,
            SuperInstructionRegistry superInstructions)
    {
        Map<Integer, ProtectedVMMethod> protectedMethods = new HashMap<>();
        for (CompiledMethod method : methods)
        {
            protectedMethods.put(method.codeId, ProtectedVMMethod.from(
                    method,
                    method.config == null ? config : method.config,
                    profile,
                    superInstructions));
        }
        return Map.copyOf(protectedMethods);
    }

    private BytecodeVMConfig methodConfig(CompiledMethod method)
    {
        return method.config == null ? config : method.config;
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

    public int getMaxGeneratedMethodSize()
    {
        int maximum = 0;
        for (MethodNode method : classNode.methods)
        {
            maximum = Math.max(maximum, FileUtils.estimateMaxSize(method));
        }
        return maximum;
    }

    public Set<Integer> registeredCodeIds()
    {
        return Collections.unmodifiableSet(new HashSet<>(protectedMethodById.keySet()));
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
