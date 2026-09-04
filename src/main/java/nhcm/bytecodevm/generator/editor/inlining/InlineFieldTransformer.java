package nhcm.bytecodevm.generator.editor.inlining;

import nhcm.bytecodevm.config.BytecodeVMConfig;
import nhcm.bytecodevm.config.sdk.SdkAnnotationReader;
import nhcm.bytecodevm.utils.RandomUtils;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Removes selected fields and expands storage plus encryption directly at every original field access.
 * The generated runtime owns storage fields only; it has no accessor or crypto methods.
 */
public final class InlineFieldTransformer
{
    private final BytecodeVMConfig config;
    private final Collection<ClassNode> classes;
    private final InlineFieldRuntimeGenerator.GeneratedRuntime runtime;
    private final InlineFieldCompatibility compatibility;
    private final ClassHierarchyResolver hierarchy;
    private final Map<FieldId, Candidate> candidates = new LinkedHashMap<>();
    private final Map<FieldId, List<Access>> accesses = new LinkedHashMap<>();
    private final Set<FieldId> handleReferences = new LinkedHashSet<>();
    private final Map<MethodNode, Workspace> workspaces = new IdentityHashMap<>();
    private int unsupportedCandidates;

    public InlineFieldTransformer(
            BytecodeVMConfig config,
            Collection<ClassNode> classes,
            InlineFieldRuntimeGenerator.GeneratedRuntime runtime,
            InlineFieldCompatibility compatibility)
    {
        this.config = config;
        this.classes = classes;
        this.runtime = runtime;
        this.compatibility = compatibility;
        this.hierarchy = new ClassHierarchyResolver(classes);
        collectCandidates();
        collectAccesses();
    }

    public Set<MethodNode> referencedMethods()
    {
        Set<MethodNode> methods = Collections.newSetFromMap(new IdentityHashMap<>());
        for (List<Access> fieldAccesses : accesses.values())
        {
            for (Access access : fieldAccesses)
            {
                if (!"<init>".equals(access.method.name) && !"<clinit>".equals(access.method.name))
                {
                    methods.add(access.method);
                }
            }
        }
        return methods;
    }

    public Result transform(Set<MethodNode> protectedMethods)
    {
        int inlined = 0;
        int rewritten = 0;
        int skipped = unsupportedCandidates;

        for (Candidate candidate : candidates.values())
        {
            List<Access> fieldAccesses = accesses.getOrDefault(candidate.id, List.of());
            boolean safe = !handleReferences.contains(candidate.id);
            for (Access access : fieldAccesses)
            {
                if ("<init>".equals(access.method.name) || "<clinit>".equals(access.method.name))
                {
                    continue;
                }
                if (!protectedMethods.contains(access.method))
                {
                    safe = false;
                    break;
                }
            }
            if (!safe)
            {
                skipped++;
                continue;
            }

            boolean isStatic = (candidate.field.access & Opcodes.ACC_STATIC) != 0;
            InlineFieldRuntimeGenerator.Storage storage = runtime.allocate(isStatic);
            EncryptionBinding binding = EncryptionBinding.random();
            Type type = Type.getType(candidate.field.desc);
            if (candidate.field.value != null)
            {
                addConstantInitializer(candidate, storage, binding, type);
                candidate.field.value = null;
            }
            for (Access access : fieldAccesses)
            {
                rewrite(access, storage, binding, type);
                rewritten++;
            }
            candidate.owner.fields.remove(candidate.field);
            inlined++;
        }

        ClassNode runtimeClass = inlined == 0 ? null : runtime.classNode();
        return new Result(runtimeClass, inlined, rewritten, skipped);
    }

