package nhcm.bytecodevm.Generator;

import org.objectweb.asm.tree.ClassNode;

@FunctionalInterface
public interface Generator
{
    ClassNode generate();
}
