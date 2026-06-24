package nhcm.bytecodevm.Utils.Builder;

public record FieldRef(String owner, String name, String descriptor)
{
    public void get(InsnBuilder ib)
    {
        ib.getField(this);
    }

    public void put(InsnBuilder ib)
    {
        ib.putField(this);
    }

    public void getStatic(InsnBuilder ib)
    {
        ib.getStatic(this);
    }

    public void putStatic(InsnBuilder ib)
    {
        ib.putStatic(this);
    }
}
