package nhcm.bytecodevm.AdvInsn;

import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

final class AdvInsnSupport
{
    private AdvInsnSupport()
    {
    }

    static Type type(Class<?> type)
    {
        if (type == void.class)
        {
            return Type.VOID_TYPE;
        }
        return Type.getType(type);
    }

    static void store(InsnBuilder builder, Type type, int index)
    {
        switch (type.getSort())
        {
            case Type.LONG -> builder.lstore(index);
            case Type.FLOAT -> builder.fstore(index);
            case Type.DOUBLE -> builder.dstore(index);
            case Type.OBJECT, Type.ARRAY -> builder.astore(index);
            default -> builder.istore(index);
        }
    }

    static void drop(InsnBuilder builder, Type type)
    {
        if (type.getSort() == Type.LONG || type.getSort() == Type.DOUBLE)
        {
            builder.pop2();
        }
        else
        {
            builder.pop();
        }
    }

    static int slotSize(Type type)
    {
        return type.getSize();
    }

    static void emitArgs(InsnBuilder ib, Expr[] args)
    {
        for (Expr arg : args)
        {
            arg.emit(ib);
        }
    }

    static String methodDescriptor(Type returnType, Expr[] args)
    {
        Type[] argTypes = new Type[args.length];
        for (int i = 0; i < args.length; i++)
        {
            argTypes[i] = args[i].type();
        }
        return Type.getMethodDescriptor(returnType, argTypes);
    }

    static String joinArgs(Expr[] args)
    {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < args.length; i++)
        {
            if (i > 0)
            {
                result.append(", ");
            }
            result.append(args[i].source());
        }
        return result.toString();
    }

    static String simpleName(Type type)
    {
        if (type.getSort() == Type.ARRAY)
        {
            return type.getClassName();
        }
        if (type.getSort() == Type.OBJECT)
        {
            String name = type.getClassName();
            int index = name.lastIndexOf('.');
            return index == -1 ? name : name.substring(index + 1);
        }
        return type.getClassName();
    }

    static String quote(String value)
    {
        return '"' + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + '"';
    }

    static void emitConverted(InsnBuilder ib, Expr expr, Type target)
    {
        expr.emit(ib);
        convert(expr.type(), target, ib);
    }

    static void convert(Type from, Type to, InsnBuilder ib)
    {
        if (sameValueType(from, to))
        {
            return;
        }

        Type normalizedFrom = numeric(from);
        Type normalizedTo = numeric(to);
        switch (normalizedFrom.getSort())
        {
            case Type.INT -> {
                switch (normalizedTo.getSort())
                {
                    case Type.LONG -> ib.i2l();
                    case Type.FLOAT -> ib.i2f();
                    case Type.DOUBLE -> ib.i2d();
                    default -> {
                    }
                }
            }
            case Type.LONG -> {
                switch (normalizedTo.getSort())
                {
                    case Type.INT -> ib.l2i();
                    case Type.FLOAT -> ib.l2f();
                    case Type.DOUBLE -> ib.l2d();
                    default -> {
                    }
                }
            }
            case Type.FLOAT -> {
                switch (normalizedTo.getSort())
                {
                    case Type.INT -> ib.f2i();
                    case Type.LONG -> ib.f2l();
                    case Type.DOUBLE -> ib.f2d();
                    default -> {
                    }
                }
            }
            case Type.DOUBLE -> {
                switch (normalizedTo.getSort())
                {
                    case Type.INT -> ib.d2i();
                    case Type.LONG -> ib.d2l();
                    case Type.FLOAT -> ib.d2f();
                    default -> {
                    }
                }
            }
            default -> throw new IllegalArgumentException("Cannot convert " + from + " to " + to);
        }
    }

    static Type commonNumeric(Type left, Type right)
    {
        left = numeric(left);
        right = numeric(right);

        if (left.getSort() == Type.DOUBLE || right.getSort() == Type.DOUBLE)
        {
            return Type.DOUBLE_TYPE;
        }
        if (left.getSort() == Type.FLOAT || right.getSort() == Type.FLOAT)
        {
            return Type.FLOAT_TYPE;
        }
        if (left.getSort() == Type.LONG || right.getSort() == Type.LONG)
        {
            return Type.LONG_TYPE;
        }
        return Type.INT_TYPE;
    }

    static Type commonBitwise(Type left, Type right)
    {
        left = numeric(left);
        right = numeric(right);
        if (left.getSort() == Type.LONG || right.getSort() == Type.LONG)
        {
            return Type.LONG_TYPE;
        }
        if (left.getSort() == Type.FLOAT || left.getSort() == Type.DOUBLE ||
                right.getSort() == Type.FLOAT || right.getSort() == Type.DOUBLE)
        {
            throw new IllegalArgumentException("Bitwise operations need int-like or long values");
        }
        return Type.INT_TYPE;
    }

    static Type numeric(Type type)
    {
        return switch (type.getSort())
        {
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> Type.INT_TYPE;
            case Type.LONG, Type.FLOAT, Type.DOUBLE -> type;
            default -> throw new IllegalArgumentException("Not a numeric value: " + type);
        };
    }

    static void requireAssignable(Type target, Type source)
    {
        if (sameValueType(target, source))
        {
            return;
        }

        if (isIntLike(target) && isIntLike(source))
        {
            return;
        }

        if (isReference(target) && isReference(source))
        {
            return;
        }

        throw new IllegalArgumentException("Cannot assign " + source + " to " + target);
    }

    static void requireReference(Type type, String context)
    {
        if (!isReference(type))
        {
            throw new IllegalArgumentException(context + " needs a reference value, got " + type);
        }
    }

    static boolean sameValueType(Type left, Type right)
    {
        return left.equals(right) || numericEquivalent(left).equals(numericEquivalent(right));
    }

    static Type numericEquivalent(Type type)
    {
        return isIntLike(type) ? Type.INT_TYPE : type;
    }

    static boolean isIntLike(Type type)
    {
        return switch (type.getSort())
        {
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> true;
            default -> false;
        };
    }

    static boolean isReference(Type type)
    {
        return type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY;
    }

    static int primitiveArrayCode(Type type)
    {
        return switch (type.getSort())
        {
            case Type.BOOLEAN -> Opcodes.T_BOOLEAN;
            case Type.CHAR -> Opcodes.T_CHAR;
            case Type.FLOAT -> Opcodes.T_FLOAT;
            case Type.DOUBLE -> Opcodes.T_DOUBLE;
            case Type.BYTE -> Opcodes.T_BYTE;
            case Type.SHORT -> Opcodes.T_SHORT;
            case Type.INT -> Opcodes.T_INT;
            case Type.LONG -> Opcodes.T_LONG;
            default -> throw new IllegalArgumentException("Not a primitive array element type: " + type);
        };
    }
}
