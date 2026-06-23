package nhcm.bytecodevm.Generator.Virtualization;

import lombok.Getter;
import nhcm.bytecodevm.Data.CompiledMethod;
import nhcm.bytecodevm.Data.VMInsn.VMInstruction;
import nhcm.bytecodevm.Data.VMInsn.VMMethod;
import nhcm.bytecodevm.Enums.Acc;
import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Generator.Abstract.ClassObj;
import nhcm.bytecodevm.Generator.GlobalTool.VMCodePoolGenerator;
import nhcm.bytecodevm.Generator.GlobalTool.VMProgramGenerator;
import nhcm.bytecodevm.Utils.*;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

public class CodePoolGenerator extends ClassObj
{
    @Getter
    public final ClassNode classNode;

    private final List<CompiledMethod> compiledMethods;
    private final List<CompiledMethod> methodsByCodeIndex;
    private final List<CompiledMethod> methodsByConstantsIndex;
    private final List<CompiledMethod> methodsByExceptionHandlersIndex;
    private final List<CompiledMethod> methodsByMaxLocalsIndex;
    private final List<CompiledMethod> methodsByMaxStackIndex;
    private final Map<Integer, Integer> codeIndexById;
    private final Map<Integer, Integer> constantsIndexById;
    private final Map<Integer, Integer> exceptionHandlersIndexById;
    private final Map<Integer, Integer> maxLocalsIndexById;
    private final Map<Integer, Integer> maxStackIndexById;
    private final VMProgramGenerator vmProgramGenerator;
    private final VMCodePoolGenerator vmCodePoolGenerator;

    public CodePoolGenerator(String className, List<CompiledMethod> compiledMethods, VMProgramGenerator vmProgramGenerator, VMCodePoolGenerator vmCodePoolGenerator)
    {
        this(className, compiledMethods, vmProgramGenerator, vmCodePoolGenerator, true);
    }

