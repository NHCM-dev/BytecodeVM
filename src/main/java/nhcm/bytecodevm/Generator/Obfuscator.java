package nhcm.bytecodevm.Generator;

import nhcm.bytecodevm.Config.BytecodeVMConfig;
import nhcm.bytecodevm.Config.TargetMatcher;
import nhcm.bytecodevm.Data.VirtualizationResult;
import nhcm.bytecodevm.Generator.GlobalTool.MethodFrameGenerator;
import nhcm.bytecodevm.Generator.GlobalTool.VMCodePoolGenerator;
import nhcm.bytecodevm.Generator.GlobalTool.VMProgramGenerator;
import nhcm.bytecodevm.Tools.JarTransformer;
import nhcm.bytecodevm.Tools.OpcMutator;
import nhcm.bytecodevm.Utils.ClassUtils;
import nhcm.bytecodevm.Utils.RandomUtils;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class Obfuscator
{
    private final Path input;
    private final Path output;
    private final BytecodeVMConfig config;
    private final TargetMatcher targetExclude;

    private final List<VMSetGenerator> VMSetGenerators = new ArrayList<>();

    private final MethodFrameGenerator methodFrameGenerator;
    private final ClassNode methodFrameClassNode;
    private final VMProgramGenerator vmProgramGenerator;
    private final ClassNode vmProgramClassNode;
    private final VMCodePoolGenerator vmCodePoolGenerator;
    private final ClassNode vmCodePoolClassNode;

    public Obfuscator(Path input, Path output, BytecodeVMConfig config)
    {
        this.input = input;
        this.output = output;
        this.config = config;
        targetExclude = new TargetMatcher();
        for(String exclusion : config.exclusions)
        {
            targetExclude.add(exclusion);
        }
        this.methodFrameGenerator = new MethodFrameGenerator("BytecodeVM/MethodFrame");
        this.methodFrameClassNode = methodFrameGenerator.getClassNode();
        this.vmProgramGenerator = new VMProgramGenerator("BytecodeVM/VMProgram");
        this.vmProgramClassNode = vmProgramGenerator.getClassNode();
        this.vmCodePoolGenerator = new VMCodePoolGenerator(
                "BytecodeVM/VMCodePool",
                vmProgramGenerator);
        this.vmCodePoolClassNode = vmCodePoolGenerator.getClassNode();
    }

    public void obfuscate()
    {
        try
        {
            JarTransformer.transformJar(input.toFile(), output.toFile(), this::obfuscateProcess);
        } catch (IOException e)
        {
            System.out.println("Failed obfuscating while reading input file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void obfuscateProcess(JarTransformer.JarContext context)
    {
        processJar(context);
        System.out.println("Adding classes...");
        context.addClass(methodFrameClassNode);
        context.addClass(vmProgramClassNode);
        context.addClass(vmCodePoolClassNode);
        System.out.println("Done adding required classes");
        List<VMSetGenerator> generators = new ArrayList<>(this.VMSetGenerators);
        for(VMSetGenerator generator : generators)
        {
            System.out.println("Virtualizing VM: " + generator.vmClassName);
            VirtualizationResult result = generator.compile();
            context.classes.putAll(result.transformedTarget);
            context.addClass(result.vmClass);
            for(ClassNode codePoolClass : result.codePoolClass)
            {
                context.addClass(codePoolClass);
            }
            System.out.println("Done virtualizing VM: " + generator.vmClassName);
        }
        System.out.println("Done virtualizing all classes");
    }

    private void processJar(JarTransformer.JarContext context)
    {
        System.out.println("Scanning input file for methods to obfuscate");
        String globalLocation = "BytecodeVM";
        VMSetGenerator allInOneVm = newVMSetGenerator(
                "BytecodeVM",
                "BytecodeVM");
        List<VMSetGenerator> perClasses = new ArrayList<>();
        List<VMSetGenerator> perMethods = new ArrayList<>();
        Map<String, VMSetGenerator> perPackage = new LinkedHashMap<>();
        for (ClassNode classNode : context.classes.values())
        {
            if(!targetExclude.isClassMatched(classNode))
            {
                String classPackage = ClassUtils.getPackageName(classNode);
                String VMlocation = globalLocation;
                switch (config.location)
                {
                    case ONE_PACKAGE -> VMlocation = globalLocation;
                    case NEW_PACKAGE -> VMlocation = classPackage + "/" + classNode.name + "VM";
                    case SAME_PACKAGE_AS_TARGET -> VMlocation = classPackage;
                }
                VMSetGenerator perClass = newVMSetGenerator(
                        classNode.name + "$VM",
                        VMlocation);
                if(config.createMode == BytecodeVMConfig.VMCreateMode.PER_CLASS)
                {
                    perClasses.add(perClass);
                }
                for(MethodNode methodNode : classNode.methods)
                {
                    if(!targetExclude.isMethodMatched(classNode, methodNode))
                    {
                        switch (config.createMode)
                        {
                            case PER_CLASS:
                            {
                                perClass.addMethod(methodNode, classNode);
                                break;
                            }
                            case PER_METHOD:
                            {
                                VMSetGenerator perMethod = newVMSetGenerator(
                                        classNode.name + "$" + methodNode.name + "$VM",
                                        VMlocation);
                                perMethods.add(perMethod);
                                perMethod.addMethod(methodNode, classNode);
                                break;
                            }
                            case PER_PACKAGE:
                            {
                                VMSetGenerator generator = perPackage.computeIfAbsent(
                                        classPackage,
                                        ignored -> newVMSetGenerator(
                                                classPackage + "$VM",
                                                classPackage));
                                generator.addMethod(methodNode, classNode);
                                break;
                            }
                            case ONE_FOR_ALL:
                            {
                                allInOneVm.addMethod(methodNode, classNode);
                                break;
                            }
                        }
                    }
                }
            }
        }
        switch (config.createMode)
        {
            case PER_CLASS:
            {
                VMSetGenerators.addAll(perClasses);
                break;
            }
            case PER_METHOD:
            {
                VMSetGenerators.addAll(perMethods);
                break;
            }
            case PER_PACKAGE:
            {
                VMSetGenerators.addAll(perPackage.values());
                break;
            }
            case ONE_FOR_ALL:
            {
                VMSetGenerators.add(allInOneVm);
                break;
            }
        }
        System.out.println("Scanned input file, found " + VMSetGenerators.size() + " methods to obfuscate");
    }

    private OpcMutator chooseMutator()
    {
        switch (config.mutateMode)
        {
            case ALL_RANDOM_INT:
            {
                return OpcMutator.MutateStrategy.RANDOM_INT.getMutator();
            }
            case ALL_RESORT:
            {
                return OpcMutator.MutateStrategy.RESORT.getMutator();
            }
            case ALL_AUTO_CHOOSE:
            {
                return OpcMutator.fromStrategy(RandomUtils.randomBoolean() ? OpcMutator.MutateStrategy.RANDOM_INT : OpcMutator.MutateStrategy.RESORT);
            }
            default:
            {
                return OpcMutator.MutateStrategy.NONE.getMutator();
            }
        }
    }

    private VMSetGenerator newVMSetGenerator(String name, String location)
    {
        return new VMSetGenerator(
                name,
                location,
                chooseMutator(),
                methodFrameGenerator,
                vmProgramGenerator,
                vmCodePoolGenerator,
                config);
    }
}
