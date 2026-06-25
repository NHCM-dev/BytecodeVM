package nhcm.bytecodevm.AdvInsn;

import java.util.function.Consumer;

public record CatchBlock(String exceptionType, String localName, Consumer<AdvInsnBuilder> body)
{
    public static CatchBlock catchType(String exceptionType, String localName, Consumer<AdvInsnBuilder> body)
    {
        return new CatchBlock(exceptionType, localName, body);
    }

    public static CatchBlock catchAny(String localName, Consumer<AdvInsnBuilder> body)
    {
        return new CatchBlock(null, localName, body);
    }
}
