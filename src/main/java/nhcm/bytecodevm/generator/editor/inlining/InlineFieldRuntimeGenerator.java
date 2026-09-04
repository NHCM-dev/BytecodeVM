package nhcm.bytecodevm.generator.editor.inlining;

import nhcm.bytecodevm.generator.GeneratedMemberNamer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.LinkedHashSet;
import java.util.Set;

/** Generates a field-only encrypted storage class. Crypto is emitted at each original access site. */
public final class InlineFieldRuntimeGenerator
{
    private InlineFieldRuntimeGenerator()
    {
    }

    public static GeneratedRuntime generate(String className, GeneratedMemberNamer namer)
    {
        ClassNode generated = new ClassNode();
        generated.version = Opcodes.V17;
        generated.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC;
        generated.name = className;
        generated.superName = "java/lang/Object";
        generated.methods.add(privateConstructor());

        MethodNode clinit = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.instructions.add(new InsnNode(Opcodes.RETURN));
        generated.methods.add(clinit);
        return new GeneratedRuntime(generated, className, namer, clinit);
    }

    private static MethodNode privateConstructor()
    {
        MethodNode constructor = new MethodNode(Opcodes.ACC_PRIVATE, "<init>", "()V", null, null);
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructor.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                "java/lang/Object",
                "<init>",
                "()V",
                false));
        constructor.instructions.add(new InsnNode(Opcodes.RETURN));
        return constructor;
    }

    public static final class GeneratedRuntime
    {
        private static final String INSTANCE_DESCRIPTOR = "Ljava/util/Map;";
        private static final String STATIC_DESCRIPTOR = "[Ljava/lang/Object;";

        private final ClassNode classNode;
        private final String className;
        private final GeneratedMemberNamer namer;
        private final MethodNode clinit;
        private final Set<String> occupied = new LinkedHashSet<>();
        private int sequence;

        private GeneratedRuntime(
                ClassNode classNode,
                String className,
                GeneratedMemberNamer namer,
                MethodNode clinit)
        {
            this.classNode = classNode;
            this.className = className;
            this.namer = namer;
            this.clinit = clinit;
        }

        public Storage allocate(boolean isStatic)
        {
            String descriptor = isStatic ? STATIC_DESCRIPTOR : INSTANCE_DESCRIPTOR;
            String name;
            do
            {
                name = namer.field(className, "$vm$encrypted$" + sequence++);
            } while (!occupied.add(name + descriptor));

            int access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC;
            access |= isStatic ? Opcodes.ACC_VOLATILE : Opcodes.ACC_FINAL;
            classNode.fields.add(new FieldNode(access, name, descriptor, null, null));
            if (!isStatic)
            {
                InsnList initialization = new InsnList();
                initialization.add(new TypeInsnNode(Opcodes.NEW, "java/util/IdentityHashMap"));
                initialization.add(new InsnNode(Opcodes.DUP));
                initialization.add(new MethodInsnNode(
                        Opcodes.INVOKESPECIAL,
                        "java/util/IdentityHashMap",
                        "<init>",
                        "()V",
                        false));
                initialization.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "java/util/Collections",
                        "synchronizedMap",
                        "(Ljava/util/Map;)Ljava/util/Map;",
                        false));
                initialization.add(new FieldInsnNode(
                        Opcodes.PUTSTATIC,
                        className,
                        name,
                        descriptor));
                clinit.instructions.insertBefore(clinit.instructions.getLast(), initialization);
            }
            return new Storage(className, name, descriptor, isStatic);
        }

        public ClassNode classNode()
        {
            return classNode;
        }
    }

    public record Storage(String owner, String name, String descriptor, boolean isStatic)
    {
    }
}
