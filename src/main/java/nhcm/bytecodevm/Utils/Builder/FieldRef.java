package nhcm.bytecodevm.Utils.Builder;

import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;

public record FieldRef(String owner, String name, String descriptor)
{
    public void get(InsnBuilder ib)
    {
        ib.getField(this);
    }

    public void get(AdvInsnBuilder ib)
    {
        get(ib.rawBuilder());
    }

    public void readField(AdvInsnBuilder ib)
    {
        get(ib);
    }

    public void put(InsnBuilder ib)
    {
        ib.putField(this);
    }

    public void put(AdvInsnBuilder ib)
    {
        put(ib.rawBuilder());
    }

    public void writeField(AdvInsnBuilder ib)
    {
        put(ib);
    }

    public void getStatic(InsnBuilder ib)
    {
        ib.getStatic(this);
    }

    public void getStatic(AdvInsnBuilder ib)
    {
        getStatic(ib.rawBuilder());
    }

    public void readStaticField(AdvInsnBuilder ib)
    {
        getStatic(ib);
    }

    public void putStatic(InsnBuilder ib)
    {
        ib.putStatic(this);
    }

    public void putStatic(AdvInsnBuilder ib)
    {
        putStatic(ib.rawBuilder());
    }

    public void writeStaticField(AdvInsnBuilder ib)
    {
        putStatic(ib);
    }
}