    private void collectCandidates()
    {
        for (ClassNode owner : classes)
        {
            if ((owner.access & (Opcodes.ACC_ENUM | Opcodes.ACC_RECORD)) != 0)
            {
                continue;
            }
            SdkAnnotationReader.ClassDirectives classSdk = SdkAnnotationReader.classDirectives(owner);
            if (classSdk.excluded())
            {
                continue;
            }
            for (FieldNode field : owner.fields)
            {
                if (config.matchRules.fieldExcluded("all", owner, field) ||
                    (!classSdk.included() && !config.matchRules.statementMatches("all", owner, field)))
                {
                    continue;
                }
                if ((field.access & (Opcodes.ACC_VOLATILE | Opcodes.ACC_ENUM)) != 0)
                {
                    continue;
                }
                SdkAnnotationReader.FieldDirectives sdk = SdkAnnotationReader.fieldDirectives(owner, field);
                boolean fieldAnnotation = Boolean.TRUE.equals(sdk.inlineField());
                boolean finalAnnotation = Boolean.TRUE.equals(sdk.inlineFinal());
                boolean regular = enabled(
                        config.inlineFields &&
                                (!config.annotationOnly || fieldAnnotation) &&
                                config.matchRules.statementMatches("inlineFields", owner, field),
                        sdk.inlineField(),
                        "inlineFields",
                        owner,
                        field);
                boolean staticFinal = (field.access & (Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)) ==
                                      (Opcodes.ACC_STATIC | Opcodes.ACC_FINAL);
                boolean inlineFinal = staticFinal && enabled(
                        config.inlineStaticFinals &&
                                (!config.annotationOnly || finalAnnotation) &&
                                config.matchRules.statementMatches("inlineStaticFinals", owner, field),
                        sdk.inlineFinal(),
                        "inlineStaticFinals",
                        owner,
                        field);
                if (!regular && !inlineFinal)
                {
                    continue;
                }
                boolean explicit = fieldAnnotation || finalAnnotation;
                if (config.privateFieldOnly && (field.access & Opcodes.ACC_PRIVATE) == 0 && !explicit)
                {
                    continue;
                }
                if (!supported(field.desc))
                {
                    unsupportedCandidates++;
                    continue;
                }
                boolean instance = (field.access & Opcodes.ACC_STATIC) == 0;
                if (compatibility.reflects(field.name) ||
                    hierarchy.isAssignableTo(owner.name, "java/io/Serializable") ||
                    hierarchy.isAssignableTo(owner.name, "java/io/Externalizable") ||
                    instance && hierarchy.hasCloneableSubtype(owner.name))
                {
                    unsupportedCandidates++;
                    continue;
                }
                FieldId id = new FieldId(owner.name, field.name, field.desc);
                candidates.put(id, new Candidate(id, owner, field));
            }
        }
    }

    private void collectAccesses()
    {
        for (ClassNode owner : classes)
        {
            for (MethodNode method : owner.methods)
            {
                for (AbstractInsnNode instruction : method.instructions)
                {
                    if (instruction instanceof LdcInsnNode ldc)
                    {
                        collectHandle(ldc.cst);
                    }
                    else if (instruction instanceof InvokeDynamicInsnNode dynamic)
                    {
                        collectHandle(dynamic.bsm);
                        for (Object argument : dynamic.bsmArgs)
                        {
                            collectHandle(argument);
                        }
                    }
                    if (instruction instanceof FieldInsnNode fieldInsn)
                    {
                        FieldId id = resolvedFieldId(
                                fieldInsn.owner,
                                fieldInsn.name,
                                fieldInsn.desc,
                                fieldInsn.getOpcode() == Opcodes.GETSTATIC ||
                                fieldInsn.getOpcode() == Opcodes.PUTSTATIC);
                        if (candidates.containsKey(id))
                        {
                            accesses.computeIfAbsent(id, ignored -> new ArrayList<>())
                                    .add(new Access(owner, method, fieldInsn));
                        }
                    }
                }
            }
        }
    }

