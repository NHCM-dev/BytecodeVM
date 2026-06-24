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
import nhcm.bytecodevm.Utils.LogColors;
import nhcm.bytecodevm.Utils.MethodUtils;
import nhcm.bytecodevm.Utils.RandomUtils;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class Obfuscator
{
    private static final Logger logger = LoggerFactory.getLogger(Obfuscator.class);

    private final BytecodeVMConfig config;
    private final TargetMatcher targetInclude;
    private final TargetMatcher targetExclude;

    private final List<VMSetGenerator> VMSetGenerators = new ArrayList<>();

    private final MethodFrameGenerator methodFrameGenerator;
    private final ClassNode methodFrameClassNode;
    private final VMProgramGenerator vmProgramGenerator;
    private final ClassNode vmProgramClassNode;
    private final VMCodePoolGenerator vmCodePoolGenerator;
    private final ClassNode vmCodePoolClassNode;

    public Obfuscator(BytecodeVMConfig config)
    {
        this.config = config;
        this.targetExclude = new TargetMatcher();
        for(String exclusion : config.exclusions)
        {
            targetExclude.add(exclusion);
        }
        this.targetInclude = new TargetMatcher();
        for(String include : config.includes)
        {
            targetInclude.add(include);
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
        if(!Files.exists(config.inputFile))
        {
            logger.error("{}", LogColors.error("Input file does not exist: " + LogColors.path(config.inputFile.toAbsolutePath())));
            return;
        }
        logger.info("{}", LogColors.lifecycle(
                "Obfuscating " +
                        LogColors.path(config.inputFile.toAbsolutePath()) +
                        " -> " +
                        LogColors.path(config.outputFile.toAbsolutePath())));
        try
        {
            JarTransformer.transformJar(config.inputFile.toFile(), config.outputFile.toFile(), this::obfuscateProcess);
        } catch (IOException e)
        {
            logger.error(LogColors.error("Failed obfuscating while reading or writing jar"), e);
        }
    }

    private void obfuscateProcess(JarTransformer.JarContext context)
    {
        processJar(context);
        logger.info("{}", LogColors.lifecycle("Adding required VM support classes"));
        context.addClass(methodFrameClassNode);
        context.addClass(vmProgramClassNode);
        context.addClass(vmCodePoolClassNode);
        logger.debug("{}", LogColors.success("Added required VM support classes"));
        List<VMSetGenerator> generators = new ArrayList<>(this.VMSetGenerators);
        for(VMSetGenerator generator : generators)
        {
            logger.info("{}", LogColors.virtualize(
                    "Virtualizing VM: " +
                            LogColors.strong(generator.vmClassName) +
                            " (" + generator.methodCount() + " method(s))"));
            VirtualizationResult result = generator.compile();
            context.classes.putAll(result.transformedTarget);
            context.addClass(result.vmClass);
            for(ClassNode codePoolClass : result.codePoolClass)
            {
                context.addClass(codePoolClass);
            }
            logger.info("{}", LogColors.success("Done virtualizing VM: " + LogColors.strong(generator.vmClassName)));
        }
        logger.info("{}", LogColors.success("Done virtualizing all classes"));
    }

    private static boolean shouldIgnoreMethod(MethodNode methodNode)
    {
        return MethodUtils.isAbstract(methodNode) ||
                MethodUtils.isNative(methodNode) ||
                usesStackTraceIntrospection(methodNode);
    }

    private static String methodKey(MethodNode methodNode)
    {
        return methodNode.name + methodNode.desc;
    }

    private static Set<String> stackTraceSensitiveMethods(ClassNode classNode)
    {
        Set<String> sensitiveMethods = new HashSet<>();
        for (MethodNode methodNode : classNode.methods)
        {
            if (usesStackTraceIntrospection(methodNode))
            {
                sensitiveMethods.add(methodKey(methodNode));
            }
        }

        boolean changed;
        do
        {
            changed = false;
            for (MethodNode methodNode : classNode.methods)
            {
                String key = methodKey(methodNode);
                if (sensitiveMethods.contains(key))
                {
                    continue;
                }
                if (callsSensitiveMethod(classNode, methodNode, sensitiveMethods))
                {
                    sensitiveMethods.add(key);
                    changed = true;
                }
            }
        } while (changed);

        return sensitiveMethods;
    }

    private static boolean callsSensitiveMethod(ClassNode classNode, MethodNode methodNode, Set<String> sensitiveMethods)
    {
        for (AbstractInsnNode insn : methodNode.instructions)
        {
            if (!(insn instanceof MethodInsnNode methodInsn))
            {
                continue;
            }
            if (classNode.name.equals(methodInsn.owner) &&
                    sensitiveMethods.contains(methodInsn.name + methodInsn.desc))
            {
                return true;
            }
        }
        return false;
    }

    private static Set<String> securityManagerClasses(Collection<ClassNode> classNodes)
    {
        Map<String, String> superNames = new HashMap<>();
        for (ClassNode classNode : classNodes)
        {
            superNames.put(classNode.name, classNode.superName);
        }

        Set<String> securityManagers = new HashSet<>();
        boolean changed;
        do
        {
            changed = false;
            for (ClassNode classNode : classNodes)
            {
                if (securityManagers.contains(classNode.name))
                {
                    continue;
                }
                String superName = classNode.superName;
                if ("java/lang/SecurityManager".equals(superName) || securityManagers.contains(superName))
                {
                    securityManagers.add(classNode.name);
                    changed = true;
                    continue;
                }
                while (superNames.containsKey(superName))
                {
                    superName = superNames.get(superName);
                    if ("java/lang/SecurityManager".equals(superName) || securityManagers.contains(superName))
                    {
                        securityManagers.add(classNode.name);
                        changed = true;
                        break;
                    }
                }
            }
        } while (changed);

        return securityManagers;
    }

    private static boolean usesStackTraceIntrospection(MethodNode methodNode)
    {
        for (AbstractInsnNode insn : methodNode.instructions)
        {
            if (!(insn instanceof MethodInsnNode methodInsn))
            {
                continue;
            }
            if ("java/lang/Throwable".equals(methodInsn.owner) &&
                    "getStackTrace".equals(methodInsn.name) &&
                    "()[Ljava/lang/StackTraceElement;".equals(methodInsn.desc))
            {
                return true;
            }
            if ("java/lang/Thread".equals(methodInsn.owner) &&
                    "getStackTrace".equals(methodInsn.name) &&
                    "()[Ljava/lang/StackTraceElement;".equals(methodInsn.desc))
            {
                return true;
            }
            if (methodInsn.owner.startsWith("java/lang/StackWalker"))
            {
                return true;
            }
        }
        return false;
    }

    private void processJar(JarTransformer.JarContext context)
    {
        logger.info("{}", LogColors.scan("Scanning input file for methods to obfuscate"));
        String globalLocation = "BytecodeVM";
        VMSetGenerator allInOneVm = newVMSetGenerator("BytecodeVM", "BytecodeVM");
        List<VMSetGenerator> perClasses = new ArrayList<>();
        List<VMSetGenerator> perMethods = new ArrayList<>();
        Map<String, VMSetGenerator> perPackage = new LinkedHashMap<>();
        Set<String> securityManagerClasses = securityManagerClasses(context.classes.values());
        int matchedMethods = 0;
        for (ClassNode classNode : context.classes.values())
        {
            if(!targetInclude.isClassMatched(classNode))
            {
                continue;
            }
            if(targetExclude.isClassMatched(classNode))
            {
                continue;
            }
            String classPackage = ClassUtils.getPackageName(classNode);
            String VMlocation = globalLocation;
            switch (config.location)
            {
                case ONE_PACKAGE -> VMlocation = globalLocation;
                case NEW_PACKAGE -> VMlocation = classPackage + "/" + classNode.name + "VM";
                case SAME_PACKAGE_AS_TARGET -> VMlocation = classPackage;
            }
            VMSetGenerator perClass = newVMSetGenerator(
                    ClassUtils.getSimpleName(classNode) + "$VM",
                    VMlocation);
            if(config.createMode == BytecodeVMConfig.VMCreateMode.PER_CLASS)
            {
                perClasses.add(perClass);
            }
            Set<String> stackTraceSensitiveMethods = stackTraceSensitiveMethods(classNode);
            for(MethodNode methodNode : classNode.methods)
            {
                if(securityManagerClasses.contains(classNode.name) ||
                        shouldIgnoreMethod(methodNode) ||
                        stackTraceSensitiveMethods.contains(methodKey(methodNode)))
                {
                    continue;
                }
                if(!targetInclude.isMethodMatched(classNode, methodNode))
                {
                    continue;
                }
                if(targetExclude.isMethodMatched(classNode, methodNode))
                {
                    continue;
                }
                matchedMethods++;
                switch (config.createMode)
                {
                    case PER_CLASS:
                    {
                        perClass.addMethod(methodNode, classNode);
                        break;
                    }
                    case PER_METHOD:
                    {
                        VMSetGenerator perMethod = newVMSetGenerator(ClassUtils.getSimpleName(classNode) + "$" + methodNode.name + "$VM", VMlocation);
                        perMethods.add(perMethod);
                        perMethod.addMethod(methodNode, classNode);
                        break;
                    }
                    case PER_PACKAGE:
                    {
                        VMSetGenerator generator = perPackage.computeIfAbsent(classPackage, ignored -> newVMSetGenerator(classPackage + "$VM", classPackage));
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
        logger.info("{}", LogColors.scan(
                "Scanned input file, found " +
                        LogColors.strong(matchedMethods) +
                        " method(s) across " +
                        LogColors.strong(VMSetGenerators.size()) +
                        " VM set(s)"));
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
