import nhcm.bytecodevm.Data.VirtualizationResult;
import nhcm.bytecodevm.Generator.GlobalTool.MethodFrameGenerator;
import nhcm.bytecodevm.Generator.GlobalTool.VMCodePoolGenerator;
import nhcm.bytecodevm.Generator.GlobalTool.VMProgramGenerator;
import nhcm.bytecodevm.Generator.VMSetGenerator;
import nhcm.bytecodevm.Tools.OpcMutator;
import nhcm.bytecodevm.Utils.FileUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.file.Files;
import java.nio.file.Path;

public class Test3
{
    public static void main(String[] args) throws Exception
    {
        Path input = args.length > 0
                ? Path.of(args[0])
                : Path.of("workspace/TestClass.class");
        Path output = args.length > 1
                ? Path.of(args[1])
                : Path.of("workspace/generated");

        ClassNode target = FileUtils.fastReadClassNode(input);
        String targetSimpleName = target.name.substring(target.name.lastIndexOf('/') + 1);

        MethodFrameGenerator methodFrameGenerator =
                new MethodFrameGenerator("generated/MethodFrame");
        VMProgramGenerator vmProgramGenerator =
                new VMProgramGenerator("generated/VMProgram");
        VMCodePoolGenerator vmCodePoolGenerator =
                new VMCodePoolGenerator("generated/VMCodePool", vmProgramGenerator);

        VMSetGenerator vmSetGenerator = new VMSetGenerator(
                targetSimpleName + "$VM",
                "generated",
                OpcMutator.MutateStrategy.RANDOM_INT.getMutator(),
                methodFrameGenerator,
                vmProgramGenerator,
                vmCodePoolGenerator);

        int methodCount = 0;
        for (MethodNode method : target.methods)
        {
            if (method.name.equals("<init>") || method.name.equals("<clinit>"))
            {
                continue;
            }
            if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0)
            {
                continue;
            }
            vmSetGenerator.addMethod(method, target);
            methodCount++;
        }

        VirtualizationResult result = vmSetGenerator.compile();
        for (ClassNode transformedTarget : result.transformedTarget.values())
        {
            export(output, transformedTarget);
        }
        export(output, methodFrameGenerator.getClassNode());
        export(output, vmProgramGenerator.getClassNode());
        export(output, vmCodePoolGenerator.getClassNode());
        export(output, result.vmClass);
        for (ClassNode codePool : result.codePoolClass)
        {
            export(output, codePool);
        }

        System.out.println("Target: " + target.name);
        System.out.println("Compiled methods: " + methodCount);
        System.out.println("VM class: " + result.vmClass.name);
        System.out.println("CodePool classes: " + result.codePoolClass.size());
        System.out.println("Output: " + output.toAbsolutePath());
    }

    private static void export(Path outputRoot, ClassNode classNode) throws Exception
    {
        Path classFile = outputRoot.resolve(classNode.name + ".class");
        Files.createDirectories(classFile.getParent());
        FileUtils.fastExportClassNode(classNode, classFile);
    }
}
