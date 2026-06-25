package nhcm.bytecodevm.AdvInsn;

import java.util.function.Consumer;

public record SwitchCase(int key, Consumer<AdvInsnBuilder> body)
{
    public static SwitchCase of(int key, Consumer<AdvInsnBuilder> body)
    {
        return new SwitchCase(key, body);
    }
}
