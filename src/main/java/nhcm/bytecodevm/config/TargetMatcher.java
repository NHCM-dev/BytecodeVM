package nhcm.bytecodevm.config;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** Compiles ordered class/member selection expressions. */
public final class TargetMatcher
{
    public record MatchResult(boolean matched, boolean included)
    {
        public boolean orElse(boolean fallback)
        {
            return matched ? included : fallback;
        }
    }

    private final List<Rule> rules = new ArrayList<>();

    public void add(String rule)
    {
        add(rule, true);
    }

    public void add(String rule, boolean included)
    {
        rules.add(Rule.parse(rule, included));
    }

    public boolean isEmpty()
    {
        return rules.isEmpty();
    }

    public MatchResult classDecision(ClassNode owner)
    {
        return classDecision(owner.name, owner.visibleAnnotations, owner.invisibleAnnotations);
    }

    public MatchResult fieldDecision(ClassNode owner, FieldNode field)
    {
        return memberDecision(
                RuleType.FIELD,
                owner.name,
                field.name,
                field.desc,
                owner.visibleAnnotations,
                owner.invisibleAnnotations,
                field.visibleAnnotations,
                field.invisibleAnnotations);
    }

    public MatchResult methodDecision(ClassNode owner, MethodNode method)
    {
        return memberDecision(
                RuleType.METHOD,
                owner.name,
                method.name,
                method.desc,
                owner.visibleAnnotations,
                owner.invisibleAnnotations,
                method.visibleAnnotations,
                method.invisibleAnnotations);
    }

    public boolean isClassMatched(ClassNode owner)
    {
        return classDecision(owner).orElse(false);
    }

    public boolean isClassMatched(String className)
    {
        return classDecision(className, null, null).orElse(false);
    }

    public boolean isFieldMatched(ClassNode owner, FieldNode field)
    {
        return fieldDecision(owner, field).orElse(false);
    }

    public boolean isFieldMatched(String owner, String name, String descriptor)
    {
        return memberDecision(RuleType.FIELD, owner, name, descriptor,
                null, null, null, null).orElse(false);
    }

    public boolean isMethodMatched(ClassNode owner, MethodNode method)
    {
        return methodDecision(owner, method).orElse(false);
    }

    public boolean isMethodMatched(String owner, String name, String descriptor)
    {
        return memberDecision(RuleType.METHOD, owner, name, descriptor,
                null, null, null, null).orElse(false);
    }

    public boolean isFieldContextMatched(ClassNode owner, FieldNode field)
    {
        return isFieldMatched(owner, field);
    }

    public boolean isMethodContextMatched(ClassNode owner, MethodNode method)
    {
        return isMethodMatched(owner, method);
    }

    private MatchResult classDecision(
            String owner,
            List<AnnotationNode> visibleAnnotations,
            List<AnnotationNode> invisibleAnnotations)
    {
        String className = normalizeClassName(owner);
        MatchResult result = new MatchResult(false, false);
        for (Rule rule : rules)
        {
            if (rule.type == RuleType.CLASS &&
                rule.matchesClass(className, visibleAnnotations, invisibleAnnotations))
            {
                result = new MatchResult(true, rule.included);
            }
        }
        return result;
    }

    private MatchResult memberDecision(
            RuleType memberType,
            String owner,
            String name,
            String descriptor,
            List<AnnotationNode> ownerVisible,
            List<AnnotationNode> ownerInvisible,
            List<AnnotationNode> memberVisible,
            List<AnnotationNode> memberInvisible)
    {
        String className = normalizeClassName(owner);
        MatchResult result = new MatchResult(false, false);
        for (Rule rule : rules)
        {
            boolean applies = rule.type == memberType && rule.matchesMember(
                    className,
                    name,
                    descriptor,
                    ownerVisible,
                    ownerInvisible,
                    memberVisible,
                    memberInvisible);
            if (applies)
            {
                result = new MatchResult(true, rule.included);
            }
        }
        return result;
    }

    private enum RuleType
    {
        CLASS,
        FIELD,
        METHOD
    }

    private record ClassClause(Pattern owner, Pattern annotation)
    {
    }

    private static final class Rule
    {
        private final RuleType type;
        private final boolean included;
        private final Pattern owner;
        private final Pattern memberName;
        private final Pattern descriptor;
        private final Pattern classAnnotation;
        private final Pattern memberAnnotation;

