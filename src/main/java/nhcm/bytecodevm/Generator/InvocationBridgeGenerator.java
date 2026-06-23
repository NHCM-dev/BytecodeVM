package nhcm.bytecodevm.Generator;

import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import nhcm.bytecodevm.Utils.TypeUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.IdentityHashMap;
import java.util.Map;

class InvocationBridgeGenerator
{
    private final Map<ClassNode, Integer> nextIds = new IdentityHashMap<>();

    void rewrite(ClassNode owner, MethodNode method)
    {
        for (AbstractInsnNode instruction : method.instructions.toArray())
        {
            if (instruction instanceof InvokeDynamicInsnNode dynamicInsn)
            {
                rewriteInvokeDynamic(owner, method, dynamicInsn);
            }
            else if (instruction instanceof MethodInsnNode methodInsn &&
                    methodInsn.getOpcode() == Opcodes.INVOKESPECIAL &&
                    !"<init>".equals(methodInsn.name))
            {
                rewriteInvokeSpecial(owner, method, methodInsn);
            }
        }
    }

    private void rewriteInvokeDynamic(
            ClassNode owner,
            MethodNode source,
            InvokeDynamicInsnNode invocation)
    {
        String bridgeName = nextBridgeName(owner);
        MethodNode bridge = new MethodNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                bridgeName,
                invocation.desc,
                null,
                null);
        InsnBuilder ib = new InsnBuilder(bridge.instructions);
        loadArguments(ib, Type.getArgumentTypes(invocation.desc), 0);
        ib.invokeDynamic(
                invocation.name,
                invocation.desc,
                invocation.bsm,
                invocation.bsmArgs.clone());
        TypeUtils.returnValue(ib, Type.getReturnType(invocation.desc));
        owner.methods.add(bridge);

        source.instructions.set(invocation, new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                owner.name,
                bridgeName,
                invocation.desc,
                false));
    }

    private void rewriteInvokeSpecial(
            ClassNode owner,
            MethodNode source,
            MethodInsnNode invocation)
    {
        Type[] arguments = Type.getArgumentTypes(invocation.desc);
        Type[] bridgeArguments = new Type[arguments.length + 1];
        bridgeArguments[0] = Type.getObjectType(owner.name);
        System.arraycopy(arguments, 0, bridgeArguments, 1, arguments.length);
        String bridgeDescriptor = Type.getMethodDescriptor(
                Type.getReturnType(invocation.desc),
                bridgeArguments);
        String bridgeName = nextBridgeName(owner);

        MethodNode bridge = new MethodNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                bridgeName,
                bridgeDescriptor,
                null,
                null);
        InsnBuilder ib = new InsnBuilder(bridge.instructions);
        ib.aload(0);
        loadArguments(ib, arguments, 1);
        ib.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                invocation.owner,
                invocation.name,
                invocation.desc,
                invocation.itf));
        TypeUtils.returnValue(ib, Type.getReturnType(invocation.desc));
        owner.methods.add(bridge);

        source.instructions.set(invocation, new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                owner.name,
                bridgeName,
                bridgeDescriptor,
                false));
    }

    private String nextBridgeName(ClassNode owner)
    {
        int id = nextIds.getOrDefault(owner, 0);
        String name;
        do
        {
            name = "$vm$invoke$" + id++;
        }
        while (hasMethod(owner, name));
        nextIds.put(owner, id);
        return name;
    }

    private static boolean hasMethod(ClassNode owner, String name)
    {
        for (MethodNode method : owner.methods)
        {
            if (method.name.equals(name))
            {
                return true;
            }
        }
        return false;
    }

    private static void loadArguments(InsnBuilder ib, Type[] arguments, int local)
    {
        for (Type argument : arguments)
        {
            TypeUtils.load(ib, argument, local);
            local += argument.getSize();
        }
    }
}
