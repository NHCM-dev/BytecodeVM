package nhcm.bytecodevm.generator.editor.inlining;

import nhcm.bytecodevm.config.BytecodeVMConfig;
import nhcm.bytecodevm.config.TargetMatcher;
import nhcm.bytecodevm.config.sdk.SdkAnnotationReader;
import nhcm.bytecodevm.generator.GeneratedMemberNamer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps the JVM-mandated constructor prefix and moves the initialized-this continuation into a
 * private static method. The continuation is later compiled and replaced like any other VM method.
 */
public final class ConstructorVirtualizationTransformer
{
    private static final int MAX_PARAMETER_SLOTS = 255;

    private final BytecodeVMConfig config;
    private final TargetMatcher includes;
    private final TargetMatcher exclusions;
    private final GeneratedMemberNamer namer;
    private final Set<String> securityManagerClasses;
    private final Map<String, Integer> nameOrdinals = new LinkedHashMap<>();

    public ConstructorVirtualizationTransformer(
            BytecodeVMConfig config,
            TargetMatcher includes,
            TargetMatcher exclusions,
            GeneratedMemberNamer namer,
            Set<String> securityManagerClasses)
    {
        this.config = config;
        this.includes = includes;
        this.exclusions = exclusions;
        this.namer = namer;
        this.securityManagerClasses = securityManagerClasses;
    }

    public Result transform(Collection<ClassNode> classes)
    {
        List<Continuation> continuations = new ArrayList<>();
        Map<String, Integer> skippedReasons = new LinkedHashMap<>();
        for (ClassNode owner : classes)
        {
            for (MethodNode method : List.copyOf(owner.methods))
            {
                if (!"<init>".equals(method.name))
                {
                    continue;
                }
                BytecodeVMConfig methodConfig = selectedConfig(owner, method);
                if (methodConfig == null)
                {
                    continue;
                }
                SplitResult split = split(owner, method);
                if (split.continuation() == null)
                {
                    skippedReasons.merge(split.reason(), 1, Integer::sum);
                    continue;
                }
                owner.methods.add(split.continuation());
                continuations.add(new Continuation(
                        owner,
                        method,
                        split.continuation(),
                        methodConfig));
            }
        }
        return new Result(List.copyOf(continuations), Map.copyOf(skippedReasons));
    }

    private BytecodeVMConfig selectedConfig(ClassNode owner, MethodNode method)
    {
        if (securityManagerClasses.contains(owner.name) ||
            (owner.access & (Opcodes.ACC_ENUM | Opcodes.ACC_RECORD)) != 0 ||
            usesStackTraceIntrospection(method))
        {
            return null;
        }
        SdkAnnotationReader.ClassDirectives classSdk = SdkAnnotationReader.classDirectives(owner);
        SdkAnnotationReader.MethodDirectives methodSdk =
                SdkAnnotationReader.methodDirectives(owner, method);
        if (classSdk.excluded() || methodSdk.excluded() ||
            exclusions.isMethodContextMatched(owner, method))
        {
            return null;
        }
        boolean explicitlySelected = methodSdk.selected() ||
                includes.isMethodContextMatched(owner, method);
        if (!explicitlySelected)
        {
            return null;
        }
        if (config.annotationOnly && !methodSdk.methodAnnotation())
        {
            return null;
        }
        BytecodeVMConfig methodConfig = config.forMethod(owner, method);
        return methodConfig.virtualizeConstructors ||
               methodSdk.methodAnnotation() && methodSdk.selected()
                ? methodConfig
                : null;
    }

