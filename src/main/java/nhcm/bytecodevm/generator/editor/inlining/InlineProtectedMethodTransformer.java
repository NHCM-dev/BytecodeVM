package nhcm.bytecodevm.generator.editor.inlining;

import nhcm.bytecodevm.config.BytecodeVMConfig;
import nhcm.bytecodevm.config.sdk.SdkAnnotationReader;
import nhcm.bytecodevm.generator.VMSetGenerator;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plans protected methods whose original method slot becomes their VM entry.
 * Calls keep their original owner/name/descriptor, so no MethodEntries bridge class is generated.
 */
public final class InlineProtectedMethodTransformer
{
    private final BytecodeVMConfig config;
    private final Collection<ClassNode> classes;
    private final Set<MethodNode> protectedMethods;
    private final Map<MethodNode, VMSetGenerator> assignments;
    private final ClassHierarchyResolver hierarchy;
    private final Map<MethodId, Target> targets = new LinkedHashMap<>();

    public InlineProtectedMethodTransformer(
            BytecodeVMConfig config,
            Collection<ClassNode> classes,
            Set<MethodNode> protectedMethods,
            Map<MethodNode, VMSetGenerator> assignments)
    {
        this.config = config;
        this.classes = classes;
        this.protectedMethods = protectedMethods;
        this.assignments = assignments;
        this.hierarchy = new ClassHierarchyResolver(classes);
    }

    public Prepared prepare()
    {
        if (!config.inlineCalledProtectedMethods)
        {
            return new Prepared(0, 0, 0);
        }
        collectTargets();
        Map<MethodId, List<Call>> calls = collectCalls();
        int callSites = targets.keySet().stream()
                .mapToInt(target -> calls.getOrDefault(target, List.of()).size())
                .sum();
        return new Prepared(targets.size(), callSites, 0);
    }

    public Finished finish()
    {
        int directEntries = 0;
        for (Target target : targets.values())
        {
            VMSetGenerator generator = assignments.get(target.method);
            if (generator == null || generator.compiledMethod(target.method) == null)
            {
                throw new IllegalStateException(
                        "Missing compiled VM entry for " + target.id.owner + '.' +
                                target.id.name + target.id.descriptor);
            }
            if (!target.owner.methods.contains(target.method))
            {
                throw new IllegalStateException(
                        "Original VM entry slot was removed for " + target.id.owner + '.' +
                                target.id.name + target.id.descriptor);
            }
            directEntries++;
        }
        return new Finished(directEntries);
    }

    private void collectTargets()
    {
        for (ClassNode owner : classes)
        {
            for (MethodNode method : owner.methods)
            {
                if (!protectedMethods.contains(method) || method.name.startsWith("<") ||
                    (method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_SYNCHRONIZED)) != 0 ||
                    !config.matchRules.statementMatches("inlineCalledProtectedMethods", owner, method))
                {
                    continue;
                }
                if (config.annotationOnly &&
                    !SdkAnnotationReader.methodDirectives(owner, method).methodAnnotation())
                {
                    continue;
                }
                if (config.ignorePublicCalls && (method.access & Opcodes.ACC_PRIVATE) == 0)
                {
                    continue;
                }
                MethodId id = new MethodId(owner.name, method.name, method.desc);
                targets.put(id, new Target(id, owner, method));
            }
        }
    }

    private Map<MethodId, List<Call>> collectCalls()
    {
        Map<MethodId, List<Call>> calls = new LinkedHashMap<>();
        for (ClassNode owner : classes)
        {
            for (MethodNode method : owner.methods)
            {
                for (AbstractInsnNode instruction : method.instructions)
                {
                    if (!(instruction instanceof MethodInsnNode call))
                    {
                        continue;
                    }
                    ClassHierarchyResolver.MethodDeclaration declaration =
                            hierarchy.resolveMethod(call.owner, call.name, call.desc);
                    MethodId id = declaration == null
                            ? new MethodId(call.owner, call.name, call.desc)
                            : new MethodId(declaration.owner().name, call.name, call.desc);
                    if (targets.containsKey(id))
                    {
                        calls.computeIfAbsent(id, ignored -> new ArrayList<>())
                                .add(new Call(owner, method));
                    }
                }
            }
        }
        return calls;
    }

    private record MethodId(String owner, String name, String descriptor)
    {
    }

    private record Target(MethodId id, ClassNode owner, MethodNode method)
    {
    }

    private record Call(ClassNode owner, MethodNode method)
    {
    }

    public record Prepared(int methods, int calls, int skipped)
    {
    }

    public record Finished(int directEntries)
    {
    }
}