        private Rule(
                RuleType type,
                boolean included,
                Pattern owner,
                Pattern memberName,
                Pattern descriptor,
                Pattern classAnnotation,
                Pattern memberAnnotation)
        {
            this.type = type;
            this.included = included;
            this.owner = owner;
            this.memberName = memberName;
            this.descriptor = descriptor;
            this.classAnnotation = classAnnotation;
            this.memberAnnotation = memberAnnotation;
        }

        private static Rule parse(String input, boolean blockDecision)
        {
            String raw = unescape(input.trim());
            boolean included = blockDecision;
            if (raw.startsWith("!"))
            {
                included = !included;
                raw = raw.substring(1).trim();
            }
            if (raw.isEmpty())
            {
                throw new IllegalArgumentException("Empty match rule");
            }
            if (containsTypedClause(raw))
            {
                return parseTyped(raw, included);
            }
            return parseLegacy(raw, included);
        }

        private static boolean containsTypedClause(String raw)
        {
            return Arrays.stream(raw.split(";"))
                    .map(String::trim)
                    .anyMatch(value -> value.startsWith("class:") ||
                                       value.startsWith("field:") ||
                                       value.startsWith("method:"));
        }

        private static Rule parseTyped(String raw, boolean included)
        {
            String classBody = null;
            String fieldBody = null;
            String methodBody = null;
            for (String clause : raw.split(";"))
            {
                String value = clause.trim();
                int separator = value.indexOf(':');
                if (separator <= 0 || separator == value.length() - 1)
                {
                    throw new IllegalArgumentException("Invalid typed match clause: " + clause);
                }
                String type = value.substring(0, separator).trim();
                String body = value.substring(separator + 1).trim();
                switch (type)
                {
                    case "class" -> {
                        if (classBody != null)
                        {
                            throw new IllegalArgumentException("Duplicate class clause: " + raw);
                        }
                        classBody = body;
                    }
                    case "field" -> {
                        if (fieldBody != null || methodBody != null)
                        {
                            throw new IllegalArgumentException("A rule can select only one member: " + raw);
                        }
                        fieldBody = body;
                    }
                    case "method" -> {
                        if (methodBody != null || fieldBody != null)
                        {
                            throw new IllegalArgumentException("A rule can select only one member: " + raw);
                        }
                        methodBody = body;
                    }
                    default -> throw new IllegalArgumentException("Unknown match clause: " + type);
                }
            }

            ClassClause classClause = parseClassClause(classBody);
            if (fieldBody != null)
            {
                return parseTypedField(fieldBody, classClause, classBody != null, included);
            }
            if (methodBody != null)
            {
                return parseTypedMethod(methodBody, classClause, classBody != null, included);
            }
            if (classBody == null)
            {
                throw new IllegalArgumentException("Typed rule has no class, field, or method clause: " + raw);
            }
            return new Rule(RuleType.CLASS, included, classClause.owner(), null, null,
                    classClause.annotation(), null);
        }

        private static ClassClause parseClassClause(String body)
        {
            if (body == null)
            {
                return new ClassClause(wildcardToPattern("*"), null);
            }
            List<String> tokens = tokens(body);
            Pattern annotation = takeAnnotation(tokens);
            if (tokens.size() != 1)
            {
                throw new IllegalArgumentException("Class clause must contain one class pattern: " + body);
            }
            return new ClassClause(wildcardToPattern(tokens.getFirst()), annotation);
        }

        private static Rule parseTypedField(
                String body,
                ClassClause classClause,
                boolean hasClassClause,
                boolean included)
        {
            List<String> tokens = tokens(body);
            Pattern memberAnnotation = takeAnnotation(tokens);
            String owner = null;
            String type = "*";
            String name;
            if (hasClassClause)
            {
                if (tokens.size() == 1)
                {
                    name = tokens.getFirst();
                }
                else if (tokens.size() == 2)
                {
                    type = tokens.get(0);
                    name = tokens.get(1);
                }
                else
                {
                    throw new IllegalArgumentException("Invalid field clause: " + body);
                }
            }
            else
            {
                if (tokens.size() == 1)
                {
                    name = tokens.getFirst();
                }
                else if (tokens.size() == 2)
                {
                    owner = tokens.get(0);
                    name = tokens.get(1);
                }
                else if (tokens.size() == 3)
                {
                    owner = tokens.get(0);
                    type = tokens.get(1);
                    name = tokens.get(2);
                }
                else
                {
                    throw new IllegalArgumentException("Invalid field clause: " + body);
                }
            }
            return new Rule(
                    RuleType.FIELD,
                    included,
                    owner == null ? classClause.owner() : wildcardToPattern(owner),
                    wildcardToPattern(name),
                    wildcardToPattern(readableTypeDescriptor(type)),
                    classClause.annotation(),
                    memberAnnotation);
        }