    private SplitResult split(ClassNode owner, MethodNode constructor)
    {
        if ((constructor.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0 ||
            constructor.instructions == null || constructor.instructions.size() == 0)
        {
            return SplitResult.skipped("NO_CODE");
        }

        Analysis analysis;
        try
        {
            analysis = analyze(owner, constructor);
        }
        catch (AnalyzerException | RuntimeException exception)
        {
            return SplitResult.skipped("ANALYSIS_FAILED");
        }
        if (analysis.initializers().size() != 1)
        {
            return SplitResult.skipped(
                    analysis.initializers().isEmpty() ? "NO_THIS_INITIALIZER" : "MULTIPLE_THIS_INITIALIZERS");
        }

        int splitIndex = analysis.initializers().getFirst();
        if (!hasContinuationBody(analysis.instructions(), splitIndex))
        {
            return SplitResult.skipped("EMPTY_CONTINUATION");
        }
        if (hasCrossBoundaryControlFlow(
                constructor,
                analysis.instructions(),
                analysis.indices(),
                splitIndex))
        {
            return SplitResult.skipped("CROSS_BOUNDARY_CONTROL_FLOW");
        }

        int continuationIndex = firstReachableIndex(analysis.basicFrames(), splitIndex + 1);
        if (continuationIndex < 0)
        {
            return SplitResult.skipped("UNREACHABLE_CONTINUATION");
        }
        Frame<BasicValue> basicFrame = analysis.basicFrames()[continuationIndex];
        if (basicFrame.getStackSize() != 0)
        {
            return SplitResult.skipped("NON_EMPTY_STACK");
        }

        LocalPlan locals = localPlan(
                constructor,
                analysis.instructions(),
                analysis.indices(),
                splitIndex,
                basicFrame,
                analysis.sourceFrames()[continuationIndex]);
        if (locals == null)
        {
            return SplitResult.skipped("UNSAFE_LIVE_LOCALS");
        }

        String descriptor = Type.getMethodDescriptor(Type.VOID_TYPE, locals.parameterTypes());
        String name = continuationName(owner, descriptor);
        MethodNode continuation = new MethodNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                name,
                descriptor,
                null,
                constructor.exceptions == null ? null : constructor.exceptions.toArray(String[]::new));
        continuation.maxLocals = locals.parameterSlots() + constructor.maxLocals;
        continuation.maxStack = constructor.maxStack;

        partitionTryCatchBlocks(constructor, continuation, analysis.indices(), splitIndex);
        moveContinuation(constructor, continuation, analysis.instructions().get(splitIndex));
        isolateContinuationLocals(continuation, locals);
        appendContinuationCall(constructor, owner, continuation, locals);
        clearDebugMetadata(constructor);
        clearDebugMetadata(continuation);
        return SplitResult.success(continuation);
    }

    private static Analysis analyze(ClassNode owner, MethodNode method) throws AnalyzerException
    {
        List<AbstractInsnNode> instructions = new ArrayList<>();
        Map<AbstractInsnNode, Integer> indices = new IdentityHashMap<>();
        int index = 0;
        for (AbstractInsnNode instruction : method.instructions)
        {
            instructions.add(instruction);
            indices.put(instruction, index++);
        }

        Frame<SourceValue>[] sourceFrames =
                new Analyzer<>(new SourceInterpreter()).analyze(owner.name, method);
        Frame<BasicValue>[] basicFrames =
                new Analyzer<>(new BasicInterpreter()).analyze(owner.name, method);
        List<Integer> initializers = new ArrayList<>();
        for (int instructionIndex = 0; instructionIndex < instructions.size(); instructionIndex++)
        {
            AbstractInsnNode instruction = instructions.get(instructionIndex);
            if (!(instruction instanceof MethodInsnNode call) ||
                call.getOpcode() != Opcodes.INVOKESPECIAL ||
                !"<init>".equals(call.name) ||
                !(owner.name.equals(call.owner) || call.owner.equals(owner.superName)))
            {
                continue;
            }
            Frame<SourceValue> frame = sourceFrames[instructionIndex];
            if (frame == null)
            {
                continue;
            }
            int receiverIndex = frame.getStackSize() - Type.getArgumentTypes(call.desc).length - 1;
            if (receiverIndex < 0)
            {
                continue;
            }
            SourceValue receiver = frame.getStack(receiverIndex);
            if (receiver.insns.stream().anyMatch(source ->
                    source instanceof VarInsnNode variable &&
                    variable.getOpcode() == Opcodes.ALOAD &&
                    variable.var == 0))
            {
                initializers.add(instructionIndex);
            }
        }
        return new Analysis(
                instructions,
                indices,
                sourceFrames,
                basicFrames,
                List.copyOf(initializers));
    }

    private static int firstReachableIndex(Frame<?>[] frames, int start)
    {
        for (int index = start; index < frames.length; index++)
        {
            if (frames[index] != null)
            {
                return index;
            }
        }
        return -1;
    }

