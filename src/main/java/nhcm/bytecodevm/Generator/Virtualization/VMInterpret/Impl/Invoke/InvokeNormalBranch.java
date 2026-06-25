package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Invoke;

import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.AdvInsn.Condition;
import nhcm.bytecodevm.AdvInsn.Expr;
import nhcm.bytecodevm.AdvInsn.Local;
import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;

import java.util.Set;

public class InvokeNormalBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.INVOKE.getOpcodes();
    }

    @Override
    public void generate(AdvInsnBuilder ib, InterpretContext context, Opcs opcode)
    {
        Local owner = context.local("invokeOwner", "java/lang/String", InterpretContext.INVOKE_OWNER);
        Local name = context.local("invokeName", "java/lang/String", InterpretContext.INVOKE_NAME);
        Local type = context.local("invokeType", "java/lang/invoke/MethodType", InterpretContext.INVOKE_TYPE);
        Local arguments = context.local("invokeArguments", "[Ljava/lang/Object;", InterpretContext.INVOKE_ARGUMENTS);
        Local index = context.intLocal("invokeIndex", InterpretContext.INVOKE_INDEX);
        Local receiver = context.objectLocal("invokeReceiver", InterpretContext.INVOKE_RECEIVER);
        Local result = context.objectLocal("invokeResult", InterpretContext.INVOKE_RESULT);
        Local ignoredInterfaceFlag = context.intLocal("invokeInterfaceFlag", InterpretContext.MIDDLE_VALUE);

        ib.set(owner, readConstantString(ib, context));
        ib.set(name, readConstantString(ib, context));
        ib.set(type, AdvInsnBuilder.callStatic(
                context.vm.owner,
                context.vm.methodType.name(),
                "java/lang/invoke/MethodType",
                readConstantString(ib, context)));

        context.nextToken(ib, ignoredInterfaceFlag);

        ib.set(arguments, AdvInsnBuilder.newArray(
                "java/lang/Object",
                AdvInsnBuilder.callVirtual(type, "java/lang/invoke/MethodType", "parameterCount", "I")));
        ib.set(index, AdvInsnBuilder.minus(AdvInsnBuilder.arrayLength(arguments), AdvInsnBuilder.constant(1)));
        ib.whileLoop(
                AdvInsnBuilder.greaterOrEqual(index, AdvInsnBuilder.constant(0)),
                b -> {
                    popObject(b, context);
                    b.setArray(arguments, index, context.stackObject());
                    b.increment(index, -1);
                });

        if (opcode == Opcs.INVOKESTATIC)
        {
            ib.set(receiver, AdvInsnBuilder.nullValue("java/lang/Object"));
        }
        else
        {
            popObject(ib, context, receiver);
        }

        if (opcode == Opcs.INVOKESPECIAL)
        {
            emitConstructorInvocation(ib, context, owner, type, arguments, receiver, result);
            return;
        }

        ib.set(result, AdvInsnBuilder.callStatic(
                context.vm.owner,
                context.vm.invoke.name(),
                "java/lang/Object",
                owner,
                name,
                type,
                AdvInsnBuilder.constant(opcode == Opcs.INVOKESTATIC),
                receiver,
                arguments));
        pushResultUnlessVoid(ib, context, type, result);
    }

    private static void emitConstructorInvocation(
            AdvInsnBuilder ib,
            InterpretContext context,
            Local owner,
            Local type,
            Local arguments,
            Local receiver,
            Local result)
    {
        ib.set(result, AdvInsnBuilder.callStatic(
                context.vm.owner,
                context.vm.construct.name(),
                "java/lang/Object",
                owner,
                type,
                arguments));
        ib.directCall(AdvInsnBuilder.callVirtual(
                context.frame(),
                context.frameClassName,
                context.frame.replaceIdentity.name(),
                "V",
                receiver,
                result));
    }

    private static void pushResultUnlessVoid(
            AdvInsnBuilder ib,
            InterpretContext context,
            Local type,
            Local result)
    {
        Local returnType = context.local("invokeReturnType", "java/lang/Class", InterpretContext.INVOKE_RETURN_TYPE);
        ib.set(returnType, AdvInsnBuilder.callVirtual(
                type,
                "java/lang/invoke/MethodType",
                "returnType",
                "java/lang/Class"));

        ib.ifCondition(
                AdvInsnBuilder.not(AdvInsnBuilder.equal(returnType, primitiveType("java/lang/Void"))),
                b -> b.ifElse(
                        isCategory2Return(returnType),
                        category2 -> pushObjectWithWidth(category2, context, result, AdvInsnBuilder.constant(2)),
                        category1 -> pushObject(category1, context, result)));
    }

    private static Condition isCategory2Return(Local returnType)
    {
        return AdvInsnBuilder.or(
                AdvInsnBuilder.equal(returnType, primitiveType("java/lang/Long")),
                AdvInsnBuilder.equal(returnType, primitiveType("java/lang/Double")));
    }

    private static Expr primitiveType(String boxedOwner)
    {
        return AdvInsnBuilder.staticField(boxedOwner, "TYPE", "java/lang/Class");
    }

    private static Expr readConstantString(AdvInsnBuilder ib, InterpretContext context)
    {
        Local token = context.intLocal("invokeToken", InterpretContext.JUMP_TARGET);
        context.nextToken(ib, token);
        return context.constantString(token);
    }
}
