package nhcm.bytecodevm.ObfuscatedExample;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class VM1
{
    private static final Map<String, MethodHandle> FIELD_HANDLES = new ConcurrentHashMap<>();
    private static final Map<String, MethodHandle> METHOD_HANDLES = new ConcurrentHashMap<>();

    private VM1()
    {
    }

    static int executeInt(int codeId, Object receiver, Object[] arguments)
    {
        MethodFrame frame = new MethodFrame(
                CodePool.maxLocals(codeId),
                CodePool.maxStack(codeId));

        frame.locals[0] = receiver;
        System.arraycopy(arguments, 0, frame.locals, 1, arguments.length);
        execute(codeId, frame);
        return (Integer) frame.returnValue;
    }

    private static void execute(int codeId, MethodFrame frame)
    {
        int[] encodedCode = CodePool.encodedCode(codeId);
        Object[] constants = CodePool.constants(codeId);

        while (!frame.returned)
        {
            // programCounter addresses every token, including opcode and operands.
            int opcode = nextToken(encodedCode, codeId, frame);

            switch (opcode)
            {
                case CodePool.ALOAD, CodePool.ILOAD -> {
                    int localIndex = nextToken(encodedCode, codeId, frame);
                    frame.push(frame.locals[localIndex]);
                }

                case CodePool.ISTORE -> {
                    int localIndex = nextToken(encodedCode, codeId, frame);
                    frame.locals[localIndex] = frame.pop();
                }

                case CodePool.ICONST_0 -> frame.push(0);
                case CodePool.ICONST_1 -> frame.push(1);
                case CodePool.ICONST_3 -> frame.push(3);
                case CodePool.ICONST_5 -> frame.push(5);
                case CodePool.BIPUSH, CodePool.SIPUSH -> frame.push(
                        nextToken(encodedCode, codeId, frame));

                case CodePool.LDC -> {
                    int constantIndex = nextToken(encodedCode, codeId, frame);
                    frame.push(constants[constantIndex]);
                }

                case CodePool.IADD -> binaryInt(frame, (left, right) -> left + right);
                case CodePool.IMUL -> binaryInt(frame, (left, right) -> left * right);
                case CodePool.IXOR -> binaryInt(frame, (left, right) -> left ^ right);
                case CodePool.INEG -> frame.push(-frame.popInt());
                case CodePool.ISHL -> binaryInt(frame, (value, distance) -> value << distance);
                case CodePool.IUSHR -> binaryInt(frame, (value, distance) -> value >>> distance);

                case CodePool.IFGE -> {
                    int target = nextToken(encodedCode, codeId, frame);
                    if (frame.popInt() >= 0) frame.programCounter = target;
                }

                case CodePool.IF_ICMPLT -> {
                    int target = nextToken(encodedCode, codeId, frame);
                    int right = frame.popInt();
                    int left = frame.popInt();
                    if (left < right) frame.programCounter = target;
                }

                case CodePool.IF_ICMPGT -> {
                    int target = nextToken(encodedCode, codeId, frame);
                    int right = frame.popInt();
                    int left = frame.popInt();
                    if (left > right) frame.programCounter = target;
                }

                case CodePool.GOTO -> frame.programCounter =
                        nextToken(encodedCode, codeId, frame);

                case CodePool.GETFIELD, CodePool.GETSTATIC -> {
                    String owner = constantString(constants, nextToken(encodedCode, codeId, frame));
                    String name = constantString(constants, nextToken(encodedCode, codeId, frame));
                    String descriptor = constantString(constants, nextToken(encodedCode, codeId, frame));
                    boolean isStatic = opcode == CodePool.GETSTATIC;
                    Object receiver = isStatic ? null : frame.pop();
                    frame.push(getField(owner, name, descriptor, isStatic, receiver));
                }

                case CodePool.PUTFIELD, CodePool.PUTSTATIC -> {
                    String owner = constantString(constants, nextToken(encodedCode, codeId, frame));
                    String name = constantString(constants, nextToken(encodedCode, codeId, frame));
                    String descriptor = constantString(constants, nextToken(encodedCode, codeId, frame));
                    boolean isStatic = opcode == CodePool.PUTSTATIC;
                    Object value = frame.pop();
                    Object receiver = isStatic ? null : frame.pop();
                    setField(owner, name, descriptor, isStatic, receiver, value);
                }

                case CodePool.INVOKEVIRTUAL, CodePool.INVOKESTATIC -> {
                    String owner = constantString(constants, nextToken(encodedCode, codeId, frame));
                    String name = constantString(constants, nextToken(encodedCode, codeId, frame));
                    String descriptor = constantString(constants, nextToken(encodedCode, codeId, frame));
                    nextToken(encodedCode, codeId, frame); // MethodInsnNode.itf

                    boolean isStatic = opcode == CodePool.INVOKESTATIC;
                    MethodType type = MethodType.fromMethodDescriptorString(
                            descriptor,
                            VM1.class.getClassLoader());
                    Object[] invocationArguments = new Object[type.parameterCount()];
                    for (int index = invocationArguments.length - 1; index >= 0; index--)
                    {
                        invocationArguments[index] = frame.pop();
                    }
                    Object receiver = isStatic ? null : frame.pop();
                    Object result = invoke(
                            owner,
                            name,
                            type,
                            isStatic,
                            receiver,
                            invocationArguments);
                    if (type.returnType() != void.class) frame.push(result);
                }

                case CodePool.IRETURN -> {
                    frame.returnValue = frame.popInt();
                    frame.returned = true;
                }

                default -> throw new IllegalStateException(
                        "Unknown VM opcode " + Integer.toHexString(opcode) +
                                " at pc " + (frame.programCounter - 1));
            }
        }
    }

    private static int nextToken(int[] encodedCode, int codeId, MethodFrame frame)
    {
        return CodePool.decodeToken(encodedCode, codeId, frame.programCounter++);
    }

    private static String constantString(Object[] constants, int index)
    {
        return (String) constants[index];
    }

    private static void binaryInt(MethodFrame frame, IntBinary operation)
    {
        int right = frame.popInt();
        int left = frame.popInt();
        frame.push(operation.apply(left, right));
    }

    private static Object getField(
            String owner,
            String name,
            String descriptor,
            boolean isStatic,
            Object receiver)
    {
        try
        {
            return fieldHandle(owner, name, descriptor, isStatic, false)
                    .invokeWithArguments(isStatic ? List.of() : List.of(receiver));
        }
        catch (Throwable throwable)
        {
            throw rethrow(throwable);
        }
    }

    private static void setField(
            String owner,
            String name,
            String descriptor,
            boolean isStatic,
            Object receiver,
            Object value)
    {
        try
        {
            List<Object> arguments = isStatic
                    ? List.of(value)
                    : List.of(receiver, value);
            fieldHandle(owner, name, descriptor, isStatic, true)
                    .invokeWithArguments(arguments);
        }
        catch (Throwable throwable)
        {
            throw rethrow(throwable);
        }
    }

    private static MethodHandle fieldHandle(
            String owner,
            String name,
            String descriptor,
            boolean isStatic,
            boolean setter)
    {
        String key = owner + '.' + name + ':' + descriptor + ':' + isStatic + ':' + setter;
        return FIELD_HANDLES.computeIfAbsent(key, ignored -> {
            try
            {
                Class<?> ownerClass = loadOwner(owner);
                Class<?> fieldType = MethodType.fromMethodDescriptorString(
                        "()" + descriptor,
                        ownerClass.getClassLoader()).returnType();
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                        ownerClass,
                        MethodHandles.lookup());

                if (isStatic)
                {
                    return setter
                            ? lookup.findStaticSetter(ownerClass, name, fieldType)
                            : lookup.findStaticGetter(ownerClass, name, fieldType);
                }
                return setter
                        ? lookup.findSetter(ownerClass, name, fieldType)
                        : lookup.findGetter(ownerClass, name, fieldType);
            }
            catch (ReflectiveOperationException exception)
            {
                throw new IllegalStateException(exception);
            }
        });
    }

    private static Object invoke(
            String owner,
            String name,
            MethodType type,
            boolean isStatic,
            Object receiver,
            Object[] arguments)
    {
        String key = owner + '.' + name + type + ':' + isStatic;
        MethodHandle handle = METHOD_HANDLES.computeIfAbsent(key, ignored -> {
            try
            {
                Class<?> ownerClass = loadOwner(owner);
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                        ownerClass,
                        MethodHandles.lookup());
                return isStatic
                        ? lookup.findStatic(ownerClass, name, type)
                        : lookup.findVirtual(ownerClass, name, type);
            }
            catch (ReflectiveOperationException exception)
            {
                throw new IllegalStateException(exception);
            }
        });

        List<Object> invocationArguments = new ArrayList<>();
        if (!isStatic) invocationArguments.add(receiver);
        invocationArguments.addAll(Arrays.asList(arguments));
        try
        {
            return handle.invokeWithArguments(invocationArguments);
        }
        catch (Throwable throwable)
        {
            throw rethrow(throwable);
        }
    }

    private static Class<?> loadOwner(String internalName)
    {
        try
        {
            return Class.forName(
                    internalName.replace('/', '.'),
                    false,
                    VM1.class.getClassLoader());
        }
        catch (ClassNotFoundException exception)
        {
            throw new IllegalStateException(exception);
        }
    }

    private static RuntimeException rethrow(Throwable throwable)
    {
        if (throwable instanceof RuntimeException runtime) return runtime;
        if (throwable instanceof Error error) throw error;
        return new IllegalStateException(throwable);
    }

    @FunctionalInterface
    private interface IntBinary
    {
        int apply(int left, int right);
    }
}
