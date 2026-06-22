package nhcm.bytecodevm.ObfuscatedExample;

public final class MethodFrame
{
    // locals[0] is the receiver for instance methods; arguments follow it.
    public final Object[] locals;
    // stack and stackPointer model the JVM operand stack.
    public final Object[] stack;

    // programCounter is an index into the encoded token stream, not a source line.
    public int programCounter;
    public int stackPointer;
    public Object returnValue;
    public boolean returned;

    public MethodFrame(int maxLocals, int maxStack)
    {
        locals = new Object[maxLocals];
        stack = new Object[maxStack];
    }

    public void push(Object value)
    {
        stack[stackPointer++] = value;
    }

    public Object pop()
    {
        Object value = stack[--stackPointer];
        stack[stackPointer] = null;
        return value;
    }
}
