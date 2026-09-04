package nhcm.bytecodevm.generator.editor.inlining;

import nhcm.bytecodevm.generator.GeneratedMemberNamer;
import nhcm.bytecodevm.utils.RandomUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Generates the weak identity key and stale-entry cleanup used by instance field storage. */
final class WeakIdentitySupportGenerator
{
    static final String CONSTRUCTOR_DESCRIPTOR =
            "(Ljava/lang/Object;ILjava/lang/ref/ReferenceQueue;)V";
    static final String DRAIN_DESCRIPTOR =
            "(Ljava/lang/ref/ReferenceQueue;Ljava/util/concurrent/ConcurrentMap;" +
            "Ljava/util/concurrent/ConcurrentMap;)V";

    private WeakIdentitySupportGenerator()
    {
    }

    static GeneratedSupport generate(String className, GeneratedMemberNamer namer)
    {
        String hashField = namer.field(className, "$vm$identityHash");
        String slotField = namer.field(className, "$vm$fieldSlot");
        String drainMethod = namer.method(className, "$vm$drainWeakEntries", DRAIN_DESCRIPTOR);
        namer.reserveMethodName(className, "hashCode", "()I");
        namer.reserveMethodName(className, "equals", "(Ljava/lang/Object;)Z");
        CleanupCipher cipher = CleanupCipher.random();

        ClassNode generated = new ClassNode();
        generated.version = Opcodes.V17;
        generated.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL |
                           Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC;
        generated.name = className;
        generated.superName = "java/lang/ref/WeakReference";
        generated.fields.add(new FieldNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
                hashField,
                "I",
                null,
                null));
        generated.fields.add(new FieldNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
                slotField,
                "I",
                null,
                null));
        generated.methods.add(constructor(className, hashField, slotField));
        generated.methods.add(hashCodeMethod(className, hashField));
        generated.methods.add(equalsMethod(className, slotField));
        generated.methods.add(drainMethod(className, drainMethod, cipher));
        return new GeneratedSupport(generated, className, drainMethod, cipher);
    }

    private static MethodNode constructor(String owner, String hashField, String slotField)
    {
        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                "<init>",
                CONSTRUCTOR_DESCRIPTOR,
                null,
                null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                "java/lang/ref/WeakReference",
                "<init>",
                "(Ljava/lang/Object;Ljava/lang/ref/ReferenceQueue;)V",
                false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "java/lang/System",
                "identityHashCode",
                "(Ljava/lang/Object;)I",
                false));
        method.instructions.add(new LdcInsnNode(31));
        method.instructions.add(new InsnNode(Opcodes.IMUL));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, owner, hashField, "I"));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, owner, slotField, "I"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxStack = 4;
        method.maxLocals = 4;
        return method;
    }

    private static MethodNode hashCodeMethod(String owner, String hashField)
    {
        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                "hashCode",
                "()I",
                null,
                null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, owner, hashField, "I"));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxStack = 1;
        method.maxLocals = 1;
        return method;
    }

    private static MethodNode equalsMethod(String owner, String slotField)
    {
        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                "equals",
                "(Ljava/lang/Object;)Z",
                null,
                null);
        LabelNode typeMatches = new LabelNode();
        LabelNode slotMatches = new LabelNode();
        LabelNode referentPresent = new LabelNode();
        LabelNode same = new LabelNode();
        LabelNode different = new LabelNode();

        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IF_ACMPEQ, same));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new TypeInsnNode(Opcodes.INSTANCEOF, owner));
        method.instructions.add(new JumpInsnNode(Opcodes.IFNE, typeMatches));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, different));

        method.instructions.add(typeMatches);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, owner, slotField, "I"));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, owner));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, owner, slotField, "I"));
        method.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, slotMatches));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, different));

        method.instructions.add(slotMatches);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/ref/Reference",
                "get",
                "()Ljava/lang/Object;",
                false));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 2));
        method.instructions.add(new JumpInsnNode(Opcodes.IFNONNULL, referentPresent));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, different));

        method.instructions.add(referentPresent);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, owner));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/ref/Reference",
                "get",
                "()Ljava/lang/Object;",
                false));
        method.instructions.add(new JumpInsnNode(Opcodes.IF_ACMPEQ, same));

        method.instructions.add(different);
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(same);
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxStack = 2;
        method.maxLocals = 3;
        return method;
    }

    private static MethodNode drainMethod(String owner, String name, CleanupCipher cipher)
    {
        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                name,
                DRAIN_DESCRIPTOR,
                null,
                null);
        LabelNode loop = new LabelNode();
        LabelNode hasRecord = new LabelNode();
        LabelNode hasCleanupToken = new LabelNode();
        LabelNode done = new LabelNode();

        method.instructions.add(loop);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/ref/ReferenceQueue",
                "poll",
                "()Ljava/lang/ref/Reference;",
                false));
        method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, owner));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 3));
        method.instructions.add(new JumpInsnNode(Opcodes.IFNULL, done));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEINTERFACE,
                "java/util/concurrent/ConcurrentMap",
                "remove",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                true));
        method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, "[Ljava/lang/Object;"));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 4));
        method.instructions.add(new JumpInsnNode(Opcodes.IFNONNULL, hasRecord));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, loop));

        method.instructions.add(hasRecord);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 4));
        method.instructions.add(new InsnNode(Opcodes.ARRAYLENGTH));
        method.instructions.add(new InsnNode(Opcodes.ICONST_3));
        method.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPLT, loop));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 4));
        method.instructions.add(new InsnNode(Opcodes.ICONST_2));
        method.instructions.add(new InsnNode(Opcodes.AALOAD));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new JumpInsnNode(Opcodes.IFNONNULL, hasCleanupToken));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, loop));

        method.instructions.add(hasCleanupToken);
        method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Long"));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/Long",
                "longValue",
                "()J",
                false));
        method.instructions.add(new LdcInsnNode(cipher.addend));
        method.instructions.add(new InsnNode(Opcodes.LSUB));
        method.instructions.add(new LdcInsnNode(cipher.inverseMultiplier));
        method.instructions.add(new InsnNode(Opcodes.LMUL));
        method.instructions.add(new LdcInsnNode(cipher.rotation));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "java/lang/Long",
                "rotateRight",
                "(JI)J",
                false));
        method.instructions.add(new LdcInsnNode(cipher.key));
        method.instructions.add(new InsnNode(Opcodes.LXOR));
        method.instructions.add(new VarInsnNode(Opcodes.LSTORE, 5));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 5));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "java/lang/Long",
                "valueOf",
                "(J)Ljava/lang/Long;",
                false));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEINTERFACE,
                "java/util/concurrent/ConcurrentMap",
                "remove",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                true));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, loop));
        method.instructions.add(done);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxStack = 5;
        method.maxLocals = 7;
        return method;
    }

    private static long randomLong()
    {
        return ((long) RandomUtils.randomInt() << 32) |
               (RandomUtils.randomInt() & 0xFFFF_FFFFL);
    }

    private static long inverseOdd(long value)
    {
        long inverse = value;
        for (int iteration = 0; iteration < 6; iteration++)
        {
            inverse *= 2L - value * inverse;
        }
        return inverse;
    }

    record GeneratedSupport(
            ClassNode classNode,
            String className,
            String drainMethod,
            CleanupCipher cipher)
    {
    }

    record CleanupCipher(
            long key,
            long multiplier,
            long inverseMultiplier,
            long addend,
            int rotation)
    {
        private static CleanupCipher random()
        {
            long multiplier = randomLong() | 1L;
            return new CleanupCipher(
                    randomLong(),
                    multiplier,
                    inverseOdd(multiplier),
                    randomLong(),
                    7 + Math.floorMod(RandomUtils.randomInt(), 51));
        }
    }
}
