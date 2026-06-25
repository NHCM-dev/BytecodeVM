package nhcm.bytecodevm.Generator;

import nhcm.bytecodevm.AdvInsn.AdvInsnBuilder;
import nhcm.bytecodevm.AdvInsn.Expr;
import nhcm.bytecodevm.AdvInsn.Local;
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
        ClassNode owner = compiledMethod.owner;
        MethodNode method = compiledMethod.source;
        boolean isStatic = compiledMethod.isStatic;
        int sourceLocal = isStatic ? 0 : 1;
        Type[] parameters = Type.getArgumentTypes(compiledMethod.descriptor);

        method.instructions.clear();
        method.tryCatchBlocks.clear();
        method.localVariables = null;
        method.visibleLocalVariableAnnotations = null;
        method.invisibleLocalVariableAnnotations = null;
        method.maxStack = 0;
        method.maxLocals = sourceLocal;

        AdvInsnBuilder ib = new AdvInsnBuilder(method);

        Type returnType = Type.getReturnType(method.desc);
        Local argArray = ib.var("args", "[Ljava/lang/Object;");
        ib.set(argArray, AdvInsnBuilder.newArray("java/lang/Object", AdvInsnBuilder.constant(parameters.length)));
        for(int i = 0; i < parameters.length; i++)
        {
            Local value = ib.getLocal("DOES_NOT_MATTER" + i, parameters[i], sourceLocal + i);
            ib.setArray(argArray, AdvInsnBuilder.constant(i), value);
        }
        Expr execute = AdvInsnBuilder.callStatic(
                vmClassName,
                "execute",
                "Ljava/lang/Object;",
                AdvInsnBuilder.constant(compiledMethod.codeId),
                (isStatic ? AdvInsnBuilder.constant(null) : AdvInsnBuilder.self(owner.name)),
                argArray
        );
        if(returnType.equals(Type.VOID_TYPE))
        {
            ib.directCall(execute);
            ib.returnVoid();
        } else
        {
            ib.returnValue(AdvInsnBuilder.cast(execute, returnType));
        }
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