    public CodePoolGenerator(String className, List<CompiledMethod> compiledMethods, VMProgramGenerator vmProgramGenerator, VMCodePoolGenerator vmCodePoolGenerator, boolean shuffleMethods)
    {
        super(className);
        this.vmProgramGenerator = vmProgramGenerator;
        this.vmCodePoolGenerator = vmCodePoolGenerator;
        if (vmCodePoolGenerator.vmProgramGenerator != vmProgramGenerator)
        {
            throw new IllegalArgumentException("VMCodePoolGenerator uses a different VMProgramGenerator");
        }
        validateUniqueCodeIds(compiledMethods);
        this.compiledMethods = List.copyOf(compiledMethods);
        this.methodsByCodeIndex = createLayout(compiledMethods, shuffleMethods);
        this.methodsByConstantsIndex = createLayout(compiledMethods, shuffleMethods);
        this.methodsByExceptionHandlersIndex = createLayout(compiledMethods, shuffleMethods);
        this.methodsByMaxLocalsIndex = createLayout(compiledMethods, shuffleMethods);
        this.methodsByMaxStackIndex = createLayout(compiledMethods, shuffleMethods);
        this.codeIndexById = indexByCodeId(methodsByCodeIndex);
        this.constantsIndexById = indexByCodeId(methodsByConstantsIndex);
        this.exceptionHandlersIndexById = indexByCodeId(methodsByExceptionHandlersIndex);
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
                "INSTANCE",
                vmCodePoolGenerator.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, "CODES", "[[I"));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, "CONSTANTS", "[[Ljava/lang/Object;"));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, "EXCEPTION_HANDLERS", "[[I"));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, "MAX_LOCALS", "[I"));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, "MAX_STACK", "[I"));

        MethodNode clinit = MethodUtils.newMethodNode(new Acc[]{Acc.STATIC}, "<clinit>", "()V");
        clinit.instructions.add(initCODES());
        clinit.instructions.add(initCONSTANTS());
        clinit.instructions.add(initEXCEPTION_HANDLERS());
        clinit.instructions.add(initMAX_LOCALS_MAX_STACK());
        InsnBuilder ib = new InsnBuilder();
        ib.new_(className);
        ib.dup();
        ib.invokeSpecial(className, "<init>", "()V");
        ib.putStatic(className, "INSTANCE", vmCodePoolGenerator.descriptor());
        ib._return();
        clinit.instructions.add(ib.toInsnList());
        cn.methods.add(clinit);

        cn.methods.add(generateFind());
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

    private InsnList initCODES()
    {
        InsnBuilder ib = new InsnBuilder();
        ib.pushInt(methodsByCodeIndex.size());
        ib.multiANewArray("[[I", 1);
        for (int slot = 0; slot < methodsByCodeIndex.size(); slot++)
        {
            VMMethod vmMethod = methodsByCodeIndex.get(slot).vmMethod;
            int[] codes = vmMethod.code;

            ib.dup();
            ib.pushInt(slot);

            ib.pushInt(codes.length);
            ib.newArray(Opcodes.T_INT);

            for (int codeIndex = 0; codeIndex < codes.length; codeIndex++)
            {
                ib.dup();
                ib.pushInt(codeIndex);
                ib.pushInt(codes[codeIndex]);
                ib.iastore();
            }

            ib.aastore();
        }

        ib.putStatic(className(), "CODES", "[[I");
        return ib.toInsnList();
    }

    private InsnList initCONSTANTS()
    {
        InsnBuilder ib = new InsnBuilder();

        ib.pushInt(methodsByConstantsIndex.size());
        ib.multiANewArray("[[Ljava/lang/Object;", 1);

        for (int slot = 0;
             slot < methodsByConstantsIndex.size();
             slot++)
        {
            Object[] constants =
                    methodsByConstantsIndex.get(slot).vmMethod.constants;

            ib.dup();
            ib.pushInt(slot);

            ib.pushInt(constants.length);
            ib.aneArray("java/lang/Object");

            for (int constantIndex = 0;
                 constantIndex < constants.length;
                 constantIndex++)
            {
                ib.dup();
                ib.pushInt(constantIndex);

                emitConstant(ib, constants[constantIndex]);

                ib.aastore();
            }

            ib.aastore();
        }

        ib.putStatic(
                className(),
                "CONSTANTS",
                "[[Ljava/lang/Object;"
        );

        return ib.toInsnList();
    }

    private InsnList initEXCEPTION_HANDLERS()
    {
        InsnBuilder ib = new InsnBuilder();
        ib.pushInt(methodsByExceptionHandlersIndex.size());
        ib.multiANewArray("[[I", 1);
        for (int slot = 0; slot < methodsByExceptionHandlersIndex.size(); slot++)
        {
            int[] handlers = methodsByExceptionHandlersIndex.get(slot).vmMethod.exceptionHandlers;

            ib.dup();
            ib.pushInt(slot);

            ib.pushInt(handlers.length);
            ib.newArray(Opcodes.T_INT);

            for (int handlerIndex = 0; handlerIndex < handlers.length; handlerIndex++)
            {
                ib.dup();
                ib.pushInt(handlerIndex);
                ib.pushInt(handlers[handlerIndex]);
                ib.iastore();
            }

            ib.aastore();
        }

        ib.putStatic(className(), "EXCEPTION_HANDLERS", "[[I");
        return ib.toInsnList();
    }

    private void emitConstant(InsnBuilder ib, Object value)
    {
        switch (value)
        {
            case null -> ib.aconstNull();
            case String ignored -> ib.ldc(value);
            case Integer integer ->
            {
                ib.pushInt(integer);
                ib.invokeStatic("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;");
            }
            case Long number ->
            {
                ib.pushLong(number);
                ib.invokeStatic("java/lang/Long", "valueOf", "(J)Ljava/lang/Long;");
            }
            case Float number ->
            {
                ib.pushFloat(number);
                ib.invokeStatic("java/lang/Float", "valueOf", "(F)Ljava/lang/Float;");
            }
            case Double number ->
            {
                ib.pushDouble(number);
                ib.invokeStatic("java/lang/Double", "valueOf", "(D)Ljava/lang/Double;");
            }
            case Type type -> emitTypeConstant(ib, type);
            case Handle ignored -> ib.ldc(value);
            case ConstantDynamic dynamic ->
            {
                ib.ldc(dynamic);
                boxIfPrimitive(ib, Type.getType(dynamic.getDescriptor()));
            }
            default -> throw new IllegalArgumentException("Unsupported constant: " + value.getClass().getName());
        }
    }

    private void emitTypeConstant(InsnBuilder ib, Type type)
    {
        ib.iconst2();
        ib.aneArray("java/lang/Object");
        ib.dup();
        ib.iconst0();
        ib.ldc("__BytecodeVM_TYPE__");
        ib.aastore();
        ib.dup();
        ib.iconst1();
        ib.ldc(type.getDescriptor());
        ib.aastore();
    }

    private InsnList initMAX_LOCALS_MAX_STACK()
    {
        InsnBuilder ib = new InsnBuilder();
        ib.pushInt(methodsByMaxLocalsIndex.size());
        ib.newArray(Opcodes.T_INT);
        for (int slot = 0; slot < methodsByMaxLocalsIndex.size(); slot++)
        {
            VMMethod vmMethod = methodsByMaxLocalsIndex.get(slot).vmMethod;
            ib.dup();
            ib.pushInt(slot);
            ib.pushInt(vmMethod.maxLocals);
            ib.iastore();
        }
        ib.putStatic(className(), "MAX_LOCALS", "[I");
        ib.pushInt(methodsByMaxStackIndex.size());
        ib.newArray(Opcodes.T_INT);
        for (int slot = 0; slot < methodsByMaxStackIndex.size(); slot++)
        {
            VMMethod vmMethod = methodsByMaxStackIndex.get(slot).vmMethod;
            ib.dup();
            ib.pushInt(slot);
            ib.pushInt(vmMethod.maxStack);
            ib.iastore();
        }
        ib.putStatic(className(), "MAX_STACK", "[I");
        return ib.toInsnList();
    }

    private MethodNode generateFind()
    {
        MethodNode method = MethodUtils.newMethodNode(
                new Acc[]{Acc.PUBLIC},
                "find",
                "(I)" + vmProgramGenerator.descriptor());
        InsnBuilder ib = new InsnBuilder(method.instructions);

        List<CompiledMethod> methodsByCodeId = new ArrayList<>(compiledMethods);
        methodsByCodeId.sort((left, right) -> Integer.compare(left.codeId, right.codeId));
        int[] codeIds = new int[methodsByCodeId.size()];
        LabelNode[] targets = new LabelNode[methodsByCodeId.size()];
        for (int index = 0; index < methodsByCodeId.size(); index++)
        {
            codeIds[index] = methodsByCodeId.get(index).codeId;
            targets[index] = new LabelNode();
        }

        LabelNode unknownCodeId = new LabelNode();
        ib.iload(1);
        ib.lookupSwitch(unknownCodeId, codeIds, targets);

        for (int index = 0; index < targets.length; index++)
        {
            int codeId = methodsByCodeId.get(index).codeId;
            ib.label(targets[index]);
            ib.new_(vmProgramGenerator.className());
            ib.dup();
            ib.getStatic(className(), "CODES", "[[I");
            ib.pushInt(codeIndexById.get(codeId));
            ib.aaload();
            ib.getStatic(className(), "CONSTANTS", "[[Ljava/lang/Object;");
            ib.pushInt(constantsIndexById.get(codeId));
            ib.aaload();
            ib.getStatic(className(), "EXCEPTION_HANDLERS", "[[I");
            ib.pushInt(exceptionHandlersIndexById.get(codeId));
            ib.aaload();
            ib.getStatic(className(), "MAX_LOCALS", "[I");
            ib.pushInt(maxLocalsIndexById.get(codeId));
            ib.iaload();
            ib.getStatic(className(), "MAX_STACK", "[I");
            ib.pushInt(maxStackIndexById.get(codeId));
            ib.iaload();
            ib.invokeSpecial(vmProgramGenerator.className(), "<init>", vmProgramGenerator.constructorDescriptor());
            ib.areturn();
        }

        ib.label(unknownCodeId);
        ib.aconstNull();
        ib.areturn();
        return method;
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
