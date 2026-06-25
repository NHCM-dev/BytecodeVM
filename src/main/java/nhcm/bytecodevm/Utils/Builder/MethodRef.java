package nhcm.bytecodevm.Utils.Builder;

import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;

public record MethodRef(String owner, String name, String descriptor)
{
    public void invokeVirtual(InsnBuilder ib)
    {
        ib.invokeVirtual(this);
    }

    public void invokeVirtual(AdvInsnBuilder ib)
    {
        invokeVirtual(ib.rawBuilder());
    }

    public void callVirtualMethod(AdvInsnBuilder ib)
    {
        invokeVirtual(ib);
    }

    public void invokeStatic(InsnBuilder ib)
    {
        ib.invokeStatic(this);
    }

    public void invokeStatic(AdvInsnBuilder ib)
    {
        invokeStatic(ib.rawBuilder());
    }

    public void callStaticMethod(AdvInsnBuilder ib)
    {
        invokeStatic(ib);
    }

    public void invokeSpecial(InsnBuilder ib)
    {
        ib.invokeSpecial(this);
    }

    public void invokeSpecial(AdvInsnBuilder ib)
    {
        invokeSpecial(ib.rawBuilder());
    }

    public void callSpecialMethod(AdvInsnBuilder ib)
    {
        invokeSpecial(ib);
    }

    public void invokeInterface(InsnBuilder ib)
    {
        ib.invokeInterface(this);
    }

    public void invokeInterface(AdvInsnBuilder ib)
    {
        invokeInterface(ib.rawBuilder());
    }

    public void callInterfaceMethod(AdvInsnBuilder ib)
    {
        invokeInterface(ib);
    }
}
