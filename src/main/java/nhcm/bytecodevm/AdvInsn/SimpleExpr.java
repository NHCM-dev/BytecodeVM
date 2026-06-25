package nhcm.bytecodevm.AdvInsn;

import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.Type;

import java.util.function.Consumer;

record SimpleExpr(Type type, Consumer<InsnBuilder> emitter, String source) implements Expr
{
    @Override
    public void emit(InsnBuilder builder)
    {
        emitter.accept(builder);
    }
}
