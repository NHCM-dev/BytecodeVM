package nhcm.bytecodevm.Config;

import org.objectweb.asm.tree.*;

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

        for (Rule rule : rules)
        {
            if (rule.type == RuleType.CLASS &&
                rule.matchesClass(
                        className,
                        visibleAnnotations,
                        invisibleAnnotations
                ))
            {
                return true;
            }
        }

        return false;
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
                return true;
            }
        }

        return false;
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
                return true;
            }
        }

        return false;
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
        private final Pattern classPattern;
        private final Pattern memberNamePattern;
        private final Pattern descPattern;
        private final Pattern annotationPattern;

        private Rule(
                RuleType type,
                Pattern classPattern,
                Pattern memberNamePattern,
                Pattern descPattern,
                Pattern annotationPattern)
        {
            this.type = type;
            this.classPattern = classPattern;
            this.memberNamePattern = memberNamePattern;
            this.descPattern = descPattern;
            this.annotationPattern = annotationPattern;
        }

        public static Rule parse(String raw)
        {
            raw = raw.trim();

            String[] parts = raw.split("\\s+");

            if (parts.length == 1)
            {
                return new Rule(
                        RuleType.CLASS,
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
                    return parseMethod(classPart, memberPart, null);
                }

                return new Rule(
                        RuleType.FIELD,
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
                            annotationToPattern(annotationPart)
                    );
                }

                return new Rule(
                        RuleType.FIELD,
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
                Pattern annotationPattern)
        {
            int start = memberPart.indexOf('(');
            int end = memberPart.indexOf(')');

            String methodName = memberPart.substring(0, start);
            String args = memberPart.substring(start, end + 1);
            String ret = memberPart.substring(end + 1);

            String desc = args + ret;

            return new Rule(
                    RuleType.METHOD,
                    wildcardToPattern(classPart),
                    wildcardToPattern(methodName),
                    wildcardToPattern(desc),
                    annotationPattern
            );
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