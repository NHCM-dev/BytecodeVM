package nhcm.bytecodevm.Transformer;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

public interface Transformer extends Opcodes
{
    ClassNode transform(ClassNode classNode);
}
