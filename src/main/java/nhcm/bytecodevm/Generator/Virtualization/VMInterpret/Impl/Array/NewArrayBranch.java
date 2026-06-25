package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Array;

import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.AdvInsn.Expr;
import nhcm.bytecodevm.AdvInsn.Local;
import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.NumericType;
import org.objectweb.asm.Opcodes;

import java.util.Set;

public class NewArrayBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.NEW_ARRAY.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        switch (opcode)
        {
            case NEWARRAY -> generatePrimitiveArray(ib, context);
            case ANEWARRAY -> generateReferenceArray(ib, context);
            case MULTIANEWARRAY -> generateMultiArray(ib, context);
            default -> throw new IllegalArgumentException("Unsupported new array opcode: " + opcode);
        }
    }

    private static void generatePrimitiveArray(AdvInsnBuilder ib, InterpretContext context)
    {
        Local atype = context.intLocal("arrayAType", InterpretContext.ARRAY_ATYPE);
        Local component = context.local("arrayComponent", "java/lang/Class", InterpretContext.ARRAY_COMPONENT);

        context.nextToken(ib, atype);
        ib.switchLookup(
                atype,
                b -> b.throwValue(AdvInsnBuilder.newObject(
                        "java/lang/IllegalArgumentException",
                        AdvInsnBuilder.constant("Unknown NEWARRAY atype"))),
                AdvInsnBuilder.switchCase(Opcodes.T_BOOLEAN, b -> b.set(component, primitiveType("java/lang/Boolean"))),
                AdvInsnBuilder.switchCase(Opcodes.T_CHAR, b -> b.set(component, primitiveType("java/lang/Character"))),
                AdvInsnBuilder.switchCase(Opcodes.T_FLOAT, b -> b.set(component, primitiveType("java/lang/Float"))),
                AdvInsnBuilder.switchCase(Opcodes.T_DOUBLE, b -> b.set(component, primitiveType("java/lang/Double"))),
                AdvInsnBuilder.switchCase(Opcodes.T_BYTE, b -> b.set(component, primitiveType("java/lang/Byte"))),
                AdvInsnBuilder.switchCase(Opcodes.T_SHORT, b -> b.set(component, primitiveType("java/lang/Short"))),
                AdvInsnBuilder.switchCase(Opcodes.T_INT, b -> b.set(component, primitiveType("java/lang/Integer"))),
                AdvInsnBuilder.switchCase(Opcodes.T_LONG, b -> b.set(component, primitiveType("java/lang/Long"))));

        createSingleArray(ib, context, component);
    }

    private static void generateReferenceArray(AdvInsnBuilder ib, InterpretContext context)
    {
        Local classIndex = context.intLocal("arrayClassIndex", InterpretContext.ARRAY_ATYPE);
        Local component = context.local("arrayComponent", "java/lang/Class", InterpretContext.ARRAY_COMPONENT);

        context.nextToken(ib, classIndex);
        ib.set(component, context.loadClass(context.constantString(classIndex)));
        createSingleArray(ib, context, component);
    }

    private static void generateMultiArray(AdvInsnBuilder ib, InterpretContext context)
    {
        Local classIndex = context.intLocal("arrayClassIndex", InterpretContext.ARRAY_ATYPE);
        Local component = context.local("arrayComponent", "java/lang/Class", InterpretContext.ARRAY_COMPONENT);
        Local dimensions = context.intLocal("arrayDimensions", InterpretContext.ARRAY_DIMENSIONS);
        Local lengths = context.local("arrayLengths", "[I", InterpretContext.ARRAY_LENGTHS);
        Local index = context.intLocal("arrayIndex", InterpretContext.ARRAY_INDEX);

        context.nextToken(ib, classIndex);
        ib.set(component, context.loadClass(context.constantString(classIndex)));

        context.nextToken(ib, dimensions);
        ib.set(lengths, AdvInsnBuilder.newArray("I", dimensions));
        ib.set(index, AdvInsnBuilder.minus(dimensions, AdvInsnBuilder.constant(1)));
        ib.whileLoop(
                AdvInsnBuilder.greaterOrEqual(index, AdvInsnBuilder.constant(0)),
                b -> {
                    popInt(b, context, InterpretContext.RIGHT_VALUE);
                    b.setArray(lengths, index, context.rightValue(NumericType.INT));
                    b.increment(index, -1);
                });

        ib.set(index, AdvInsnBuilder.constant(0));
        ib.whileLoop(
                AdvInsnBuilder.lessThan(index, dimensions),
                b -> {
                    b.set(component, AdvInsnBuilder.callVirtual(
                            component,
                            "java/lang/Class",
                            "getComponentType",
                            "java/lang/Class"));
                    b.increment(index, 1);
                });

        pushObject(ib, context, AdvInsnBuilder.callStatic(
                "java/lang/reflect/Array",
                "newInstance",
                "java/lang/Object",
                component,
                lengths));
    }

    private static void createSingleArray(AdvInsnBuilder ib, InterpretContext context, Expr component)
    {
        popInt(ib, context, InterpretContext.RIGHT_VALUE);
        pushObject(ib, context, AdvInsnBuilder.callStatic(
                "java/lang/reflect/Array",
                "newInstance",
                "java/lang/Object",
                component,
                context.rightValue(NumericType.INT)));
    }

    private static Expr primitiveType(String boxedOwner)
    {
        return AdvInsnBuilder.staticField(boxedOwner, "TYPE", "java/lang/Class");
    }
}
