package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Array;

import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;

import java.util.Set;

public class NewArrayBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.NEW_ARRAY.getOpcodes();
    }

    @Override
    public InsnList generate(InterpretContext context, Opcs opcode)
    {
        InsnBuilder ib = new InsnBuilder();
        switch (opcode)
        {
            case NEWARRAY -> generatePrimitiveArray(ib, context);
            case ANEWARRAY -> generateReferenceArray(ib, context);
            case MULTIANEWARRAY -> generateMultiArray(ib, context);
            default -> throw new IllegalArgumentException("Unsupported new array opcode: " + opcode);
        }
        return ib.toInsnList();
    }

    private static void generatePrimitiveArray(InsnBuilder ib, InterpretContext context)
    {
        LabelNode booleanType = new LabelNode();
        LabelNode charType = new LabelNode();
        LabelNode floatType = new LabelNode();
        LabelNode doubleType = new LabelNode();
        LabelNode byteType = new LabelNode();
        LabelNode shortType = new LabelNode();
        LabelNode intType = new LabelNode();
        LabelNode longType = new LabelNode();
        LabelNode create = new LabelNode();

        context.nextToken(ib);
        ib.istore(InterpretContext.ARRAY_ATYPE);

        ib.iload(InterpretContext.ARRAY_ATYPE);
        ib.pushInt(Opcodes.T_BOOLEAN);
        ib.ifIcmpEq(booleanType);
        ib.iload(InterpretContext.ARRAY_ATYPE);
        ib.pushInt(Opcodes.T_CHAR);
        ib.ifIcmpEq(charType);
        ib.iload(InterpretContext.ARRAY_ATYPE);
        ib.pushInt(Opcodes.T_FLOAT);
        ib.ifIcmpEq(floatType);
        ib.iload(InterpretContext.ARRAY_ATYPE);
        ib.pushInt(Opcodes.T_DOUBLE);
        ib.ifIcmpEq(doubleType);
        ib.iload(InterpretContext.ARRAY_ATYPE);
        ib.pushInt(Opcodes.T_BYTE);
        ib.ifIcmpEq(byteType);
        ib.iload(InterpretContext.ARRAY_ATYPE);
        ib.pushInt(Opcodes.T_SHORT);
        ib.ifIcmpEq(shortType);
        ib.iload(InterpretContext.ARRAY_ATYPE);
        ib.pushInt(Opcodes.T_INT);
        ib.ifIcmpEq(intType);
        ib.iload(InterpretContext.ARRAY_ATYPE);
        ib.pushInt(Opcodes.T_LONG);
        ib.ifIcmpEq(longType);

        ib.new_("java/lang/IllegalArgumentException");
        ib.dup();
        ib.ldc("Unknown NEWARRAY atype");
        ib.invokeSpecial("java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V");
        ib.athrow();

        emitPrimitiveClass(ib, booleanType, "java/lang/Boolean", create);
        emitPrimitiveClass(ib, charType, "java/lang/Character", create);
        emitPrimitiveClass(ib, floatType, "java/lang/Float", create);
        emitPrimitiveClass(ib, doubleType, "java/lang/Double", create);
        emitPrimitiveClass(ib, byteType, "java/lang/Byte", create);
        emitPrimitiveClass(ib, shortType, "java/lang/Short", create);
        emitPrimitiveClass(ib, intType, "java/lang/Integer", create);
        emitPrimitiveClass(ib, longType, "java/lang/Long", create);

        ib.label(create);
        createSingleArray(ib, context);
    }

    private static void generateReferenceArray(InsnBuilder ib, InterpretContext context)
    {
        readClass(ib, context);
        createSingleArray(ib, context);
    }

    private static void generateMultiArray(InsnBuilder ib, InterpretContext context)
    {
        readClass(ib, context);
        ib.astore(InterpretContext.ARRAY_COMPONENT);

        context.nextToken(ib);
        ib.istore(InterpretContext.ARRAY_DIMENSIONS);

        ib.iload(InterpretContext.ARRAY_DIMENSIONS);
        ib.newArray(Opcodes.T_INT);
        ib.astore(InterpretContext.ARRAY_LENGTHS);

        ib.iload(InterpretContext.ARRAY_DIMENSIONS);
        ib.iconst1();
        ib.isub();
        ib.istore(InterpretContext.ARRAY_INDEX);

        LabelNode readLengths = new LabelNode();
        LabelNode lengthsDone = new LabelNode();
        ib.label(readLengths);
        ib.iload(InterpretContext.ARRAY_INDEX);
        ib.iflt(lengthsDone);
        ib.aload(InterpretContext.ARRAY_LENGTHS);
        ib.iload(InterpretContext.ARRAY_INDEX);
        popInt(ib, context);
        ib.iastore();
        ib.iinc(InterpretContext.ARRAY_INDEX, -1);
        ib.goto_(readLengths);
        ib.label(lengthsDone);

        ib.iconst0();
        ib.istore(InterpretContext.ARRAY_INDEX);

        LabelNode peelComponent = new LabelNode();
        LabelNode componentDone = new LabelNode();
        ib.label(peelComponent);
        ib.iload(InterpretContext.ARRAY_INDEX);
        ib.iload(InterpretContext.ARRAY_DIMENSIONS);
        ib.ifIcmpGe(componentDone);
        ib.aload(InterpretContext.ARRAY_COMPONENT);
        ib.invokeVirtual("java/lang/Class", "getComponentType", "()Ljava/lang/Class;");
        ib.astore(InterpretContext.ARRAY_COMPONENT);
        ib.iinc(InterpretContext.ARRAY_INDEX, 1);
        ib.goto_(peelComponent);
        ib.label(componentDone);

        ib.aload(InterpretContext.ARRAY_COMPONENT);
        ib.aload(InterpretContext.ARRAY_LENGTHS);
        ib.invokeStatic("java/lang/reflect/Array", "newInstance", "(Ljava/lang/Class;[I)Ljava/lang/Object;");
        pushObject(ib, context);
    }

    private static void createSingleArray(InsnBuilder ib, InterpretContext context)
    {
        popInt(ib, context);
        ib.invokeStatic("java/lang/reflect/Array", "newInstance", "(Ljava/lang/Class;I)Ljava/lang/Object;");
        pushObject(ib, context);
    }

    private static void readClass(InsnBuilder ib, InterpretContext context)
    {
        ib.aload(InterpretContext.CONSTANTS);
        context.nextToken(ib);
        context.vm.constantString.invokeStatic(ib);
        context.vm.loadOwner.invokeStatic(ib);
    }

    private static void emitPrimitiveClass(InsnBuilder ib, LabelNode label, String boxedOwner, LabelNode create)
    {
        ib.label(label);
        ib.getStatic(boxedOwner, "TYPE", "Ljava/lang/Class;");
        ib.goto_(create);
    }
}
