package nhcm.bytecodevm.Data;

import org.objectweb.asm.tree.ClassNode;

import java.util.List;
import java.util.Map;

public class VirtualizationResult
{
    public final Map<String, ClassNode> transformedTarget;
    public final ClassNode vmClass;
    public final List<ClassNode> codePoolClass;

    public VirtualizationResult(Map<String, ClassNode> transformedTarget, ClassNode vmClass, List<ClassNode> codePoolClass)
    {
        this.transformedTarget = transformedTarget;
        this.vmClass = vmClass;
        this.codePoolClass = codePoolClass;
    }
}