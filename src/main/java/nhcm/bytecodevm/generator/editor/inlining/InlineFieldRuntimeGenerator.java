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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Generates a field-only encrypted storage class. Crypto is emitted at each original access site. */
public final class InlineFieldRuntimeGenerator
{
    private InlineFieldRuntimeGenerator()
    {
    }

    public static GeneratedRuntime generate(
            String className,
            String weakKeyClassName,
            GeneratedMemberNamer namer)
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
        return new GeneratedRuntime(generated, className, weakKeyClassName, namer, clinit);
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
        private static final String INSTANCE_DESCRIPTOR = "Ljava/util/concurrent/ConcurrentMap;";
        private static final String STATIC_DESCRIPTOR = "[Ljava/lang/Object;";
        private static final String REFERENCE_QUEUE_DESCRIPTOR = "Ljava/lang/ref/ReferenceQueue;";
        private static final String REFERENCE_VAULT_DESCRIPTOR = "Ljava/util/concurrent/ConcurrentMap;";

        private final ClassNode classNode;
        private final String className;
        private final String weakKeyClassName;
        private final GeneratedMemberNamer namer;
        private final MethodNode clinit;
        private final Set<String> occupied = new LinkedHashSet<>();
        private final Map<String, String> initializationTriggers = new LinkedHashMap<>();
        private int sequence;
        private int instanceSlot;
        private String instanceMapName;
        private String referenceQueueName;
        private String referenceVaultName;
        private WeakIdentitySupportGenerator.GeneratedSupport weakSupport;

        private GeneratedRuntime(
                ClassNode classNode,
                String className,
                String weakKeyClassName,
                GeneratedMemberNamer namer,
                MethodNode clinit)
        {
            this.classNode = classNode;
            this.className = className;
            this.weakKeyClassName = weakKeyClassName;
            this.namer = namer;
            this.clinit = clinit;
        }

        public Storage allocate(ClassNode targetOwner, boolean isStatic, boolean reference)
        {
            if (!isStatic)
            {
                ensureInstanceStorage();
                return new Storage(
                        className,
                        instanceMapName,
                        INSTANCE_DESCRIPTOR,
                        false,
                        reference,
                        referenceVaultName,
                        referenceQueueName,
                        instanceSlot++,
                        weakSupport.className(),
                        weakSupport.drainMethod(),
                        weakSupport.cipher(),
                        null,
                        null);
            }

            String name = uniqueFieldName("$vm$encrypted$");
            classNode.fields.add(new FieldNode(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC |
                    Opcodes.ACC_VOLATILE | Opcodes.ACC_SYNTHETIC,
                    name,
                    STATIC_DESCRIPTOR,
                    null,
                    null));
            return new Storage(
                    className,
                    name,
                    STATIC_DESCRIPTOR,
                    true,
                    reference,
                    reference ? referenceVault() : null,
                    null,
                    -1,
                    null,
                    null,
                    null,
                    targetOwner.name,
                    initializationTrigger(targetOwner));
        }

        private void ensureInstanceStorage()
        {
            if (instanceMapName != null)
            {
                return;
            }
            weakSupport = WeakIdentitySupportGenerator.generate(weakKeyClassName, namer);
            instanceMapName = uniqueFieldName("$vm$weak$instances$");
            referenceQueueName = uniqueFieldName("$vm$weak$queue$");
            referenceVault();

            int access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC |
                         Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC;
            classNode.fields.add(new FieldNode(
                    access,
                    instanceMapName,
                    INSTANCE_DESCRIPTOR,
                    null,
                    null));
            classNode.fields.add(new FieldNode(
                    access,
                    referenceQueueName,
                    REFERENCE_QUEUE_DESCRIPTOR,
                    null,
                    null));

            InsnList initialization = new InsnList();
            initialization.add(new TypeInsnNode(Opcodes.NEW, "java/util/concurrent/ConcurrentHashMap"));
            initialization.add(new InsnNode(Opcodes.DUP));
            initialization.add(new MethodInsnNode(
                    Opcodes.INVOKESPECIAL,
                    "java/util/concurrent/ConcurrentHashMap",
                    "<init>",
                    "()V",
                    false));
            initialization.add(new FieldInsnNode(
                    Opcodes.PUTSTATIC,
                    className,
                    instanceMapName,
                    INSTANCE_DESCRIPTOR));
            initialization.add(new TypeInsnNode(Opcodes.NEW, "java/lang/ref/ReferenceQueue"));
            initialization.add(new InsnNode(Opcodes.DUP));
            initialization.add(new MethodInsnNode(
                    Opcodes.INVOKESPECIAL,
                    "java/lang/ref/ReferenceQueue",
                    "<init>",
                    "()V",
                    false));
            initialization.add(new FieldInsnNode(
                    Opcodes.PUTSTATIC,
                    className,
                    referenceQueueName,
                    REFERENCE_QUEUE_DESCRIPTOR));
            clinit.instructions.insertBefore(clinit.instructions.getLast(), initialization);
        }

        private String uniqueFieldName(String prefix)
        {
            String name;
            do
            {
                name = namer.field(className, prefix + sequence++);
            } while (!occupied.add(name));
            return name;
        }

        private String initializationTrigger(ClassNode targetOwner)
        {
            return initializationTriggers.computeIfAbsent(targetOwner.name, ignored ->
            {
                String name = namer.field(targetOwner.name, "$vm$inline$initialized");
                targetOwner.fields.add(new FieldNode(
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                        name,
                        "I",
                        null,
                        null));
                return name;
            });
        }

        private String referenceVault()
        {
            if (referenceVaultName != null)
            {
                return referenceVaultName;
            }
            referenceVaultName = uniqueFieldName("$vm$reference$");

            classNode.fields.add(new FieldNode(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
                    referenceVaultName,
                    REFERENCE_VAULT_DESCRIPTOR,
                    null,
                    null));
            InsnList initialization = new InsnList();
            initialization.add(new TypeInsnNode(Opcodes.NEW, "java/util/concurrent/ConcurrentHashMap"));
            initialization.add(new InsnNode(Opcodes.DUP));
            initialization.add(new MethodInsnNode(
                    Opcodes.INVOKESPECIAL,
                    "java/util/concurrent/ConcurrentHashMap",
                    "<init>",
                    "()V",
                    false));
            initialization.add(new FieldInsnNode(
                    Opcodes.PUTSTATIC,
                    className,
                    referenceVaultName,
                    REFERENCE_VAULT_DESCRIPTOR));
            clinit.instructions.insertBefore(clinit.instructions.getLast(), initialization);
            return referenceVaultName;
        }

        public List<ClassNode> generatedClasses()
        {
            List<ClassNode> classes = new ArrayList<>();
            classes.add(classNode);
            if (weakSupport != null)
            {
                classes.add(weakSupport.classNode());
            }
            return List.copyOf(classes);
        }
    }

    public record Storage(
            String owner,
            String name,
            String descriptor,
            boolean isStatic,
            boolean reference,
            String referenceVaultName,
            String referenceQueueName,
            int fieldSlot,
            String weakKeyClassName,
            String drainMethodName,
            WeakIdentitySupportGenerator.CleanupCipher cleanupCipher,
            String initializationOwner,
            String initializationTriggerName)
    {
        public boolean storesReference()
        {
            return reference;
        }

        public boolean usesWeakIdentity()
        {
            return !isStatic;
        }
    }
}
