package nhcm.bytecodevm.Generator;

import lombok.Getter;
import nhcm.bytecodevm.Config.BytecodeVMConfig;
import nhcm.bytecodevm.Data.CompiledMethod;
import nhcm.bytecodevm.Data.VMInsn.VMMethod;
import nhcm.bytecodevm.Data.VirtualizationResult;
import nhcm.bytecodevm.Generator.GlobalClass.MethodFrameGenerator;
import nhcm.bytecodevm.Generator.GlobalClass.VMCodePoolGenerator;
import nhcm.bytecodevm.Generator.GlobalClass.VMProgramGenerator;
import nhcm.bytecodevm.Generator.Virtualization.CodePoolGenerator;
import nhcm.bytecodevm.Generator.Virtualization.VMGenerator;
import nhcm.bytecodevm.Tools.OpcMutator;
import nhcm.bytecodevm.Tools.VMMethodCompiler;
import nhcm.bytecodevm.Utils.MethodUtils;
import nhcm.bytecodevm.Utils.RandomUtils;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

public class VMSetGenerator
{
    private static final int CODE_POOL_METHOD_SIZE_LIMIT = 55_000;

    private final Map<MethodNode, ClassNode> methodsToObfuscate = new LinkedHashMap<>();
    private final Set<Integer> uniqueCodeIds = new LinkedHashSet<>();

    public final String vmClassName;
    public final String codePoolClassName;
    public final OpcMutator opcMutator;

    @Getter
    private final MethodFrameGenerator methodFrameGenerator;
    @Getter
    private final VMProgramGenerator vmProgramGenerator;
    @Getter
    private final VMCodePoolGenerator vmCodePoolGenerator;
    private final VMMethodCompiler compiler;
    private final InvocationBridgeGenerator invocationBridgeGenerator = new InvocationBridgeGenerator();
    private final List<CompiledMethod> compiledMethods = new ArrayList<>();
    @Getter
    private final List<CodePoolGenerator> codePoolGenerators = new ArrayList<>();
    private final BytecodeVMConfig config;

    public VMSetGenerator(
            String name, String location,
            OpcMutator opcMutator,
            MethodFrameGenerator methodFrameGenerator,
            VMProgramGenerator vmProgramGenerator,
            VMCodePoolGenerator vmCodePoolGenerator,
            BytecodeVMConfig config)
    {
        this.vmClassName = qualifyClassName(location, name);
        this.codePoolClassName = vmClassName + "$CodePool";
        this.opcMutator = opcMutator;
        this.methodFrameGenerator = methodFrameGenerator;
        this.vmProgramGenerator = vmProgramGenerator;
        this.vmCodePoolGenerator = vmCodePoolGenerator;
        this.config = config;
        this.compiler = new VMMethodCompiler(opcMutator);
    }

    private static String qualifyClassName(String location, String name)
    {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(name, "name");
        String normalizedLocation = location.replace('.', '/');
        while (normalizedLocation.startsWith("/"))
        {
            normalizedLocation = normalizedLocation.substring(1);
        }
        while (normalizedLocation.endsWith("/"))
        {
            normalizedLocation = normalizedLocation.substring(0, normalizedLocation.length() - 1);
        }
        String normalizedName = name.replace('.', '/');
        while (normalizedName.startsWith("/"))
        {
            normalizedName = normalizedName.substring(1);
        }
        return normalizedLocation.isEmpty()
                ? normalizedName
                : normalizedLocation + '/' + normalizedName;
    }

    public VirtualizationResult compile()
    {
        compiledMethods.clear();
        codePoolGenerators.clear();
        for (Map.Entry<MethodNode, ClassNode> entry : methodsToObfuscate.entrySet())
        {
            ClassNode owner = entry.getValue();
            MethodNode method = entry.getKey();

            invocationBridgeGenerator.rewrite(owner, method);
            VMMethod vmMethod = compiler.compile(owner, method);

            int codeId = generateUniqueCodeId();
            compiledMethods.add(new CompiledMethod(owner, method, vmMethod, codeId, method.desc, MethodUtils.isStatic(method)));
        }

        createCodePools();

        ClassNode vmClass = new VMGenerator(vmClassName, codePoolGenerators, opcMutator, methodFrameGenerator, vmProgramGenerator, vmCodePoolGenerator, config).getClassNode();
        List<ClassNode> codePoolClasses = new ArrayList<>();
        for (CodePoolGenerator codePoolGenerator : codePoolGenerators)
        {
            codePoolClasses.add(codePoolGenerator.getClassNode());
        }

        Map<String, ClassNode> transformedTargets = new MethodsReplacer(compiledMethods, vmClassName).transform();
        return new VirtualizationResult(transformedTargets, vmClass, codePoolClasses);
    }

    private void createCodePools()
    {
        List<List<CompiledMethod>> partitions = partitionCompiledMethods();
        for (int index = 0; index < partitions.size(); index++)
        {
            String poolClassName = partitions.size() == 1
                    ? codePoolClassName
                    : codePoolClassName + '$' + index;
            codePoolGenerators.add(new CodePoolGenerator(
                    poolClassName,
                    partitions.get(index),
                    vmProgramGenerator,
                    vmCodePoolGenerator,
                    config,
                    true));
        }
    }

    private List<List<CompiledMethod>> partitionCompiledMethods()
    {
        List<List<CompiledMethod>> partitions = new ArrayList<>();
        List<CompiledMethod> current = new ArrayList<>();

        for (CompiledMethod method : compiledMethods)
        {
            current.add(method);
            if (fitsInCodePool(current))
            {
                continue;
            }

            current.remove(current.size() - 1);
            if (current.isEmpty())
            {
                throw methodTooLarge(method);
            }
            partitions.add(new ArrayList<>(current));
            current.clear();
            current.add(method);
            if (!fitsInCodePool(current))
            {
                throw methodTooLarge(method);
            }
        }

        if (!current.isEmpty())
        {
            partitions.add(new ArrayList<>(current));
        }
        return partitions;
    }

    private boolean fitsInCodePool(List<CompiledMethod> methods)
    {
        CodePoolGenerator candidate = new CodePoolGenerator(
                codePoolClassName,
                methods,
                vmProgramGenerator,
                vmCodePoolGenerator,
                config,
                false);
        return candidate.getMaxGeneratedMethodSize() <= CODE_POOL_METHOD_SIZE_LIMIT;
    }

    private static IllegalStateException methodTooLarge(CompiledMethod method)
    {
        return new IllegalStateException(
                "VM method cannot fit in a CodePool: " +
                        method.owner.name + '.' +
                        method.source.name + method.source.desc);
    }

    public void addMethod(MethodNode methodNode, ClassNode classNode)
    {
        methodsToObfuscate.put(methodNode, classNode);
    }

    public int methodCount()
    {
        return methodsToObfuscate.size();
    }

    private int generateUniqueCodeId()
    {
        int codeId;
        do
        {
            codeId = RandomUtils.randomInt();
        } while (uniqueCodeIds.contains(codeId));
        uniqueCodeIds.add(codeId);
        return codeId;
    }

    public boolean hasMethods()
    {
        return !methodsToObfuscate.isEmpty();
    }
}
