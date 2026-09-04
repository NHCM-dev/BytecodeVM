package nhcm.bytecodevm.generator.editor.inlining;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Resolves symbolic member owners to declarations available in the input JAR. */
public final class ClassHierarchyResolver
{
    private final Map<String, ClassNode> classesByName = new HashMap<>();

    public ClassHierarchyResolver(Collection<ClassNode> classes)
    {
        for (ClassNode classNode : classes)
        {
            classesByName.put(classNode.name, classNode);
        }
    }

    FieldDeclaration resolveField(
            String symbolicOwner,
            String name,
            String descriptor,
            boolean staticAccess)
    {
        return resolveField(
                symbolicOwner,
                name,
                descriptor,
                staticAccess,
                new HashSet<>());
    }

    public MethodDeclaration resolveMethod(String symbolicOwner, String name, String descriptor)
    {
        return resolveMethod(symbolicOwner, name, descriptor, new HashSet<>());
    }

    private MethodDeclaration resolveMethod(
            String ownerName,
            String name,
            String descriptor,
            Set<String> visited)
    {
        if (ownerName == null || !visited.add(ownerName))
        {
            return null;
        }
        ClassNode owner = classesByName.get(ownerName);
        if (owner == null)
        {
            return null;
        }
        for (MethodNode method : owner.methods)
        {
            if (method.name.equals(name) && method.desc.equals(descriptor))
            {
                return new MethodDeclaration(owner, method);
            }
        }
        if (name.startsWith("<"))
        {
            return null;
        }

        MethodDeclaration declaration = resolveMethod(owner.superName, name, descriptor, visited);
        if (declaration != null)
        {
            return declaration;
        }
        for (String interfaceName : owner.interfaces)
        {
            declaration = resolveMethod(interfaceName, name, descriptor, visited);
            if (declaration != null)
            {
                return declaration;
            }
        }
        return null;
    }

    boolean isAssignableTo(String source, String target)
    {
        return isAssignableTo(source, target, new HashSet<>());
    }

    boolean hasCloneableSubtype(String owner)
    {
        for (String candidate : classesByName.keySet())
        {
            if (isAssignableTo(candidate, owner) && isAssignableTo(candidate, "java/lang/Cloneable"))
            {
                return true;
            }
        }
        return false;
    }

    private boolean isAssignableTo(String source, String target, Set<String> visited)
    {
        if (source == null || !visited.add(source))
        {
            return false;
        }
        if (source.equals(target))
        {
            return true;
        }
        ClassNode sourceClass = classesByName.get(source);
        if (sourceClass == null)
        {
            return false;
        }
        if (isAssignableTo(sourceClass.superName, target, visited))
        {
            return true;
        }
        for (String interfaceName : sourceClass.interfaces)
        {
            if (isAssignableTo(interfaceName, target, visited))
            {
                return true;
            }
        }
        return false;
    }

    private FieldDeclaration resolveField(
            String ownerName,
            String name,
            String descriptor,
            boolean staticAccess,
            Set<String> visited)
    {
        if (ownerName == null || !visited.add(ownerName))
        {
            return null;
        }
        ClassNode owner = classesByName.get(ownerName);
        if (owner == null)
        {
            return null;
        }

        for (FieldNode field : owner.fields)
        {
            if (field.name.equals(name) && field.desc.equals(descriptor) &&
                ((field.access & Opcodes.ACC_STATIC) != 0) == staticAccess)
            {
                return new FieldDeclaration(owner, field);
            }
        }

        // JVM field resolution checks direct superinterfaces before continuing with the superclass.
        for (String interfaceName : owner.interfaces)
        {
            FieldDeclaration declaration = resolveField(
                    interfaceName,
                    name,
                    descriptor,
                    staticAccess,
                    visited);
            if (declaration != null)
            {
                return declaration;
            }
        }
        return resolveField(owner.superName, name, descriptor, staticAccess, visited);
    }

    record FieldDeclaration(ClassNode owner, FieldNode field)
    {
    }

    public record MethodDeclaration(ClassNode owner, MethodNode method)
    {
    }
}
