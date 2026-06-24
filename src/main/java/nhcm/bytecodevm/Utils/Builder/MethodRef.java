package nhcm.bytecodevm.Utils.Builder;

public record MethodRef(String owner, String name, String descriptor)
{
    public void invokeVirtual(InsnBuilder ib)
    {
        ib.invokeVirtual(this);
    }

    public void invokeStatic(InsnBuilder ib)
    {
        ib.invokeStatic(this);
    }

    public void invokeSpecial(InsnBuilder ib)
    {
        ib.invokeSpecial(this);
    }

    public void invokeInterface(InsnBuilder ib)
    {
        ib.invokeInterface(this);
    }
}
