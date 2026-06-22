package nhcm.bytecodevm.Utils;

import nhcm.bytecodevm.Enums.Acc;
import org.objectweb.asm.tree.FieldNode;

public class FieldUtils
{
    public static FieldNode newFieldNode(Acc[] access, String name, String descriptor)
    {
        return newFieldNode(access, name, descriptor, null, null);
    }

    public static FieldNode newFieldNode(Acc[] access, String name, String descriptor, Object value)
    {
        return newFieldNode(access, name, descriptor, null, value);
    }

    public static FieldNode newFieldNode(Acc[] access, String name, String descriptor, String signature)
    {
        return newFieldNode(access, name, descriptor, signature, null);
    }

    public static FieldNode newFieldNode(Acc[] access, String name, String descriptor, String signature, Object value)
    {
        return new FieldNode(ClassUtils.getAccessModifiers(access), name, descriptor, signature, value);
    }
}
