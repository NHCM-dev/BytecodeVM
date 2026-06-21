package nhcm.bytecodevm.Data;

import lombok.ToString;

import java.util.Objects;

@ToString
public class VMHandle
{
    public final int tag;
    public final String owner;
    public final String name;
    public final String descriptor;
    public final boolean isInterface;

    public VMHandle(int tag, String owner, String name, String descriptor, boolean isInterface)
    {
        this.tag = tag;
        this.owner = Objects.requireNonNull(owner, "owner");
        this.name = Objects.requireNonNull(name, "name");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.isInterface = isInterface;
    }

    @Override
    public boolean equals(Object other)
    {
        if (!(other instanceof VMHandle)) return false;
        VMHandle handle = (VMHandle) other;
        return tag == handle.tag && isInterface == handle.isInterface &&
                owner.equals(handle.owner) && name.equals(handle.name) &&
                descriptor.equals(handle.descriptor);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(tag, owner, name, descriptor, isInterface);
    }
}
