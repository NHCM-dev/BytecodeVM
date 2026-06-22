package nhcm.bytecodevm.Utils;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.CodeSizeEvaluator;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileUtils
{
    public static int estimateMaxSize(MethodNode method)
    {
        CodeSizeEvaluator evaluator = new CodeSizeEvaluator(null);
        method.accept(evaluator);
        return evaluator.getMaxSize();
    }

    public static void fastExportClassNode(ClassNode classNode, Path path) throws IOException
    {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        classNode.accept(cw);
        Files.write(path, cw.toByteArray());
    }

    public static ClassNode fastReadClassNode(Path path) throws IOException
    {
        ClassReader cr = new ClassReader(Files.readAllBytes(path));
        ClassNode cn = new ClassNode();
        cr.accept(cn, ClassReader.SKIP_FRAMES);
        return cn;
    }
}
