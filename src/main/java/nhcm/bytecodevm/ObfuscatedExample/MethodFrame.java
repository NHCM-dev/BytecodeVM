package nhcm.bytecodevm.ObfuscatedExample;

final class MethodFrame
{
    // locals[0] is the receiver for instance methods; arguments follow it.
    final Object[] locals;
    // stack and stackPointer model the JVM operand stack.
    final Object[] stack;

    // programCounter is an index into the encoded token stream, not a source line.
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
