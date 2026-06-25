package nhcm.bytecodevm.Generator.Virtualization;

import lombok.Getter;
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
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

public class CodePoolGenerator extends ClassObj
{
    @Getter
    public final ClassNode classNode;
    @Getter
    public final CodePoolLayout layout;

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

    public CodePoolGenerator(String className, List<CompiledMethod> compiledMethods, VMProgramGenerator vmProgramGenerator, VMCodePoolGenerator vmCodePoolGenerator)
    {
        this(className, compiledMethods, vmProgramGenerator, vmCodePoolGenerator, true);
    }

    public CodePoolGenerator(String className, List<CompiledMethod> compiledMethods, VMProgramGenerator vmProgramGenerator, VMCodePoolGenerator vmCodePoolGenerator, boolean shuffleMethods)
    {
        super(className);
        this.vmProgramGenerator = vmProgramGenerator;
        this.layout = new CodePoolLayout(className, vmCodePoolGenerator.descriptor(), vmProgramGenerator.descriptor());
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
                layout.instance.name(),
                layout.instance.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, layout.codes.name(), layout.codes.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, layout.constants.name(), layout.constants.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, layout.exceptionHandlers.name(), layout.exceptionHandlers.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, layout.maxLocals.name(), layout.maxLocals.descriptor()));
        cn.fields.add(FieldUtils.newFieldNode(new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, layout.maxStack.name(), layout.maxStack.descriptor()));

        MethodNode clinit = MethodUtils.newMethodNode(new Acc[]{Acc.STATIC}, "<clinit>", "()V");
        clinit.instructions.add(initCODES());
        clinit.instructions.add(initCONSTANTS());
        clinit.instructions.add(initEXCEPTION_HANDLERS());
        clinit.instructions.add(initMAX_LOCALS_MAX_STACK());
        AdvInsnBuilder ib = new AdvInsnBuilder(0);
        ib.set(AdvInsnBuilder.staticField(layout.instance), AdvInsnBuilder.newObject(layout.owner));
        ib.returnVoid();
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
        AdvInsnBuilder ib = new AdvInsnBuilder(0);
        Local codesTable = ib.var("codes", "[[I");
        ib.set(codesTable, AdvInsnBuilder.newMultiArray(layout.codes.descriptor(), 1, AdvInsnBuilder.constant(methodsByCodeIndex.size())));
        for (int slot = 0; slot < methodsByCodeIndex.size(); slot++)
        {
            VMMethod vmMethod = methodsByCodeIndex.get(slot).vmMethod;
            int[] codes = vmMethod.code;

            Local code = ib.var("code" + slot, "[I");
            ib.set(code, AdvInsnBuilder.newArray("int", AdvInsnBuilder.constant(codes.length)));
            for (int codeIndex = 0; codeIndex < codes.length; codeIndex++)
            {
                ib.setArray(code, AdvInsnBuilder.constant(codeIndex), AdvInsnBuilder.constant(codes[codeIndex]));
            }
            ib.setArray(codesTable, AdvInsnBuilder.constant(slot), code);
        }

        ib.set(AdvInsnBuilder.staticField(layout.codes), codesTable);
        return ib.toInsnList();
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
            Object[] constants =
                    methodsByConstantsIndex.get(slot).vmMethod.constants;

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
            int[] handlers = methodsByExceptionHandlersIndex.get(slot).vmMethod.exceptionHandlers;

            Local handlerRow = ib.var("exceptionHandlers" + slot, "[I");
            ib.set(handlerRow, AdvInsnBuilder.newArray("int", AdvInsnBuilder.constant(handlers.length)));
            for (int handlerIndex = 0; handlerIndex < handlers.length; handlerIndex++)
            {
                ib.setArray(handlerRow, AdvInsnBuilder.constant(handlerIndex), AdvInsnBuilder.constant(handlers[handlerIndex]));
            }
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
                codeRow(codeId),
                constantRow(codeId),
                exceptionHandlerRow(codeId),
                maxLocalsValue(codeId),
                maxStackValue(codeId));
    }

    private Expr codeRow(int codeId)
    {
        return AdvInsnBuilder.arrayAt(
                AdvInsnBuilder.staticField(layout.codes),
                AdvInsnBuilder.constant(codeIndexById.get(codeId)));
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