        private static Rule parseTypedMethod(
                String body,
                ClassClause classClause,
                boolean hasClassClause,
                boolean included)
        {
            List<String> tokens = tokens(body);
            Pattern memberAnnotation = takeAnnotation(tokens);
            String joined = String.join(" ", tokens);
            int start = joined.indexOf('(');
            int end = joined.lastIndexOf(')');
            if (start <= 0 || end < start)
            {
                throw new IllegalArgumentException("Invalid method clause: " + body);
            }
            String before = joined.substring(0, start).trim();
            String arguments = joined.substring(start + 1, end).trim();
            String suffixReturn = joined.substring(end + 1).trim();
            List<String> prefix = tokens(before);
            if (prefix.isEmpty())
            {
                throw new IllegalArgumentException("Method clause is missing a name: " + body);
            }
            String methodName = prefix.removeLast();
            String owner = null;
            String prefixReturn = "*";
            if (hasClassClause)
            {
                if (prefix.size() > 1)
                {
                    throw new IllegalArgumentException("Invalid method clause: " + body);
                }
                if (!prefix.isEmpty())
                {
                    prefixReturn = prefix.getFirst();
                }
            }
            else if (prefix.size() == 1)
            {
                if (looksLikeType(prefix.getFirst()))
                {
                    prefixReturn = prefix.getFirst();
                }
                else
                {
                    owner = prefix.getFirst();
                }
            }
            else if (prefix.size() == 2)
            {
                owner = prefix.get(0);
                prefixReturn = prefix.get(1);
            }
            else if (!prefix.isEmpty())
            {
                throw new IllegalArgumentException("Invalid method clause: " + body);
            }

            String returnType = suffixReturn.isEmpty() ? prefixReturn : suffixReturn;
            return new Rule(
                    RuleType.METHOD,
                    included,
                    owner == null ? classClause.owner() : wildcardToPattern(owner),
                    wildcardToPattern(methodName),
                    wildcardToPattern(readableMethodDescriptor(arguments, returnType)),
                    classClause.annotation(),
                    memberAnnotation);
        }

        private static Rule parseLegacy(String raw, boolean included)
        {
            if (raw.indexOf(',') >= 0)
            {
                String[] parts = raw.split("\\s*,\\s*", 3);
                if (parts.length < 2)
                {
                    throw new IllegalArgumentException("Invalid readable match rule: " + raw);
                }
                String descriptor = parts.length == 3 ? parts[2] : "*";
                if (descriptor.contains("("))
                {
                    int start = descriptor.indexOf('(');
                    int end = descriptor.lastIndexOf(')');
                    String returnType = descriptor.substring(0, start).trim();
                    String arguments = descriptor.substring(start + 1, end).trim();
                    return new Rule(RuleType.METHOD, included,
                            wildcardToPattern(parts[0]), wildcardToPattern(parts[1]),
                            wildcardToPattern(readableMethodDescriptor(arguments, returnType)), null, null);
                }
                return new Rule(RuleType.FIELD, included,
                        wildcardToPattern(parts[0]), wildcardToPattern(parts[1]),
                        wildcardToPattern(readableTypeDescriptor(descriptor)), null, null);
            }

            String[] parts = raw.split("\\s+");
            if (parts.length == 1)
            {
                return new Rule(RuleType.CLASS, included, wildcardToPattern(parts[0]),
                        null, null, null, null);
            }
            String owner = parts[0];
            int memberIndex = 1;
            Pattern annotation = null;
            if (parts.length > 2 && parts[1].startsWith("@"))
            {
                annotation = annotationToPattern(parts[1]);
                memberIndex = 2;
            }
            else if (parts[0].startsWith("@") && parts.length == 2)
            {
                return new Rule(RuleType.CLASS, included, wildcardToPattern(parts[1]),
                        null, null, annotationToPattern(parts[0]), null);
            }
            if (memberIndex >= parts.length)
            {
                throw new IllegalArgumentException("Invalid match rule: " + raw);
            }
            String member = parts[memberIndex];
            if (member.contains("(") && member.contains(")"))
            {
                int start = member.indexOf('(');
                return new Rule(RuleType.METHOD, included, wildcardToPattern(owner),
                        wildcardToPattern(member.substring(0, start)),
                        wildcardToPattern(member.substring(start)), null, annotation);
            }
            return new Rule(RuleType.FIELD, included, wildcardToPattern(owner),
                    wildcardToPattern(member), wildcardToPattern("*"), null, annotation);
        }