    private static boolean hasContinuationBody(List<AbstractInsnNode> instructions, int splitIndex)
    {
        for (int index = splitIndex + 1; index < instructions.size(); index++)
        {
            int opcode = instructions.get(index).getOpcode();
            if (opcode >= 0 && opcode != Opcodes.RETURN)
            {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCrossBoundaryControlFlow(
            MethodNode method,
            List<AbstractInsnNode> instructions,
            Map<AbstractInsnNode, Integer> indices,
            int splitIndex)
    {
        for (int index = 0; index < instructions.size(); index++)
        {
            int sourceIndex = index;
            AbstractInsnNode instruction = instructions.get(index);
            if (instruction instanceof JumpInsnNode jump && crosses(index, index(indices, jump.label), splitIndex))
            {
                return true;
            }
            if (instruction instanceof TableSwitchInsnNode table)
            {
                if (crosses(index, index(indices, table.dflt), splitIndex) ||
                    table.labels.stream().anyMatch(label ->
                            crosses(sourceIndex, index(indices, label), splitIndex)))
                {
                    return true;
                }
            }
            if (instruction instanceof LookupSwitchInsnNode lookup)
            {
                if (crosses(index, index(indices, lookup.dflt), splitIndex) ||
                    lookup.labels.stream().anyMatch(label ->
                            crosses(sourceIndex, index(indices, label), splitIndex)))
                {
                    return true;
                }
            }
        }

        for (TryCatchBlockNode block : method.tryCatchBlocks)
        {
            boolean startSuffix = index(indices, block.start) > splitIndex;
            boolean endSuffix = index(indices, block.end) > splitIndex;
            boolean handlerSuffix = index(indices, block.handler) > splitIndex;
            if (!(startSuffix == endSuffix && endSuffix == handlerSuffix))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean crosses(int source, int target, int splitIndex)
    {
        return (source <= splitIndex) != (target <= splitIndex);
    }

    private static int index(Map<AbstractInsnNode, Integer> indices, AbstractInsnNode instruction)
    {
        Integer value = indices.get(instruction);
        return value == null ? -1 : value;
    }

    private static LocalPlan localPlan(
            MethodNode method,
            List<AbstractInsnNode> instructions,
            Map<AbstractInsnNode, Integer> indices,
            int splitIndex,
            Frame<BasicValue> basicFrame,
        Frame<SourceValue> sourceFrame)
    {
        LocalUsage usage = localUsage(method, instructions, indices, splitIndex);
        int originalParameterSlots = 1;
        for (Type argument : Type.getArgumentTypes(method.desc))
        {
            originalParameterSlots += argument.getSize();
        }

        List<LocalParameter> parameters = new ArrayList<>();
        int parameterSlots = 0;
        int slot = 0;
        while (slot < usage.requiredSlots())
        {
            Type type = localType(basicFrame, usage, slot);
            int size = type.getSize();
            boolean live = usage.liveAtEntry().get(slot);
            BasicValue value = slot < basicFrame.getLocals() ? basicFrame.getLocal(slot) : null;
            boolean initialized = value != null && value != BasicValue.UNINITIALIZED_VALUE;
            if (live && !initialized)
            {
                return null;
            }
            if (live && isReference(type) && slot >= originalParameterSlots &&
                mayBeUninitialized(sourceFrame, slot))
            {
                return null;
            }
            if (live)
            {
                parameters.add(new LocalParameter(slot, parameterSlots, type));
                parameterSlots += size;
                if (parameterSlots > MAX_PARAMETER_SLOTS)
                {
                    return null;
                }
            }
            slot += size;
        }
        return new LocalPlan(List.copyOf(parameters), parameterSlots);
    }

    private static LocalUsage localUsage(
            MethodNode method,
            List<AbstractInsnNode> instructions,
            Map<AbstractInsnNode, Integer> indices,
            int splitIndex)
    {
        int start = splitIndex + 1;
        int count = instructions.size() - start;
        List<BitSet> uses = new ArrayList<>(count);
        List<BitSet> definitions = new ArrayList<>(count);
        List<int[]> successors = new ArrayList<>(count);
        int[] inferredSort = new int[Math.max(method.maxLocals, 1)];
        int requiredSlots = 0;

        for (int relative = 0; relative < count; relative++)
        {
            int global = start + relative;
            AbstractInsnNode instruction = instructions.get(global);
            BitSet use = new BitSet();
            BitSet definition = new BitSet();
            if (instruction instanceof VarInsnNode variable)
            {
                int size = localSize(variable.getOpcode());
                requiredSlots = Math.max(requiredSlots, variable.var + size);
                inferSort(inferredSort, variable.var, localSort(variable.getOpcode()));
                if (isLoad(variable.getOpcode()))
                {
                    use.set(variable.var, variable.var + size);
                }
                else if (isStore(variable.getOpcode()))
                {
                    definition.set(variable.var, variable.var + size);
                }
            }
            else if (instruction instanceof IincInsnNode increment)
            {
                requiredSlots = Math.max(requiredSlots, increment.var + 1);
                inferSort(inferredSort, increment.var, Type.INT);
                use.set(increment.var);
                definition.set(increment.var);
            }
            uses.add(use);
            definitions.add(definition);
            successors.add(successors(instruction, global, start, instructions.size(), indices));
        }

        addExceptionSuccessors(method, indices, start, successors);
        List<BitSet> liveIn = new ArrayList<>(Collections.nCopies(count, null));
        List<BitSet> liveOut = new ArrayList<>(Collections.nCopies(count, null));
        for (int index = 0; index < count; index++)
        {
            liveIn.set(index, new BitSet());
            liveOut.set(index, new BitSet());
        }
        boolean changed;
        do
        {
            changed = false;
            for (int index = count - 1; index >= 0; index--)
            {
                BitSet out = new BitSet();
                for (int successor : successors.get(index))
                {
                    out.or(liveIn.get(successor));
                }
                BitSet in = (BitSet) out.clone();
                in.andNot(definitions.get(index));
                in.or(uses.get(index));
                if (!out.equals(liveOut.get(index)) || !in.equals(liveIn.get(index)))
                {
                    liveOut.set(index, out);
                    liveIn.set(index, in);
                    changed = true;
                }
            }
        } while (changed);

        return new LocalUsage(
                requiredSlots,
                inferredSort,
                count == 0 ? new BitSet() : liveIn.getFirst());
    }

    private static int[] successors(
            AbstractInsnNode instruction,
            int globalIndex,
            int suffixStart,
            int instructionCount,
            Map<AbstractInsnNode, Integer> indices)
    {
        List<Integer> result = new ArrayList<>();
        int opcode = instruction.getOpcode();
        if (instruction instanceof JumpInsnNode jump)
        {
            addSuccessor(result, index(indices, jump.label), suffixStart, instructionCount);
            if (opcode != Opcodes.GOTO && opcode != Opcodes.JSR)
            {
                addSuccessor(result, globalIndex + 1, suffixStart, instructionCount);
            }
        }
        else if (instruction instanceof TableSwitchInsnNode table)
        {
            addSuccessor(result, index(indices, table.dflt), suffixStart, instructionCount);
            table.labels.forEach(label ->
                    addSuccessor(result, index(indices, label), suffixStart, instructionCount));
        }
        else if (instruction instanceof LookupSwitchInsnNode lookup)
        {
            addSuccessor(result, index(indices, lookup.dflt), suffixStart, instructionCount);
            lookup.labels.forEach(label ->
                    addSuccessor(result, index(indices, label), suffixStart, instructionCount));
        }
        else if (opcode != Opcodes.RETURN && opcode != Opcodes.IRETURN &&
                 opcode != Opcodes.LRETURN && opcode != Opcodes.FRETURN &&
                 opcode != Opcodes.DRETURN && opcode != Opcodes.ARETURN &&
                 opcode != Opcodes.ATHROW)
        {
            addSuccessor(result, globalIndex + 1, suffixStart, instructionCount);
        }
        return result.stream().mapToInt(Integer::intValue).toArray();
    }

    private static void addSuccessor(
            List<Integer> successors,
            int globalIndex,
            int suffixStart,
            int instructionCount)
    {
        if (globalIndex >= suffixStart && globalIndex < instructionCount)
        {
            int relative = globalIndex - suffixStart;
            if (!successors.contains(relative))
            {
                successors.add(relative);
            }
        }
    }

    private static void addExceptionSuccessors(
            MethodNode method,
            Map<AbstractInsnNode, Integer> indices,
            int suffixStart,
            List<int[]> successors)
    {
        for (TryCatchBlockNode block : method.tryCatchBlocks)
        {
            int start = index(indices, block.start);
            int end = index(indices, block.end);
            int handler = index(indices, block.handler) - suffixStart;
            if (start < suffixStart || handler < 0 || handler >= successors.size())
            {
                continue;
            }
            for (int global = start; global < end; global++)
            {
                int relative = global - suffixStart;
                if (relative < 0 || relative >= successors.size())
                {
                    continue;
                }
                int[] current = successors.get(relative);
                boolean present = false;
                for (int value : current)
                {
                    present |= value == handler;
                }
                if (!present)
                {
                    int[] expanded = java.util.Arrays.copyOf(current, current.length + 1);
                    expanded[current.length] = handler;
                    successors.set(relative, expanded);
                }
            }
        }
    }

    private static Type localType(Frame<BasicValue> frame, LocalUsage usage, int slot)
    {
        if (slot < frame.getLocals())
        {
            BasicValue value = frame.getLocal(slot);
            if (value != null && value != BasicValue.UNINITIALIZED_VALUE)
            {
                if (value == BasicValue.INT_VALUE)
                {
                    return Type.INT_TYPE;
                }
                if (value == BasicValue.FLOAT_VALUE)
                {
                    return Type.FLOAT_TYPE;
                }
                if (value == BasicValue.LONG_VALUE)
                {
                    return Type.LONG_TYPE;
                }
                if (value == BasicValue.DOUBLE_VALUE)
                {
                    return Type.DOUBLE_TYPE;
                }
                return Type.getObjectType("java/lang/Object");
            }
        }
        int sort = slot < usage.inferredSort().length ? usage.inferredSort()[slot] : 0;
        return switch (sort)
        {
            case Type.LONG -> Type.LONG_TYPE;
            case Type.FLOAT -> Type.FLOAT_TYPE;
            case Type.DOUBLE -> Type.DOUBLE_TYPE;
            case Type.OBJECT, Type.ARRAY -> Type.getObjectType("java/lang/Object");
            default -> Type.INT_TYPE;
        };
    }

    private static boolean mayBeUninitialized(Frame<SourceValue> frame, int slot)
    {
        if (frame == null || slot >= frame.getLocals())
        {
            return true;
        }
        SourceValue value = frame.getLocal(slot);
        return value == null || value.insns.stream().anyMatch(instruction ->
                instruction.getOpcode() == Opcodes.NEW);
    }

    private static boolean isReference(Type type)
    {
        return type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY;
    }

    private static void inferSort(int[] inferredSort, int slot, int sort)
    {
        if (slot >= 0 && slot < inferredSort.length && inferredSort[slot] == 0)
        {
            inferredSort[slot] = sort;
        }
    }

    private static int localSort(int opcode)
    {
        return switch (opcode)
        {
            case Opcodes.LLOAD, Opcodes.LSTORE -> Type.LONG;
            case Opcodes.FLOAD, Opcodes.FSTORE -> Type.FLOAT;
            case Opcodes.DLOAD, Opcodes.DSTORE -> Type.DOUBLE;
            case Opcodes.ALOAD, Opcodes.ASTORE -> Type.OBJECT;
            default -> Type.INT;
        };
    }

    private static int localSize(int opcode)
    {
        return opcode == Opcodes.LLOAD || opcode == Opcodes.LSTORE ||
               opcode == Opcodes.DLOAD || opcode == Opcodes.DSTORE ? 2 : 1;
    }

    private static boolean isLoad(int opcode)
    {
        return opcode >= Opcodes.ILOAD && opcode <= Opcodes.ALOAD;
    }

    private static boolean isStore(int opcode)
    {
        return opcode >= Opcodes.ISTORE && opcode <= Opcodes.ASTORE;
    }

    private static void partitionTryCatchBlocks(
            MethodNode constructor,
            MethodNode continuation,
            Map<AbstractInsnNode, Integer> indices,
            int splitIndex)
    {
        List<TryCatchBlockNode> prefix = new ArrayList<>();
        for (TryCatchBlockNode block : constructor.tryCatchBlocks)
        {
            if (index(indices, block.start) > splitIndex)
            {
                continuation.tryCatchBlocks.add(block);
            }
            else
            {
                prefix.add(block);
            }
        }
        constructor.tryCatchBlocks = prefix;
    }

    private static void moveContinuation(
            MethodNode constructor,
            MethodNode continuation,
            AbstractInsnNode initializer)
    {
        AbstractInsnNode instruction = initializer.getNext();
        while (instruction != null)
        {
            AbstractInsnNode next = instruction.getNext();
            constructor.instructions.remove(instruction);
            continuation.instructions.add(instruction);
            instruction = next;
        }
    }

    private static void appendContinuationCall(
            MethodNode constructor,
            ClassNode owner,
            MethodNode continuation,
            LocalPlan locals)
    {
        InsnList call = new InsnList();
        for (LocalParameter parameter : locals.parameters())
        {
            call.add(new VarInsnNode(
                    parameter.type().getOpcode(Opcodes.ILOAD),
                    parameter.sourceLocal()));
        }
        call.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                owner.name,
                continuation.name,
                continuation.desc,
                false));
        call.add(new InsnNode(Opcodes.RETURN));
        constructor.instructions.add(call);
        constructor.maxStack = Math.max(constructor.maxStack, locals.parameterSlots());
    }

    private static void isolateContinuationLocals(MethodNode continuation, LocalPlan locals)
    {
        int bodyBase = locals.parameterSlots();
        for (AbstractInsnNode instruction : continuation.instructions)
        {
            if (instruction instanceof VarInsnNode variable)
            {
                variable.var += bodyBase;
            }
            else if (instruction instanceof IincInsnNode increment)
            {
                increment.var += bodyBase;
            }
        }

        InsnList restore = new InsnList();
        for (LocalParameter parameter : locals.parameters())
        {
            restore.add(new VarInsnNode(
                    parameter.type().getOpcode(Opcodes.ILOAD),
                    parameter.parameterLocal()));
            restore.add(new VarInsnNode(
                    parameter.type().getOpcode(Opcodes.ISTORE),
                    bodyBase + parameter.sourceLocal()));
        }
        continuation.instructions.insert(restore);
    }

    private String continuationName(ClassNode owner, String descriptor)
    {
        int ordinal = nameOrdinals.getOrDefault(owner.name, 0);
        while (true)
        {
            String semantic = "$vm$constructorBody$" + ordinal++;
            String candidate = namer.method(owner.name, semantic, descriptor);
            boolean occupied = owner.methods.stream().anyMatch(method ->
                    method.name.equals(candidate) && method.desc.equals(descriptor));
            if (!occupied)
            {
                nameOrdinals.put(owner.name, ordinal);
                return candidate;
            }
        }
    }

    private static void clearDebugMetadata(MethodNode method)
    {
        method.localVariables = null;
        method.visibleLocalVariableAnnotations = null;
        method.invisibleLocalVariableAnnotations = null;
    }

    private static boolean usesStackTraceIntrospection(MethodNode method)
    {
        for (AbstractInsnNode instruction : method.instructions)
        {
            if (!(instruction instanceof MethodInsnNode call))
            {
                continue;
            }
            if (("java/lang/Throwable".equals(call.owner) ||
                 "java/lang/Thread".equals(call.owner)) &&
                "getStackTrace".equals(call.name) &&
                "()[Ljava/lang/StackTraceElement;".equals(call.desc))
            {
                return true;
            }
            if (call.owner.startsWith("java/lang/StackWalker"))
            {
                return true;
            }
        }
        return false;
    }

    private record Analysis(
            List<AbstractInsnNode> instructions,
            Map<AbstractInsnNode, Integer> indices,
            Frame<SourceValue>[] sourceFrames,
            Frame<BasicValue>[] basicFrames,
            List<Integer> initializers)
    {
    }

    private record LocalUsage(int requiredSlots, int[] inferredSort, BitSet liveAtEntry)
    {
    }

    private record LocalParameter(int sourceLocal, int parameterLocal, Type type)
    {
    }

    private record LocalPlan(List<LocalParameter> parameters, int parameterSlots)
    {
        private Type[] parameterTypes()
        {
            return parameters.stream().map(LocalParameter::type).toArray(Type[]::new);
        }
    }

    private record SplitResult(MethodNode continuation, String reason)
    {
        private static SplitResult success(MethodNode continuation)
        {
            return new SplitResult(continuation, null);
        }

        private static SplitResult skipped(String reason)
        {
            return new SplitResult(null, reason);
        }
    }

    public record Continuation(
            ClassNode owner,
            MethodNode constructor,
            MethodNode method,
            BytecodeVMConfig methodConfig)
    {
    }

    public record Result(List<Continuation> continuations, Map<String, Integer> skippedReasons)
    {
        public Set<MethodNode> methods()
        {
            Set<MethodNode> methods = Collections.newSetFromMap(new IdentityHashMap<>());
            continuations.forEach(continuation -> methods.add(continuation.method()));
            return methods;
        }

        public int skipped()
        {
            return skippedReasons.values().stream().mapToInt(Integer::intValue).sum();
        }
    }
}
