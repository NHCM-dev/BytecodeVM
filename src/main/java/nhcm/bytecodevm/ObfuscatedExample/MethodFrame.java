package nhcm.bytecodevm.ObfuscatedExample;

final class MethodFrame
{
    final Object[] locals;
    final Object[] stack;

    int programCounter;
    int stackPointer;
    Object returnValue;
    boolean returned;

    MethodFrame(int maxLocals, int maxStack)
    {
        locals = new Object[maxLocals];
        stack = new Object[maxStack];
    }

    void push(Object value)
    {
        stack[stackPointer++] = value;
    }

    Object pop()
    {
        Object value = stack[--stackPointer];
        stack[stackPointer] = null;
        return value;
    }

    int popInt()
    {
        return (Integer) pop();
    }
}
