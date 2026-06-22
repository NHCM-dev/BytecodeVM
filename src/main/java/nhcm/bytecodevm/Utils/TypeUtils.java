package nhcm.bytecodevm.Utils;

import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.Type;

public class TypeUtils
{

    public static void loadAndBox(InsnBuilder builder, Type type, int localIndex)
    {
        load(builder, type, localIndex);
        box(builder, type);
    }

    public static void load(InsnBuilder builder, Type type, int localIndex)
    {
        switch (type.getSort())
        {
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.CHAR:
            case Type.SHORT:
            case Type.INT:
                builder.iload(localIndex);
                break;

            case Type.LONG:
                builder.lload(localIndex);
                break;

            case Type.FLOAT:
                builder.fload(localIndex);
                break;

            case Type.DOUBLE:
                builder.dload(localIndex);
                break;

            case Type.OBJECT:
            case Type.ARRAY:
                builder.aload(localIndex);
                break;

            default:
                throw new IllegalArgumentException(type.toString());
        }
    }

    public static void box(InsnBuilder builder, Type type)
    {
        switch (type.getSort())
        {
            case Type.BOOLEAN:
                builder.invokeStatic("java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;");
                break;

            case Type.BYTE:
                builder.invokeStatic("java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;");
                break;

            case Type.CHAR:
                builder.invokeStatic("java/lang/Character", "valueOf", "(C)Ljava/lang/Character;");
                break;

            case Type.SHORT:
                builder.invokeStatic("java/lang/Short", "valueOf", "(S)Ljava/lang/Short;");
                break;

            case Type.INT:
                builder.invokeStatic("java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;");
                break;

            case Type.LONG:
                builder.invokeStatic("java/lang/Long", "valueOf", "(J)Ljava/lang/Long;");
                break;

            case Type.FLOAT:
                builder.invokeStatic("java/lang/Float", "valueOf", "(F)Ljava/lang/Float;");
                break;

            case Type.DOUBLE:
                builder.invokeStatic("java/lang/Double", "valueOf", "(D)Ljava/lang/Double;");
                break;

            case Type.OBJECT:
            case Type.ARRAY:
                break;

            default:
                throw new IllegalArgumentException(type.toString());
        }
    }

    public static void unbox(
            InsnBuilder builder,
            Type type)
    {
        switch (type.getSort())
        {
            case Type.VOID:
                return;

            case Type.BOOLEAN:
                builder.checkCast("java/lang/Boolean");
                builder.invokeVirtual("java/lang/Boolean", "booleanValue", "()Z");
                break;

            case Type.BYTE:
                builder.checkCast("java/lang/Byte");
                builder.invokeVirtual("java/lang/Byte", "byteValue", "()B");
                break;

            case Type.CHAR:
                builder.checkCast("java/lang/Character");
                builder.invokeVirtual("java/lang/Character", "charValue", "()C");
                break;

            case Type.SHORT:
                builder.checkCast("java/lang/Short");
                builder.invokeVirtual("java/lang/Short", "shortValue", "()S");
                break;

            case Type.INT:
                builder.checkCast("java/lang/Integer");
                builder.invokeVirtual("java/lang/Integer", "intValue", "()I");
                break;

            case Type.LONG:
                builder.checkCast("java/lang/Long");
                builder.invokeVirtual("java/lang/Long", "longValue", "()J");
                break;

            case Type.FLOAT:
                builder.checkCast("java/lang/Float");
                builder.invokeVirtual("java/lang/Float", "floatValue", "()F");
                break;

            case Type.DOUBLE:
                builder.checkCast("java/lang/Double");
                builder.invokeVirtual("java/lang/Double", "doubleValue", "()D");
                break;

            case Type.OBJECT:
            case Type.ARRAY:
                builder.checkCast(type.getInternalName());
                break;
        }
    }

    public static void returnValue(
            InsnBuilder builder,
            Type returnType)
    {
        switch (returnType.getSort())
        {
            case Type.VOID:
                builder._return();
                break;

            case Type.LONG:
                builder.lreturn();
                break;

            case Type.FLOAT:
                builder.freturn();
                break;

            case Type.DOUBLE:
                builder.dreturn();
                break;

            case Type.OBJECT:
            case Type.ARRAY:
                builder.areturn();
                break;

            default:
                builder.ireturn();
                break;
        }
    }
}
