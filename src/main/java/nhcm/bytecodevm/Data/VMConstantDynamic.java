package nhcm.bytecodevm.Data;

import java.util.Arrays;
import java.util.Objects;

public class VMConstantDynamic
{
    public final String name;
    public final String descriptor;
    public final VMHandle bootstrapMethod;
    public final Object[] bootstrapArguments;

    public VMConstantDynamic(
            String name,
            String descriptor,
            VMHandle bootstrapMethod,
            Object[] bootstrapArguments)
    {
        this.name = Objects.requireNonNull(name, "name");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.bootstrapMethod = Objects.requireNonNull(bootstrapMethod, "bootstrapMethod");
        this.bootstrapArguments = Objects.requireNonNull(bootstrapArguments, "bootstrapArguments").clone();
    }

    @Override
    public boolean equals(Object other)
    {
        if (!(other instanceof VMConstantDynamic)) return false;
        VMConstantDynamic constant = (VMConstantDynamic) other;
        return name.equals(constant.name) && descriptor.equals(constant.descriptor) &&
                bootstrapMethod.equals(constant.bootstrapMethod) &&
                Arrays.equals(bootstrapArguments, constant.bootstrapArguments);
    }

    @Override
    public int hashCode()
    {
        return 31 * Objects.hash(name, descriptor, bootstrapMethod) +
                Arrays.hashCode(bootstrapArguments);
    }

    @Override
    public String toString()
    {
        return "VMConstantDynamic{" +
                "name='" + name + '\'' +
                ", descriptor='" + descriptor + '\'' +
                ", bootstrapMethod=" + bootstrapMethod +
                ", bootstrapArguments=" + Arrays.toString(bootstrapArguments) +
                '}';
    }
}
