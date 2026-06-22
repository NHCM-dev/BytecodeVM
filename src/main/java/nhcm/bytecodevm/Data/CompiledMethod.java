package nhcm.bytecodevm.Data;

import nhcm.bytecodevm.Data.VMInsn.VMMethod;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class CompiledMethod
{
    public final ClassNode owner;
    public final MethodNode source;
    public final VMMethod vmMethod;

    public final int codeId;
    public final String descriptor;
    public final boolean isStatic;

    public CompiledMethod(ClassNode owner, MethodNode source, VMMethod vmMethod, int codeId, String descriptor, boolean isStatic)
    {
        this.owner = owner;
        this.source = source;
        this.vmMethod = vmMethod;
        this.codeId = codeId;
        this.descriptor = descriptor;
        this.isStatic = isStatic;
    }
}