        private boolean matchesClass(
                String className,
                List<AnnotationNode> visible,
                List<AnnotationNode> invisible)
        {
            return owner.matcher(className).matches() &&
                   matchesAnnotation(classAnnotation, visible, invisible);
        }

        private boolean matchesMember(
                String className,
                String name,
                String desc,
                List<AnnotationNode> ownerVisible,
                List<AnnotationNode> ownerInvisible,
                List<AnnotationNode> memberVisible,
                List<AnnotationNode> memberInvisible)
        {
            return owner.matcher(className).matches() &&
                   memberName.matcher(name).matches() &&
                   descriptor.matcher(desc).matches() &&
                   matchesAnnotation(classAnnotation, ownerVisible, ownerInvisible) &&
                   matchesAnnotation(memberAnnotation, memberVisible, memberInvisible);
        }
    }

    private static List<String> tokens(String value)
    {
        if (value == null || value.isBlank())
        {
            return new ArrayList<>();
        }
        return new ArrayList<>(List.of(value.trim().split("\\s+")));
    }

    private static Pattern takeAnnotation(List<String> tokens)
    {
        if (!tokens.isEmpty() && tokens.getFirst().startsWith("@"))
        {
            return annotationToPattern(tokens.removeFirst());
        }
        return null;
    }

    private static boolean looksLikeType(String value)
    {
        return "*".equals(value) || value.endsWith("[]") || value.startsWith("[") ||
               value.startsWith("L") && value.endsWith(";") ||
               switch (value)
               {
                   case "void", "boolean", "byte", "char", "short", "int", "long", "float", "double",
                        "V", "Z", "B", "C", "S", "I", "J", "F", "D" -> true;
                   default -> value.contains(".") && !value.endsWith(".*");
               };
    }

    private static String readableMethodDescriptor(String arguments, String returnType)
    {
        StringBuilder descriptor = new StringBuilder("(");
        if (!arguments.isBlank())
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
        String value = type.trim();
        if (value.isEmpty() || "*".equals(value))
        {
            return "*";
        }
        if (value.matches("[VZBCSIJFD]") || value.startsWith("[") ||
            value.startsWith("L") && value.endsWith(";"))
        {
            return value;
        }
        int dimensions = 0;
        while (value.endsWith("[]"))
        {
            dimensions++;
            value = value.substring(0, value.length() - 2).trim();
        }
        String descriptor = switch (value)
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
            default -> 'L' + value.replace('.', '/') + ';';
        };
        return "[".repeat(dimensions) + descriptor;
    }

    private static boolean matchesAnnotation(
            Pattern expected,
            List<AnnotationNode> visible,
            List<AnnotationNode> invisible)
    {
        if (expected == null)
        {
            return true;
        }
        return matchesAnnotationList(expected, visible) || matchesAnnotationList(expected, invisible);
    }

    private static boolean matchesAnnotationList(Pattern expected, List<AnnotationNode> annotations)
    {
        if (annotations == null)
        {
            return false;
        }
        for (AnnotationNode annotation : annotations)
        {
            String normalized = normalizeAnnotation(annotation.desc);
            int separator = normalized.lastIndexOf('.');
            String simpleName = separator < 0 ? normalized : normalized.substring(separator + 1);
            if (expected.matcher(normalized).matches() || expected.matcher(simpleName).matches())
            {
                return true;
            }
        }
        return false;
    }

    private static Pattern annotationToPattern(String annotation)
    {
        return wildcardToPattern(normalizeAnnotation(annotation.substring(1)));
    }

    private static String normalizeAnnotation(String annotation)
    {
        String value = annotation.replace('/', '.');
        return value.startsWith("L") && value.endsWith(";")
                ? value.substring(1, value.length() - 1)
                : value;
    }

    private static String normalizeClassName(String name)
    {
        return name.replace('/', '.');
    }

    private static String unescape(String value)
    {
        return value.replace("\\*", "*").replace("\\<", "<").replace("\\>", ">");
    }

    private static Pattern wildcardToPattern(String wildcard)
    {
        StringBuilder regex = new StringBuilder("^");
        for (int index = 0; index < wildcard.length(); index++)
        {
            char value = wildcard.charAt(index);
            if (value == '*')
            {
                regex.append(".*");
            }
            else
            {
                if ("\\.[]{}()+-^$?|".indexOf(value) >= 0)
                {
                    regex.append('\\');
                }
                regex.append(value);
            }
        }
        return Pattern.compile(regex.append('$').toString());
    }
}
