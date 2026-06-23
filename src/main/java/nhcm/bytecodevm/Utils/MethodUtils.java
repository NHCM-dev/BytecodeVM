package nhcm.bytecodevm.Utils;

import nhcm.bytecodevm.Enums.Acc;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

public class MethodUtils
{
    public static Type[] getParameters(MethodNode methodNode)
    {
        return Type.getArgumentTypes(methodNode.desc);
    }

    public static Type getReturn(MethodNode methodNode)
    {
        return Type.getReturnType(methodNode.desc);
    }

    public static MethodNode newMethodNode(Acc[] access, String name, String descriptor)
    {
        return newMethodNode(access, name, descriptor, null);
    }

    public static MethodNode newMethodNode(Acc[] access, String name, String descriptor, String[] exceptions)
    {
        return newMethodNode(access, name, descriptor, null, exceptions);
    }

    public static MethodNode newMethodNode(Acc[] access, String name, String descriptor, String signature, String[] exceptions)
    {
        return new MethodNode(ClassUtils.getAccessModifiers(access), name, descriptor, signature, exceptions);
    }

    public static boolean isStatic(MethodNode methodNode)
    {
        return (methodNode.access & Opcodes.ACC_STATIC) != 0;
    }

    public static boolean isAbstract(MethodNode methodNode)
    {
        return (methodNode.access & Opcodes.ACC_ABSTRACT) != 0;
    }

    public static boolean isNative(MethodNode methodNode)
    {
        return (methodNode.access & Opcodes.ACC_NATIVE) != 0;
    }
}
