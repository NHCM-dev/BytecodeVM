package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Field;

import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.AdvInsn.Expr;
import nhcm.bytecodevm.AdvInsn.Local;
import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;

import java.util.Set;

public class WriteFieldBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.WRITE_FIELD.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        Local owner = context.local("fieldOwner", "java/lang/String", InterpretContext.FIELD_OWNER);
        Local name = context.local("fieldName", "java/lang/String", InterpretContext.FIELD_NAME);
        Local descriptor = context.local("fieldDescriptor", "java/lang/String", InterpretContext.FIELD_DESCRIPTOR);
        Local value = context.objectLocal("fieldValue", InterpretContext.FIELD_VALUE);
        Local receiver = context.objectLocal("fieldReceiver", InterpretContext.FIELD_RECEIVER);

        popObject(ib, context, value);
        if (opcode == Opcs.PUTSTATIC)
        {
            ib.set(receiver, AdvInsnBuilder.nullValue("java/lang/Object"));
        }
        else
        {
            popObject(ib, context, receiver);
        }

        ib.set(owner, readConstantString(ib, context));
        ib.set(name, readConstantString(ib, context));
        ib.set(descriptor, readConstantString(ib, context));

        ib.directCall(AdvInsnBuilder.callStatic(
                context.vm.owner,
                context.vm.setField.name(),
                "V",
                owner,
                name,
                descriptor,
                AdvInsnBuilder.constant(opcode == Opcs.PUTSTATIC),
                receiver,
                value));
    }

    private static Expr readConstantString(AdvInsnBuilder ib, InterpretContext context)
    {
        Local token = context.intLocal("fieldToken", InterpretContext.JUMP_TARGET);
        context.nextOperand(ib, token);
        return context.constantString(token);
    }
}
