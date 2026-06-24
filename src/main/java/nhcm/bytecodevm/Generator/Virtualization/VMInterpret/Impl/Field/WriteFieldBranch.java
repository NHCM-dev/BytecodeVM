package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Field;

import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.tree.InsnList;

import java.util.Set;

public class WriteFieldBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.WRITE_FIELD.getOpcodes();
    }

    @Override
    public InsnList generate(InterpretContext context, Opcs opcode)
    {
        InsnBuilder ib = new InsnBuilder();

        // value 一定处于 VM 栈顶
        popObject(ib, context, InterpretContext.FIELD_VALUE);

        if (opcode == Opcs.PUTSTATIC)
        {
            ib.aconstNull();
            ib.astore(InterpretContext.FIELD_RECEIVER);
        }
        else
        {
            popObject(
                    ib,
                    context,
                    InterpretContext.FIELD_RECEIVER);
        }

        readConstantString(ib, context);
        readConstantString(ib, context);
        readConstantString(ib, context);

        if (opcode == Opcs.PUTSTATIC)
        {
            ib.iconst1();
        }
        else
        {
            ib.iconst0();
        }

        ib.aload(InterpretContext.FIELD_RECEIVER);
        ib.aload(InterpretContext.FIELD_VALUE);

        context.vm.setField.invokeStatic(ib);

        return ib.toInsnList();
    }

    private static void readConstantString(
            InsnBuilder ib,
            InterpretContext context)
    {
        ib.aload(InterpretContext.CONSTANTS);
        context.nextToken(ib);
        context.vm.constantString.invokeStatic(ib);
    }
}
