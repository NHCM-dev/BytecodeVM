package nhcm.bytecodevm.config;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class TargetMatcher
{
    private final List<Rule> rules = new ArrayList<>();

    public void add(String rule)
    {
        rules.add(Rule.parse(rule));
    }

    public boolean isClassMatched(ClassNode cn)
    {
        return isClassMatched(
                cn.name,
                cn.visibleAnnotations,
                cn.invisibleAnnotations
        );
    }

    public boolean isFieldMatched(ClassNode owner, FieldNode fn)
    {
        return isFieldMatched(
                owner.name,
                fn.name,
                fn.desc,
                fn.visibleAnnotations,
                fn.invisibleAnnotations
        );
    }

    public boolean isMethodMatched(ClassNode owner, MethodNode mn)
    {
        return isMethodMatched(
                owner.name,
                mn.name,
                mn.desc,
                mn.visibleAnnotations,
                mn.invisibleAnnotations
        );
    }

    public boolean isFieldContextMatched(ClassNode owner, FieldNode field)
    {
        return isMemberContextMatched(
                RuleType.FIELD,
                owner.name,
                field.name,
                field.desc,
                owner.visibleAnnotations,
                owner.invisibleAnnotations,
                field.visibleAnnotations,
                field.invisibleAnnotations);
    }

    public boolean isMethodContextMatched(ClassNode owner, MethodNode method)
    {
        return isMemberContextMatched(
                RuleType.METHOD,
                owner.name,
                method.name,
                method.desc,
                owner.visibleAnnotations,
                owner.invisibleAnnotations,
                method.visibleAnnotations,
                method.invisibleAnnotations);
    }

    private boolean isMemberContextMatched(
            RuleType memberType,
            String owner,
            String name,
            String descriptor,
            List<AnnotationNode> ownerVisible,
            List<AnnotationNode> ownerInvisible,
            List<AnnotationNode> memberVisible,
            List<AnnotationNode> memberInvisible)
    {
        owner = internalClassName(owner);
        boolean matched = false;
        for (Rule rule : rules)
        {
            boolean applies = rule.type == RuleType.CLASS
                    ? rule.matchesClass(owner, ownerVisible, ownerInvisible)
                    : rule.type == memberType &&
                      rule.matchesMember(owner, name, descriptor, memberVisible, memberInvisible);
            if (applies)
            {
                matched = rule.included;
            }
        }
        return matched;
    }

    public boolean isClassMatched(String className)
    {
        return isClassMatched(className, null, null);
    }

    public boolean isClassMatched(
            String className,
            List<AnnotationNode> visibleAnnotations,
            List<AnnotationNode> invisibleAnnotations)
    {
        className = internalClassName(className);

        boolean matched = false;
        for (Rule rule : rules)
        {
            if (rule.type == RuleType.CLASS &&
                rule.matchesClass(
                        className,
                        visibleAnnotations,
                        invisibleAnnotations
                ))
            {
                matched = rule.included;
            }
        }
        return matched;
    }

    public boolean isFieldMatched(String owner, String name, String desc)
    {
        return isFieldMatched(owner, name, desc, null, null);
    }

    public boolean isFieldMatched(
            String owner,
            String name,
            String desc,
            List<AnnotationNode> visibleAnnotations,
            List<AnnotationNode> invisibleAnnotations)
    {
        owner = internalClassName(owner);

        boolean matched = false;
        for (Rule rule : rules)
        {
            if (rule.type == RuleType.FIELD &&
                rule.matchesMember(
                        owner,
                        name,
                        desc,
                        visibleAnnotations,
                        invisibleAnnotations
                ))
            {
                matched = rule.included;
            }
        }
        return matched;
    }

    public boolean isMethodMatched(String owner, String name, String desc)
    {
        return isMethodMatched(owner, name, desc, null, null);
    }

    public boolean isMethodMatched(
            String owner,
            String name,
            String desc,
            List<AnnotationNode> visibleAnnotations,
            List<AnnotationNode> invisibleAnnotations)
    {
        owner = internalClassName(owner);

        boolean matched = false;
        for (Rule rule : rules)
        {
            if (rule.type == RuleType.METHOD &&
                rule.matchesMember(
                        owner,
                        name,
                        desc,
                        visibleAnnotations,
                        invisibleAnnotations
                ))
            {
                matched = rule.included;
            }
        }
        return matched;
    }

    private static String internalClassName(String name)
    {
        return name.replace('/', '.');
    }

    private enum RuleType
    {
        CLASS,
        FIELD,
        METHOD
    }

    private static class Rule
    {
        private final RuleType type;
        private final boolean included;
        private final Pattern classPattern;
        private final Pattern memberNamePattern;
        private final Pattern descPattern;
        private final Pattern annotationPattern;

        private Rule(
                RuleType type,
                boolean included,
                Pattern classPattern,
                Pattern memberNamePattern,
                Pattern descPattern,
                Pattern annotationPattern)
        {
            this.type = type;
            this.included = included;
            this.classPattern = classPattern;
            this.memberNamePattern = memberNamePattern;
            this.descPattern = descPattern;
            this.annotationPattern = annotationPattern;
        }

        public static Rule parse(String raw)
        {
            raw = raw.trim();
            boolean included = true;
            if (raw.startsWith("!"))
            {
                included = false;
                raw = raw.substring(1).trim();
            }
            if (raw.isEmpty())
            {
                throw new IllegalArgumentException("Empty match rule");
            }

            if (raw.indexOf(',') >= 0)
            {
                return parseReadable(raw, included);
            }

            String[] parts = raw.split("\\s+");

            if (parts.length == 1)
            {
                return new Rule(
                        RuleType.CLASS,
                        included,
                        wildcardToPattern(parts[0]),
                        null,
                        null,
                        null
                );
            }

            if (parts.length == 2)
            {
                if (isAnnotation(parts[0]))
                {
                    return new Rule(
                            RuleType.CLASS,
                            included,
                            wildcardToPattern(parts[1]),
                            null,
                            null,
                            annotationToPattern(parts[0])
                    );
                }

                String classPart = parts[0];
                String memberPart = parts[1];

                if (isMethodPattern(memberPart))
                {
                    return parseMethod(classPart, memberPart, null, included);
                }

                return new Rule(
                        RuleType.FIELD,
                        included,
                        wildcardToPattern(classPart),
                        wildcardToPattern(memberPart),
                        wildcardToPattern("*"),
                        null
                );
            }

            if (parts.length == 3)
            {
                String classPart = parts[0];
                String annotationPart = parts[1];
                String memberPart = parts[2];

                if (!isAnnotation(annotationPart))
                {
                    throw new IllegalArgumentException("Invalid annotation rule: " + raw);
                }

                if (isMethodPattern(memberPart))
                {
                    return parseMethod(
                            classPart,
                            memberPart,
                            annotationToPattern(annotationPart),
                            included
                    );
                }

                return new Rule(
                        RuleType.FIELD,
                        included,
                        wildcardToPattern(classPart),
                        wildcardToPattern(memberPart),
                        wildcardToPattern("*"),
                        annotationToPattern(annotationPart)
                );
            }

            throw new IllegalArgumentException("Invalid rule: " + raw);
        }

        private static Rule parseMethod(
                String classPart,
                String memberPart,
                Pattern annotationPattern,
                boolean included)
        {
            int start = memberPart.indexOf('(');
            int end = memberPart.indexOf(')');

            String methodName = memberPart.substring(0, start);
            String args = memberPart.substring(start, end + 1);
            String ret = memberPart.substring(end + 1);

            String desc = args + ret;

            return new Rule(
                    RuleType.METHOD,
                    included,
                    wildcardToPattern(classPart),
                    wildcardToPattern(methodName),
                    wildcardToPattern(desc),
                    annotationPattern
            );
        }

        private static Rule parseReadable(String raw, boolean included)
        {
            String[] parts = raw.split("\\s*,\\s*", 3);
            if (parts.length < 2 || parts.length > 3 || parts[0].isEmpty() || parts[1].isEmpty())
            {
                throw new IllegalArgumentException("Invalid readable match rule: " + raw);
            }
            String descriptor = parts.length == 3 ? parts[2].trim() : "*";
            if (descriptor.contains("("))
            {
                return new Rule(
                        RuleType.METHOD,
                        included,
                        wildcardToPattern(parts[0]),
                        wildcardToPattern(parts[1]),
                        wildcardToPattern(readableMethodDescriptor(descriptor)),
                        null);
            }
            return new Rule(
                    RuleType.FIELD,
                    included,
                    wildcardToPattern(parts[0]),
                    wildcardToPattern(parts[1]),
                    wildcardToPattern(readableTypeDescriptor(descriptor)),
                    null);
        }

        private static String readableMethodDescriptor(String signature)
        {
            int start = signature.indexOf('(');
            int end = signature.lastIndexOf(')');
            if (start < 0 || end < start)
            {
                throw new IllegalArgumentException("Invalid readable method signature: " + signature);
            }
            String before = signature.substring(0, start).trim();
            String after = signature.substring(end + 1).trim();
            String returnType;
            if (before.isEmpty() || "*".equals(before))
            {
                returnType = after.startsWith(":") ? after.substring(1).trim() : "*";
            }
            else
            {
                returnType = before;
            }
            String arguments = signature.substring(start + 1, end).trim();
            StringBuilder descriptor = new StringBuilder("(");
            if (!arguments.isEmpty())
            {
                for (String argument : arguments.split("\\s*,\\s*"))
                {
                    descriptor.append(readableTypeDescriptor(argument));
                }
            }
            return descriptor.append(')').append(readableTypeDescriptor(returnType)).toString();
        }

        private static String readableTypeDescriptor(String type)
        {
            type = type.trim();
            if (type.isEmpty() || "*".equals(type))
            {
                return "*";
            }
            int dimensions = 0;
            while (type.endsWith("[]"))
            {
                dimensions++;
                type = type.substring(0, type.length() - 2).trim();
            }
            String descriptor = switch (type)
            {
                case "void" -> "V";
                case "boolean" -> "Z";
                case "byte" -> "B";
                case "char" -> "C";
                case "short" -> "S";
                case "int" -> "I";
                case "long" -> "J";
                case "float" -> "F";
                case "double" -> "D";
                default -> 'L' + type.replace('.', '/') + ';';
            };
            return "[".repeat(dimensions) + descriptor;
        }

        public boolean matchesClass(
                String className,
                List<AnnotationNode> visibleAnnotations,
                List<AnnotationNode> invisibleAnnotations)
        {
            return classPattern.matcher(className).matches()
                   && matchesAnnotation(visibleAnnotations, invisibleAnnotations);
        }

        public boolean matchesMember(
                String owner,
                String name,
                String desc,
                List<AnnotationNode> visibleAnnotations,
                List<AnnotationNode> invisibleAnnotations)
        {
            return classPattern.matcher(owner).matches()
                   && memberNamePattern.matcher(name).matches()
                   && descPattern.matcher(desc).matches()
                   && matchesAnnotation(visibleAnnotations, invisibleAnnotations);
        }

        private boolean matchesAnnotation(
                List<AnnotationNode> visibleAnnotations,
                List<AnnotationNode> invisibleAnnotations)
        {
            if (annotationPattern == null)
            {
                return true;
            }

            return matchesAnnotationList(visibleAnnotations)
                   || matchesAnnotationList(invisibleAnnotations);
        }

        private boolean matchesAnnotationList(List<AnnotationNode> annotations)
        {
            if (annotations == null)
            {
                return false;
            }

            for (AnnotationNode annotation : annotations)
            {
                String normalized = normalizeAnnotation(annotation.desc);

                if (annotationPattern.matcher(normalized).matches())
                {
                    return true;
                }

                String simpleName = simpleName(normalized);

                if (annotationPattern.matcher(simpleName).matches())
                {
                    return true;
                }
            }

            return false;
        }

        private static boolean isMethodPattern(String value)
        {
            return value.contains("(") && value.contains(")");
        }

        private static boolean isAnnotation(String value)
        {
            return value.startsWith("@");
        }

        private static Pattern annotationToPattern(String annotation)
        {
            annotation = annotation.substring(1);
            annotation = normalizeAnnotation(annotation);
            return wildcardToPattern(annotation);
        }

        private static String normalizeAnnotation(String annotation)
        {
            annotation = annotation.replace('/', '.');

            if (annotation.startsWith("L") && annotation.endsWith(";"))
            {
                annotation = annotation.substring(1, annotation.length() - 1);
            }

            return annotation;
        }

        private static String simpleName(String name)
        {
            int index = name.lastIndexOf('.');

            if (index == -1)
            {
                return name;
            }

            return name.substring(index + 1);
        }

        private static Pattern wildcardToPattern(String wildcard)
        {
            StringBuilder regex = new StringBuilder();

            regex.append("^");

            for (int i = 0; i < wildcard.length(); i++)
            {
                char c = wildcard.charAt(i);

                if (c == '*')
                {
                    regex.append(".*");
                }
                else
                {
                    if ("\\.[]{}()+-^$?|".indexOf(c) >= 0)
                    {
                        regex.append("\\");
                    }

                    regex.append(c);
                }
            }

            regex.append("$");

            return Pattern.compile(regex.toString());
        }
    }
}
