package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Field;

import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.AdvInsn.Condition;
import nhcm.bytecodevm.AdvInsn.Expr;
import nhcm.bytecodevm.AdvInsn.Local;
import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;

import java.util.Set;

public class ReadFieldBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.READ_FIELD.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        Local owner = context.local("fieldOwner", "java/lang/String", InterpretContext.FIELD_OWNER);
        Local name = context.local("fieldName", "java/lang/String", InterpretContext.FIELD_NAME);
        Local descriptor = context.local("fieldDescriptor", "java/lang/String", InterpretContext.FIELD_DESCRIPTOR);
        Local receiver = context.objectLocal("fieldReceiver", InterpretContext.FIELD_RECEIVER);
        Local result = context.objectLocal("fieldResult", InterpretContext.FIELD_RESULT);

        ib.set(owner, readConstantString(ib, context));
        ib.set(name, readConstantString(ib, context));
        ib.set(descriptor, readConstantString(ib, context));

        if (opcode == Opcs.GETSTATIC)
        {
            ib.set(receiver, AdvInsnBuilder.nullValue("java/lang/Object"));
        }
        else
        {
            popObject(ib, context, receiver);
        }

        ib.set(result, AdvInsnBuilder.callStatic(
                context.vm.owner,
                context.vm.getField.name(),
                "java/lang/Object",
                owner,
                name,
                descriptor,
                AdvInsnBuilder.constant(opcode == Opcs.GETSTATIC),
                receiver));

        ib.ifElse(
                isCategory2Descriptor(descriptor),
                b -> pushObjectWithWidth(b, context, result, AdvInsnBuilder.constant(2)),
                b -> pushObject(b, context, result));
    }

    private static Expr readConstantString(AdvInsnBuilder ib, InterpretContext context)
    {
        Local token = context.intLocal("fieldToken", InterpretContext.JUMP_TARGET);
        context.nextToken(ib, token);
        return context.constantString(token);
    }

    private static Condition isCategory2Descriptor(Local descriptor)
    {
        Expr firstChar = AdvInsnBuilder.callVirtual(
                descriptor,
                "java/lang/String",
                "charAt",
                "C",
                AdvInsnBuilder.constant(0));
        return AdvInsnBuilder.or(
                AdvInsnBuilder.equal(firstChar, AdvInsnBuilder.constant('J')),
                AdvInsnBuilder.equal(firstChar, AdvInsnBuilder.constant('D')));
    }
}
