package nhcm.bytecodevm.ObfuscatedExample;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VM1
{
    private static final List<VMCodePool> CODE_POOLS = Arrays.asList(
            CodePool.INSTANCE,
            CodePool2.INSTANCE);
    private static final Map<String, MethodHandle> FIELD_HANDLES = new ConcurrentHashMap<>();
    private static final Map<String, MethodHandle> METHOD_HANDLES = new ConcurrentHashMap<>();

    private VM1()
    {
    }

    @SuppressWarnings("unchecked")
    public static <T> T execute(int codeId, Object receiver, Object... arguments)
    {
        VMProgram program = resolve(codeId);
        MethodFrame frame = new MethodFrame(
                program.maxLocals(),
                program.maxStack());

        int argumentOffset = 0;
        if (receiver != null)
        {
            frame.locals[0] = receiver;
            argumentOffset = 1;
        }
        System.arraycopy(arguments, 0, frame.locals, argumentOffset, arguments.length);
        interpret(program, frame);
        return (T) frame.returnValue;
    }

    private static VMProgram resolve(int codeId)
    {
        VMProgram resolved = null;
        for (VMCodePool codePool : CODE_POOLS)
        {
            VMProgram candidate = codePool.find(codeId);
            if (candidate == null) continue;
            if (resolved != null)
            {
                throw new IllegalStateException("Duplicate code id: " + codeId);
            }
            resolved = candidate;
        }
        if (resolved == null)
        {
            throw new IllegalArgumentException("Unknown code id: " + codeId);
        }
        return resolved;
    }

    private static void interpret(VMProgram program, MethodFrame frame)
    {
        int[] code = program.code();
        Object[] constants = program.constants();

        while (!frame.returned)
        {
            int opcode = nextToken(code, frame);

            switch (opcode)
            {
                case -1062458953:
                case 1779033703:
                {
                    int localIndex = nextToken(code, frame);
                    frame.push(frame.locals[localIndex]);
                    break;
                }

                case -1150833019:
                {
                    int localIndex = nextToken(code, frame);
                    frame.locals[localIndex] = frame.pop();
                    break;
                }

                case 608135816: frame.push(0); break;
                case -2052912941: frame.push(1); break;
                case 320440878: frame.push(3); break;
                case 57701188: frame.push(5); break;
                case 1013904242:
                case -1542899678:
                    frame.push(nextToken(code, frame));
                    break;

                case 698298832:
                {
                    int constantIndex = nextToken(code, frame);
                    frame.push(constants[constantIndex]);
                    break;
                }

                case -1521486534:
                {
                    int right = (int) frame.pop();
                    int left = (int) frame.pop();
                    frame.push(left + right);
                    break;
                }
                case -1694144372:
                {
                    int right = (int) frame.pop();
                    int left = (int) frame.pop();
                    frame.push(left * right);
                    break;
                }
                case 528734635:
                {
                    int right = (int) frame.pop();
                    int left = (int) frame.pop();
                    frame.push(left ^ right);
                    break;
                }
                case 1541459225:
                    frame.push(-(int) frame.pop());
                    break;
                case 310598401:
                {
                    int distance = (int) frame.pop();
                    int value = (int) frame.pop();
                    frame.push(value << distance);
                    break;
                }
                case 607225278:
                {
                    int distance = (int) frame.pop();
                    int value = (int) frame.pop();
                    frame.push(value >>> distance);
                    break;
                }

                case -876896931:
                {
                    int target = nextToken(code, frame);
                    if ((int) frame.pop() >= 0) frame.programCounter = target;
                    break;
                }

                case 1426881987:
                {
                    int target = nextToken(code, frame);
                    int right = (int) frame.pop();
                    int left = (int) frame.pop();
                    if (left < right) frame.programCounter = target;
                    break;
                }

                case 1925078388:
                {
                    int target = nextToken(code, frame);
                    int right = (int) frame.pop();
                    int left = (int) frame.pop();
                    if (left > right) frame.programCounter = target;
                    break;
                }

                case -2132889090:
                    frame.programCounter = nextToken(code, frame);
                    break;

                case -559038737:
                case 253635900:
                {
                    String owner = constantString(constants, nextToken(code, frame));
                    String name = constantString(constants, nextToken(code, frame));
                    String descriptor = constantString(constants, nextToken(code, frame));
                    boolean isStatic = opcode == 253635900;
                    Object receiver = isStatic ? null : frame.pop();
                    frame.push(getField(owner, name, descriptor, isStatic, receiver));
                    break;
                }

                case -889275714:
                case -1985229329:
                {
                    String owner = constantString(constants, nextToken(code, frame));
                    String name = constantString(constants, nextToken(code, frame));
                    String descriptor = constantString(constants, nextToken(code, frame));
                    boolean isStatic = opcode == -1985229329;
                    Object value = frame.pop();
                    Object receiver = isStatic ? null : frame.pop();
                    setField(owner, name, descriptor, isStatic, receiver, value);
                    break;
                }

                case 270544960:
                case 540033104:
                {
                    String owner = constantString(constants, nextToken(code, frame));
                    String name = constantString(constants, nextToken(code, frame));
                    String descriptor = constantString(constants, nextToken(code, frame));
                    nextToken(code, frame); // MethodInsnNode.itf

                    boolean isStatic = opcode == 540033104;
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
                    break;
                }

                case 1654270250:
                    frame.returnValue = frame.pop();
                    frame.returned = true;
                    break;

                default:
                    throw new IllegalStateException(
                            "Unknown VM opcode " + Integer.toHexString(opcode) +
                                    " at pc " + (frame.programCounter - 1));
            }
        }
    }

    private static int nextToken(int[] code, MethodFrame frame)
    {
        return code[frame.programCounter++];
    }

    private static String constantString(Object[] constants, int index)
    {
        return (String) constants[index];
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
                    .invokeWithArguments(isStatic
                            ? Collections.emptyList()
                            : Collections.singletonList(receiver));
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
                    ? Collections.singletonList(value)
                    : Arrays.asList(receiver, value);
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
                Field field = ownerClass.getDeclaredField(name);
                field.setAccessible(true);
                if (field.getType() != fieldType ||
                        Modifier.isStatic(field.getModifiers()) != isStatic)
                {
                    throw new NoSuchFieldException(ownerClass.getName() + '.' + name);
                }
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                return setter
                        ? lookup.unreflectSetter(field)
                        : lookup.unreflectGetter(field);
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
                Method target = ownerClass.getDeclaredMethod(name, type.parameterArray());
                target.setAccessible(true);
                if (target.getReturnType() != type.returnType() ||
                        Modifier.isStatic(target.getModifiers()) != isStatic)
                {
                    throw new NoSuchMethodException(ownerClass.getName() + '.' + name + type);
                }
                return MethodHandles.lookup().unreflect(target);
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
        if (throwable instanceof RuntimeException) return (RuntimeException) throwable;
        if (throwable instanceof Error) throw (Error) throwable;
        return new IllegalStateException(throwable);
    }

}