    private void collectHandle(Object value)
    {
        if (value instanceof Handle handle)
        {
            int tag = handle.getTag();
            if (tag == Opcodes.H_GETFIELD || tag == Opcodes.H_GETSTATIC ||
                tag == Opcodes.H_PUTFIELD || tag == Opcodes.H_PUTSTATIC)
            {
                FieldId id = resolvedFieldId(
                        handle.getOwner(),
                        handle.getName(),
                        handle.getDesc(),
                        tag == Opcodes.H_GETSTATIC || tag == Opcodes.H_PUTSTATIC);
                if (candidates.containsKey(id))
                {
                    handleReferences.add(id);
                }
            }
        }
        else if (value instanceof ConstantDynamic dynamic)
        {
            collectHandle(dynamic.getBootstrapMethod());
            for (int index = 0; index < dynamic.getBootstrapMethodArgumentCount(); index++)
            {
                collectHandle(dynamic.getBootstrapMethodArgument(index));
            }
        }
    }

    private FieldId resolvedFieldId(
            String symbolicOwner,
            String name,
            String descriptor,
            boolean staticAccess)
    {
        ClassHierarchyResolver.FieldDeclaration declaration = hierarchy.resolveField(
                symbolicOwner,
                name,
                descriptor,
                staticAccess);
        return declaration == null
                ? new FieldId(symbolicOwner, name, descriptor)
                : new FieldId(declaration.owner().name, name, descriptor);
    }

    private void rewrite(
            Access access,
            InlineFieldRuntimeGenerator.Storage storage,
            EncryptionBinding binding,
            Type type)
    {
        FieldInsnNode field = access.instruction;
        Workspace workspace = workspace(access.method);
        InsnList replacement = new InsnList();
        switch (field.getOpcode())
        {
            case Opcodes.GETFIELD ->
            {
                replacement.add(new VarInsnNode(Opcodes.ASTORE, workspace.owner));
                emitRead(replacement, storage, binding, type, workspace, true);
            }
            case Opcodes.PUTFIELD ->
            {
                replacement.add(new VarInsnNode(type.getOpcode(Opcodes.ISTORE), workspace.value));
                replacement.add(new VarInsnNode(Opcodes.ASTORE, workspace.owner));
                emitWrite(replacement, storage, binding, type, workspace, true);
            }
            case Opcodes.GETSTATIC ->
                    emitRead(replacement, storage, binding, type, workspace, false);
            case Opcodes.PUTSTATIC ->
            {
                replacement.add(new VarInsnNode(type.getOpcode(Opcodes.ISTORE), workspace.value));
                emitWrite(replacement, storage, binding, type, workspace, false);
            }
            default -> throw new IllegalStateException("Unknown field opcode: " + field.getOpcode());
        }
        access.method.instructions.insertBefore(field, replacement);
        access.method.instructions.remove(field);
        access.method.maxStack = Math.max(access.method.maxStack + 12, 24);
    }

    private void addConstantInitializer(
            Candidate candidate,
            InlineFieldRuntimeGenerator.Storage storage,
            EncryptionBinding binding,
            Type type)
    {
        MethodNode clinit = findOrCreateClinit(candidate.owner);
        Workspace workspace = workspace(clinit);
        InsnList initialization = new InsnList();
        initialization.add(new LdcInsnNode(candidate.field.value));
        initialization.add(new VarInsnNode(type.getOpcode(Opcodes.ISTORE), workspace.value));
        emitWrite(initialization, storage, binding, type, workspace, false);
        clinit.instructions.insert(initialization);
        clinit.maxStack = Math.max(clinit.maxStack + 12, 24);
    }

