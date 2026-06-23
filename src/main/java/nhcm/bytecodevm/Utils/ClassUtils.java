package nhcm.bytecodevm.Utils;

import nhcm.bytecodevm.Enums.Acc;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

public class ClassUtils implements Opcodes
{
    private static final int ClassNodeVersion = Opcodes.V1_8;

    public static String getPackageName(ClassNode cn)
    {
        int index = cn.name.lastIndexOf('/');

        if(index == -1)
        {
            return "";
        }

        return cn.name.substring(0, index);
    }

    public static String getSimpleName(ClassNode cn)
    {
        String name = cn.name;
        int index = name.lastIndexOf('/');
        return index == -1 ? name : name.substring(index + 1);
    }

    public static ClassNode newClassNode(Acc[] access, String className)
    {
        return newClassNode(access, className, "java/lang/Object");
    }

    public static ClassNode newClassNode(Acc[] access, String className, String extendsTo)
    {
        ClassNode cn = new ClassNode();
        cn.version = ClassNodeVersion;
        cn.access = getAccessModifiers(access);
        cn.name = className;
        cn.superName = extendsTo;
        return cn;
    }

    public static int getAccessModifiers(Acc... accessModifiers)
    {
        int accesses = 0;
        for (Acc accessModifier : accessModifiers)
        {
            accesses |= accessModifier.asmOpcodeValue;
        }
        return accesses;
    }
}
