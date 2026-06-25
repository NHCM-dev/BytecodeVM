package nhcm.bytecodevm.AdvInsn;

import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.Type;

public record StaticFieldAccess(Type owner, String name, Type type) implements Expr
{
    @Override
    public void emit(InsnBuilder builder)
    {
        builder.getStatic(owner.getInternalName(), name, type.getDescriptor());
    }

    @Override
    public String source()
    {
        return AdvInsnSupport.simpleName(owner) + "." + name;
    }
}