    private static void emitWrite(
            InsnList out,
            InlineFieldRuntimeGenerator.Storage storage,
            EncryptionBinding binding,
            Type type,
            Workspace workspace,
            boolean instance)
    {
        pushInt(out, 2);
        out.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        out.add(new VarInsnNode(Opcodes.ASTORE, workspace.record));
        out.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "java/util/concurrent/ThreadLocalRandom",
                "current",
                "()Ljava/util/concurrent/ThreadLocalRandom;",
                false));
        out.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "java/util/concurrent/ThreadLocalRandom",
                "nextLong",
                "()J",
                false));
        out.add(new VarInsnNode(Opcodes.LSTORE, workspace.nonce));

        out.add(new VarInsnNode(Opcodes.ALOAD, workspace.record));
        pushInt(out, 0);
        out.add(new VarInsnNode(Opcodes.LLOAD, workspace.nonce));
        out.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "java/lang/Long",
                "valueOf",
                "(J)Ljava/lang/Long;",
                false));
        out.add(new InsnNode(Opcodes.AASTORE));

        if (type.getSort() == Type.OBJECT)
        {
            emitStringWrite(out, binding, workspace);
        }
        else
        {
            out.add(new VarInsnNode(Opcodes.ALOAD, workspace.record));
            pushInt(out, 1);
            emitPrimitiveBits(out, type, workspace.value);
            emitMask(out, binding, workspace.nonce);
            out.add(new InsnNode(Opcodes.LXOR));
            out.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "java/lang/Long",
                    "valueOf",
                    "(J)Ljava/lang/Long;",
                    false));
            out.add(new InsnNode(Opcodes.AASTORE));
        }

        if (instance)
        {
            out.add(new FieldInsnNode(
                    Opcodes.GETSTATIC,
                    storage.owner(),
                    storage.name(),
                    storage.descriptor()));
            out.add(new VarInsnNode(Opcodes.ALOAD, workspace.owner));
            out.add(new VarInsnNode(Opcodes.ALOAD, workspace.record));
            out.add(new MethodInsnNode(
                    Opcodes.INVOKEINTERFACE,
                    "java/util/Map",
                    "put",
                    "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                    true));
            out.add(new InsnNode(Opcodes.POP));
        }
        else
        {
            out.add(new VarInsnNode(Opcodes.ALOAD, workspace.record));
            out.add(new FieldInsnNode(
                    Opcodes.PUTSTATIC,
                    storage.owner(),
                    storage.name(),
                    storage.descriptor()));
        }
    }

    private static void emitStringWrite(
            InsnList out,
            EncryptionBinding binding,
            Workspace workspace)
    {
        LabelNode isNull = new LabelNode();
        LabelNode loop = new LabelNode();
        LabelNode loopEnd = new LabelNode();
        LabelNode done = new LabelNode();
        out.add(new VarInsnNode(Opcodes.ALOAD, workspace.value));
        out.add(new JumpInsnNode(Opcodes.IFNULL, isNull));
        out.add(new VarInsnNode(Opcodes.ALOAD, workspace.value));
        out.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/String"));
        out.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/String",
                "toCharArray",
                "()[C",
                false));
        out.add(new VarInsnNode(Opcodes.ASTORE, workspace.source));
        out.add(new VarInsnNode(Opcodes.ALOAD, workspace.source));
        out.add(new InsnNode(Opcodes.ARRAYLENGTH));
        out.add(new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_LONG));
        out.add(new VarInsnNode(Opcodes.ASTORE, workspace.encrypted));
        pushInt(out, 0);
        out.add(new VarInsnNode(Opcodes.ISTORE, workspace.index));
        out.add(loop);
        out.add(new VarInsnNode(Opcodes.ILOAD, workspace.index));
        out.add(new VarInsnNode(Opcodes.ALOAD, workspace.source));
        out.add(new InsnNode(Opcodes.ARRAYLENGTH));
        out.add(new JumpInsnNode(Opcodes.IF_ICMPGE, loopEnd));
        out.add(new VarInsnNode(Opcodes.ALOAD, workspace.encrypted));
        out.add(new VarInsnNode(Opcodes.ILOAD, workspace.index));
        out.add(new VarInsnNode(Opcodes.ALOAD, workspace.source));
        out.add(new VarInsnNode(Opcodes.ILOAD, workspace.index));
        out.add(new InsnNode(Opcodes.CALOAD));
        out.add(new InsnNode(Opcodes.I2L));
        emitIndexedMask(out, binding, workspace);
        out.add(new InsnNode(Opcodes.LXOR));
        out.add(new InsnNode(Opcodes.LASTORE));
        out.add(new IincInsnNode(workspace.index, 1));
        out.add(new JumpInsnNode(Opcodes.GOTO, loop));
        out.add(loopEnd);
        out.add(new VarInsnNode(Opcodes.ALOAD, workspace.record));
        pushInt(out, 1);
        out.add(new VarInsnNode(Opcodes.ALOAD, workspace.encrypted));
        out.add(new InsnNode(Opcodes.AASTORE));
        out.add(new JumpInsnNode(Opcodes.GOTO, done));
        out.add(isNull);
        out.add(new VarInsnNode(Opcodes.ALOAD, workspace.record));
        pushInt(out, 1);
        out.add(new InsnNode(Opcodes.ACONST_NULL));
        out.add(new InsnNode(Opcodes.AASTORE));
        out.add(done);
    }

    private static void emitRead(
            InsnList out,
            InlineFieldRuntimeGenerator.Storage storage,
            EncryptionBinding binding,
            Type type,
            Workspace workspace,
            boolean instance)
    {
        if (instance)
        {
            out.add(new FieldInsnNode(
                    Opcodes.GETSTATIC,
                    storage.owner(),
                    storage.name(),
                    storage.descriptor()));
            out.add(new VarInsnNode(Opcodes.ALOAD, workspace.owner));
            out.add(new MethodInsnNode(
                    Opcodes.INVOKEINTERFACE,
                    "java/util/Map",
                    "get",
                    "(Ljava/lang/Object;)Ljava/lang/Object;",
                    true));
            out.add(new TypeInsnNode(Opcodes.CHECKCAST, "[Ljava/lang/Object;"));
        }
        else
        {
            out.add(new FieldInsnNode(
                    Opcodes.GETSTATIC,
                    storage.owner(),
                    storage.name(),
                    storage.descriptor()));
        }
        out.add(new VarInsnNode(Opcodes.ASTORE, workspace.record));

        LabelNode decode = new LabelNode();
        LabelNode done = new LabelNode();
        out.add(new VarInsnNode(Opcodes.ALOAD, workspace.record));
        out.add(new JumpInsnNode(Opcodes.IFNONNULL, decode));
        emitDefault(out, type);
        out.add(new JumpInsnNode(Opcodes.GOTO, done));
        out.add(decode);
        out.add(new VarInsnNode(Opcodes.ALOAD, workspace.record));
        pushInt(out, 0);
        out.add(new InsnNode(Opcodes.AALOAD));
        out.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Long"));
        out.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/Long",
                "longValue",
                "()J",
                false));
        out.add(new VarInsnNode(Opcodes.LSTORE, workspace.nonce));
        if (type.getSort() == Type.OBJECT)
        {
            emitStringRead(out, binding, workspace);
        }
        else
        {
            out.add(new VarInsnNode(Opcodes.ALOAD, workspace.record));
            pushInt(out, 1);
            out.add(new InsnNode(Opcodes.AALOAD));
            out.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Long"));
            out.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    "java/lang/Long",
                    "longValue",
                    "()J",
                    false));
            emitMask(out, binding, workspace.nonce);
            out.add(new InsnNode(Opcodes.LXOR));
            emitPrimitiveValue(out, type);
        }
        out.add(done);
    }

    private static void emitStringRead(
            InsnList out,
            EncryptionBinding binding,
            Workspace workspace)
    {
        LabelNode notNull = new LabelNode();
        LabelNode loop = new LabelNode();
        LabelNode loopEnd = new LabelNode();
        LabelNode done = new LabelNode();
        out.add(new VarInsnNode(Opcodes.ALOAD, workspace.record));
        pushInt(out, 1);
        out.add(new InsnNode(Opcodes.AALOAD));
        out.add(new InsnNode(Opcodes.DUP));
        out.add(new JumpInsnNode(Opcodes.IFNONNULL, notNull));
        out.add(new InsnNode(Opcodes.POP));
        out.add(new InsnNode(Opcodes.ACONST_NULL));
        out.add(new JumpInsnNode(Opcodes.GOTO, done));
        out.add(notNull);
        out.add(new TypeInsnNode(Opcodes.CHECKCAST, "[J"));
        out.add(new VarInsnNode(Opcodes.ASTORE, workspace.encrypted));
        out.add(new VarInsnNode(Opcodes.ALOAD, workspace.encrypted));
        out.add(new InsnNode(Opcodes.ARRAYLENGTH));
        out.add(new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_CHAR));
        out.add(new VarInsnNode(Opcodes.ASTORE, workspace.decoded));
        pushInt(out, 0);
        out.add(new VarInsnNode(Opcodes.ISTORE, workspace.index));
        out.add(loop);
        out.add(new VarInsnNode(Opcodes.ILOAD, workspace.index));
        out.add(new VarInsnNode(Opcodes.ALOAD, workspace.encrypted));
        out.add(new InsnNode(Opcodes.ARRAYLENGTH));
        out.add(new JumpInsnNode(Opcodes.IF_ICMPGE, loopEnd));
        out.add(new VarInsnNode(Opcodes.ALOAD, workspace.decoded));
        out.add(new VarInsnNode(Opcodes.ILOAD, workspace.index));
        out.add(new VarInsnNode(Opcodes.ALOAD, workspace.encrypted));
        out.add(new VarInsnNode(Opcodes.ILOAD, workspace.index));
        out.add(new InsnNode(Opcodes.LALOAD));
        emitIndexedMask(out, binding, workspace);
        out.add(new InsnNode(Opcodes.LXOR));
        out.add(new InsnNode(Opcodes.L2I));
        out.add(new InsnNode(Opcodes.CASTORE));
        out.add(new IincInsnNode(workspace.index, 1));
        out.add(new JumpInsnNode(Opcodes.GOTO, loop));
        out.add(loopEnd);
        out.add(new TypeInsnNode(Opcodes.NEW, "java/lang/String"));
        out.add(new InsnNode(Opcodes.DUP));
        out.add(new VarInsnNode(Opcodes.ALOAD, workspace.decoded));
        out.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                "java/lang/String",
                "<init>",
                "([C)V",
                false));
        out.add(done);
    }

    private static void emitPrimitiveBits(InsnList out, Type type, int local)
    {
        switch (type.getSort())
        {
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT ->
            {
                out.add(new VarInsnNode(Opcodes.ILOAD, local));
                out.add(new InsnNode(Opcodes.I2L));
            }
            case Type.LONG -> out.add(new VarInsnNode(Opcodes.LLOAD, local));
            case Type.FLOAT ->
            {
                out.add(new VarInsnNode(Opcodes.FLOAD, local));
                out.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Float",
                        "floatToRawIntBits",
                        "(F)I",
                        false));
                out.add(new InsnNode(Opcodes.I2L));
                out.add(new LdcInsnNode(0xFFFF_FFFFL));
                out.add(new InsnNode(Opcodes.LAND));
            }
            case Type.DOUBLE ->
            {
                out.add(new VarInsnNode(Opcodes.DLOAD, local));
                out.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Double",
                        "doubleToRawLongBits",
                        "(D)J",
                        false));
            }
            default -> throw new IllegalArgumentException("Unsupported inline field type: " + type);
        }
    }

    private static void emitPrimitiveValue(InsnList out, Type type)
    {
        switch (type.getSort())
        {
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT ->
                    out.add(new InsnNode(Opcodes.L2I));
            case Type.LONG -> { }
            case Type.FLOAT ->
            {
                out.add(new InsnNode(Opcodes.L2I));
                out.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Float",
                        "intBitsToFloat",
                        "(I)F",
                        false));
            }
            case Type.DOUBLE -> out.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "java/lang/Double",
                    "longBitsToDouble",
                    "(J)D",
                    false));
            default -> throw new IllegalArgumentException("Unsupported inline field type: " + type);
        }
    }

    private static void emitDefault(InsnList out, Type type)
    {
        switch (type.getSort())
        {
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT ->
                    out.add(new InsnNode(Opcodes.ICONST_0));
            case Type.LONG -> out.add(new InsnNode(Opcodes.LCONST_0));
            case Type.FLOAT -> out.add(new InsnNode(Opcodes.FCONST_0));
            case Type.DOUBLE -> out.add(new InsnNode(Opcodes.DCONST_0));
            case Type.OBJECT -> out.add(new InsnNode(Opcodes.ACONST_NULL));
            default -> throw new IllegalArgumentException("Unsupported inline field type: " + type);
        }
    }

    private static void emitIndexedMask(
            InsnList out,
            EncryptionBinding binding,
            Workspace workspace)
    {
        out.add(new VarInsnNode(Opcodes.LLOAD, workspace.nonce));
        out.add(new VarInsnNode(Opcodes.ILOAD, workspace.index));
        out.add(new InsnNode(Opcodes.I2L));
        out.add(new InsnNode(Opcodes.LADD));
        emitMaskTail(out, binding);
    }

    private static void emitMask(
            InsnList out,
            EncryptionBinding binding,
            int nonceLocal)
    {
        out.add(new VarInsnNode(Opcodes.LLOAD, nonceLocal));
        emitMaskTail(out, binding);
    }

    private static void emitMaskTail(InsnList out, EncryptionBinding binding)
    {
        out.add(new LdcInsnNode(binding.key));
        out.add(new InsnNode(Opcodes.LXOR));
        pushInt(out, binding.rotation);
        out.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "java/lang/Long",
                "rotateLeft",
                "(JI)J",
                false));
        out.add(new LdcInsnNode(binding.multiplier));
        out.add(new InsnNode(Opcodes.LMUL));
        out.add(new LdcInsnNode(binding.addend));
        out.add(new InsnNode(Opcodes.LADD));
    }

    private Workspace workspace(MethodNode method)
    {
        return workspaces.computeIfAbsent(method, ignored ->
        {
            Workspace workspace = new Workspace(method.maxLocals);
            method.maxLocals = workspace.limit;
            return workspace;
        });
    }

    private static MethodNode findOrCreateClinit(ClassNode owner)
    {
        for (MethodNode method : owner.methods)
        {
            if ("<clinit>".equals(method.name) && "()V".equals(method.desc))
            {
                return method;
            }
        }
        MethodNode method = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        owner.methods.add(method);
        return method;
    }

    private boolean enabled(
            boolean configured,
            Boolean sdk,
            String group,
            ClassNode owner,
            FieldNode field)
    {
        return sdk == null
                ? configured
                : sdk && !config.matchRules.fieldExcluded(group, owner, field);
    }

    private static boolean supported(String descriptor)
    {
        return descriptor.length() == 1 && "ZBCSIJFD".indexOf(descriptor.charAt(0)) >= 0 ||
               "Ljava/lang/String;".equals(descriptor);
    }

    private static void pushInt(InsnList out, int value)
    {
        out.add(new LdcInsnNode(value));
    }

    private static long randomLong()
    {
        return ((long) RandomUtils.randomInt() << 32) |
               (RandomUtils.randomInt() & 0xFFFF_FFFFL);
    }

    private record FieldId(String owner, String name, String descriptor)
    {
    }

    private record Candidate(FieldId id, ClassNode owner, FieldNode field)
    {
    }

    private record Access(ClassNode owner, MethodNode method, FieldInsnNode instruction)
    {
    }

    private record EncryptionBinding(long key, long multiplier, long addend, int rotation)
    {
        private static EncryptionBinding random()
        {
            long key;
            do
            {
                key = randomLong();
            } while (key == 0L);
            return new EncryptionBinding(
                    key,
                    randomLong() | 1L,
                    randomLong(),
                    7 + Math.floorMod(RandomUtils.randomInt(), 51));
        }
    }

    private static final class Workspace
    {
        private final int owner;
        private final int value;
        private final int record;
        private final int nonce;
        private final int source;
        private final int encrypted;
        private final int decoded;
        private final int index;
        private final int limit;

        private Workspace(int base)
        {
            owner = base;
            value = base + 1;
            record = base + 3;
            nonce = base + 4;
            source = base + 6;
            encrypted = base + 7;
            decoded = base + 8;
            index = base + 9;
            limit = base + 10;
        }
    }

    public record Result(ClassNode runtimeClass, int fields, int accesses, int skipped)
    {
    }
}
