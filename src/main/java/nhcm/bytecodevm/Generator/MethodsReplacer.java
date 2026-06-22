package nhcm.bytecodevm.Generator;

import nhcm.bytecodevm.Data.CompiledMethod;
import nhcm.bytecodevm.Utils.Builder.InsnBuilder;
import nhcm.bytecodevm.Utils.MethodUtils;
import nhcm.bytecodevm.Utils.TypeUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MethodsReplacer
{
    private final List<CompiledMethod> compiledMethods;
    private final String vmClassName;

    public MethodsReplacer(List<CompiledMethod> compiledMethods, String vmClassName)
    {
        this.compiledMethods = List.copyOf(Objects.requireNonNull(compiledMethods, "compiledMethods"));
        this.vmClassName = Objects.requireNonNull(vmClassName, "vmClassName");
    }

    public Map<String, ClassNode> transform()
    {
        Map<String, ClassNode> classes = new LinkedHashMap<>();
        for (CompiledMethod compiledMethod : compiledMethods)
        {
            validate(compiledMethod);
            classes.put(compiledMethod.owner.name, compiledMethod.owner);
            replace(compiledMethod);
        }
        return classes;
    }

    private void replace(CompiledMethod compiledMethod)
    {
        MethodNode method = compiledMethod.source;
        boolean isStatic = compiledMethod.isStatic;
        Type[] parameters = Type.getArgumentTypes(compiledMethod.descriptor);

        InsnBuilder ib = new InsnBuilder();
        ib.pushInt(compiledMethod.codeId);
        if (isStatic)
        {
            ib.aconstNull();
        }
        else
        {
            ib.aload(0);
        }

        // execute copies this array directly into MethodFrame.locals. Keep JVM
        // local-variable slots aligned, including the unused second slot of J/D.
        int argumentSlots = 0;
        for (Type parameter : parameters)
        {
            argumentSlots += parameter.getSize();
        }
        ib.pushInt(argumentSlots);
        ib.aneArray("java/lang/Object");

        int sourceLocal = isStatic ? 0 : 1;
        int argumentSlot = 0;
        for (Type parameter : parameters)
        {
            ib.dup();
            ib.pushInt(argumentSlot);
            TypeUtils.loadAndBox(ib, parameter, sourceLocal);
            ib.aastore();
            sourceLocal += parameter.getSize();
            argumentSlot += parameter.getSize();
        }

        ib.invokeStatic(
                vmClassName,
                "execute",
                "(ILjava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");

        Type returnType = Type.getReturnType(compiledMethod.descriptor);
        if (returnType.getSort() == Type.VOID)
        {
            ib.pop();
            ib._return();
        }
        else
        {
            TypeUtils.unbox(ib, returnType);
            TypeUtils.returnValue(ib, returnType);
        }

        method.instructions.clear();
        method.instructions.add(ib.toInsnList());
        method.tryCatchBlocks.clear();
        method.localVariables = null;
        method.visibleLocalVariableAnnotations = null;
        method.invisibleLocalVariableAnnotations = null;
        method.maxStack = 0;
        method.maxLocals = sourceLocal;
    }

    private static void validate(CompiledMethod compiledMethod)
    {
        Objects.requireNonNull(compiledMethod, "compiledMethod");
        Objects.requireNonNull(compiledMethod.owner, "compiledMethod.owner");
        MethodNode method = Objects.requireNonNull(compiledMethod.source, "compiledMethod.source");

        if (!compiledMethod.owner.methods.contains(method))
        {
            throw new IllegalArgumentException("Method does not belong to owner: " + method.name + method.desc);
        }
        if ("<init>".equals(method.name) || "<clinit>".equals(method.name))
        {
            throw new IllegalArgumentException("Initializers cannot be replaced: " + method.name + method.desc);
        }
        if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0)
        {
            throw new IllegalArgumentException("Method has no replaceable bytecode: " + method.name + method.desc);
        }
        if (!method.desc.equals(compiledMethod.descriptor))
        {
            throw new IllegalArgumentException("Compiled method descriptor no longer matches " + method.name);
        }
        if (MethodUtils.isStatic(method) != compiledMethod.isStatic)
        {
            throw new IllegalArgumentException("Compiled method static flag no longer matches " + method.name + method.desc);
        }
    }
}
