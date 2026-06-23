package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Field;

import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;

import java.util.Set;

public class ReadFieldBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.READ_FIELD.getOpcodes();
    }

    @Override
    public InsnList generate(InterpretContext context, Opcs opcode)
    {
        InsnBuilder ib = new InsnBuilder();

        readConstantString(ib, context);
        ib.astore(InterpretContext.FIELD_OWNER);
        readConstantString(ib, context);
        ib.astore(InterpretContext.FIELD_NAME);
        readConstantString(ib, context);
        ib.astore(InterpretContext.FIELD_DESCRIPTOR);

        ib.aload(InterpretContext.FIELD_OWNER);
        ib.aload(InterpretContext.FIELD_NAME);
        ib.aload(InterpretContext.FIELD_DESCRIPTOR);

        if (opcode == Opcs.GETSTATIC)
        {
            ib.iconst1();
            ib.aconstNull();
        }
        else
        {
            ib.iconst0();
            popObject(ib, context);
        }

        ib.invokeStatic(
                context.vmClassName, "getField",
                "(Ljava/lang/String;" +
                "Ljava/lang/String;" +
                "Ljava/lang/String;" +
                "ZLjava/lang/Object;)" +
                "Ljava/lang/Object;");

        ib.astore(InterpretContext.FIELD_RESULT);

        LabelNode category2 = new LabelNode();
        LabelNode done = new LabelNode();
        ib.aload(InterpretContext.FIELD_DESCRIPTOR);
        ib.iconst0();
        ib.invokeVirtual("java/lang/String", "charAt", "(I)C");
        ib.bipush('J');
        ib.ifIcmpEq(category2);
        ib.aload(InterpretContext.FIELD_DESCRIPTOR);
        ib.iconst0();
        ib.invokeVirtual("java/lang/String", "charAt", "(I)C");
        ib.bipush('D');
        ib.ifIcmpEq(category2);

        pushObject(ib, context, InterpretContext.FIELD_RESULT);
        ib.goto_(done);

        ib.label(category2);
        ib.aload(InterpretContext.FIELD_RESULT);
        pushObjectWithWidth(ib, context, 2);
        ib.label(done);
        return ib.toInsnList();
    }

    private static void readConstantString(
            InsnBuilder ib,
            InterpretContext context)
    {
        ib.aload(InterpretContext.CONSTANTS);
        context.nextToken(ib);
        ib.invokeStatic(
                context.vmClassName,
                "constantString",
                "([Ljava/lang/Object;I)Ljava/lang/String;");
    }
}
