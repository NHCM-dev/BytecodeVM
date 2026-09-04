package nhcm.bytecodevm.generator.editor.inlining;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Captures field-layout requirements before constant pre-encryption rewrites source literals. */
public final class InlineFieldCompatibility
{
    private final Set<String> reflectedFieldNames;

    private InlineFieldCompatibility(Set<String> reflectedFieldNames)
    {
        this.reflectedFieldNames = Set.copyOf(reflectedFieldNames);
    }

    public static InlineFieldCompatibility analyze(Collection<ClassNode> classes)
    {
        Set<String> reflectedNames = new LinkedHashSet<>();
        for (ClassNode owner : classes)
        {
            for (MethodNode method : owner.methods)
            {
                for (AbstractInsnNode instruction : method.instructions)
                {
                    if (!(instruction instanceof MethodInsnNode call) ||
                        !"java/lang/Class".equals(call.owner) ||
                        !("getField".equals(call.name) || "getDeclaredField".equals(call.name)) ||
                        !"(Ljava/lang/String;)Ljava/lang/reflect/Field;".equals(call.desc))
                    {
                        continue;
                    }
                    String name = nearestStringConstant(call.getPrevious());
                    if (name != null)
                    {
                        reflectedNames.add(name);
                    }
                }
            }
        }
        return new InlineFieldCompatibility(reflectedNames);
    }

    boolean reflects(String fieldName)
    {
        return reflectedFieldNames.contains(fieldName);
    }

    private static String nearestStringConstant(AbstractInsnNode instruction)
    {
        for (int remaining = 8; instruction != null && remaining-- > 0;
             instruction = instruction.getPrevious())
        {
            if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof String string)
            {
                return string;
            }
            if (instruction instanceof MethodInsnNode)
            {
                break;
            }
        }
        return null;
    }
}
