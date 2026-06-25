package nhcm.bytecodevm.AdvInsn;

import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import org.objectweb.asm.Type;

public record FieldAccess(Expr owner, Type ownerType, String name, Type type) implements Expr
{
    @Override
    public void emit(InsnBuilder builder)
    {
        emitOwner(builder);
        builder.getField(ownerType.getInternalName(), name, type.getDescriptor());
    }

    @Override
    public String source()
    {
        return owner.source() + "." + name;
    }

    void emitOwner(InsnBuilder builder)
    {
        owner.emit(builder);
    }
}
