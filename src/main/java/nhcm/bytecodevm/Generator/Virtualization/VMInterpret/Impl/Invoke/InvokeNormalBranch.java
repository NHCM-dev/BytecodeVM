package nhcm.bytecodevm.Generator.Virtualization.VMInterpret.Impl.Invoke;

import nhcm.bytecodevm.Enums.Opcs;
import nhcm.bytecodevm.Enums.VMOpcode;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretBranch;
import nhcm.bytecodevm.Generator.Virtualization.VMInterpret.InterpretContext;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;

import java.util.Set;

public class InvokeNormalBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.INVOKE.getOpcodes();
    }

    @Override
    public InsnList generate(InterpretContext context, Opcs opcode)
    {
        InsnBuilder ib = new InsnBuilder();

        readConstantString(ib, context);
        ib.astore(InterpretContext.INVOKE_OWNER);
        readConstantString(ib, context);
        ib.astore(InterpretContext.INVOKE_NAME);

        readConstantString(ib, context);
        context.vm.methodType.invokeStatic(ib);
        ib.astore(InterpretContext.INVOKE_TYPE);

        // The interface flag is compile-time metadata; the runtime helper resolves
        // the declared member from owner/name/type.
        context.nextToken(ib);
        ib.pop();

        ib.aload(InterpretContext.INVOKE_TYPE);
        ib.invokeVirtual("java/lang/invoke/MethodType", "parameterCount", "()I");
        ib.aneArray("java/lang/Object");
        ib.astore(InterpretContext.INVOKE_ARGUMENTS);

        ib.aload(InterpretContext.INVOKE_ARGUMENTS);
        ib.arrayLength();
        ib.iconst1();
        ib.isub();
        ib.istore(InterpretContext.INVOKE_INDEX);

        LabelNode argumentsLoop = new LabelNode();
        LabelNode argumentsReady = new LabelNode();
        ib.label(argumentsLoop);
        ib.iload(InterpretContext.INVOKE_INDEX);
        ib.iflt(argumentsReady);
        ib.aload(InterpretContext.INVOKE_ARGUMENTS);
        ib.iload(InterpretContext.INVOKE_INDEX);
        popObject(ib, context);
        ib.aastore();
        ib.iinc(InterpretContext.INVOKE_INDEX, -1);
        ib.goto_(argumentsLoop);
        ib.label(argumentsReady);

        if (opcode == Opcs.INVOKESTATIC)
        {
            ib.aconstNull();
        }
        else
        {
            popObject(ib, context);
        }
        ib.astore(InterpretContext.INVOKE_RECEIVER);

        if (opcode == Opcs.INVOKESPECIAL)
        {
            emitConstructorInvocation(ib, context);
            return ib.toInsnList();
        }

        ib.aload(InterpretContext.INVOKE_OWNER);
        ib.aload(InterpretContext.INVOKE_NAME);
        ib.aload(InterpretContext.INVOKE_TYPE);
        if (opcode == Opcs.INVOKESTATIC)
        {
            ib.iconst1();
        }
        else
        {
            ib.iconst0();
        }
        ib.aload(InterpretContext.INVOKE_RECEIVER);
        ib.aload(InterpretContext.INVOKE_ARGUMENTS);
        context.vm.invoke.invokeStatic(ib);
        ib.astore(InterpretContext.INVOKE_RESULT);
        pushResultUnlessVoid(ib, context);
        return ib.toInsnList();
    }

    private static void emitConstructorInvocation(InsnBuilder ib, InterpretContext context)
    {
        ib.aload(InterpretContext.INVOKE_OWNER);
        ib.aload(InterpretContext.INVOKE_TYPE);
        ib.aload(InterpretContext.INVOKE_ARGUMENTS);
        context.vm.construct.invokeStatic(ib);
        ib.astore(InterpretContext.INVOKE_RESULT);

        context.loadFrame(ib);
        ib.aload(InterpretContext.INVOKE_RECEIVER);
        ib.aload(InterpretContext.INVOKE_RESULT);
        context.frame.replaceIdentity.invokeVirtual(ib);
    }

    private static void pushResultUnlessVoid(InsnBuilder ib, InterpretContext context)
    {
        LabelNode done = new LabelNode();
        LabelNode category2 = new LabelNode();
        ib.aload(InterpretContext.INVOKE_TYPE);
        ib.invokeVirtual("java/lang/invoke/MethodType", "returnType", "()Ljava/lang/Class;");
        ib.astore(InterpretContext.INVOKE_RETURN_TYPE);
        ib.aload(InterpretContext.INVOKE_RETURN_TYPE);
        ib.getStatic("java/lang/Void", "TYPE", "Ljava/lang/Class;");
        ib.ifAcmpEq(done);

        ib.aload(InterpretContext.INVOKE_RETURN_TYPE);
        ib.getStatic("java/lang/Long", "TYPE", "Ljava/lang/Class;");
        ib.ifAcmpEq(category2);
        ib.aload(InterpretContext.INVOKE_RETURN_TYPE);
        ib.getStatic("java/lang/Double", "TYPE", "Ljava/lang/Class;");
        ib.ifAcmpEq(category2);

        pushObject(ib, context, InterpretContext.INVOKE_RESULT);
        ib.goto_(done);

        ib.label(category2);
        ib.aload(InterpretContext.INVOKE_RESULT);
        pushObjectWithWidth(ib, context, 2);
        ib.label(done);
    }

    private static void readConstantString(InsnBuilder ib, InterpretContext context)
    {
        ib.aload(InterpretContext.CONSTANTS);
        context.nextToken(ib);
        context.vm.constantString.invokeStatic(ib);
    }
}
