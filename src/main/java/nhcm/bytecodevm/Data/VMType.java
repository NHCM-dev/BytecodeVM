package nhcm.bytecodevm.Data;

import java.util.Objects;

public class VMType
{
    public final String descriptor;

    public VMType(String descriptor)
    {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
    }

    @Override
    public boolean equals(Object other)
    {
        return other instanceof VMType && descriptor.equals(((VMType) other).descriptor);
    }

    @Override
    public int hashCode()
    {
        return descriptor.hashCode();
    }

    @Override
    public String toString()
    {
        return descriptor;
    }
}
