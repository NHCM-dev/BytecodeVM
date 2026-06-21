package nhcm.bytecodevm.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ExclusionMatcher
{
    private final List<Rule> rules = new ArrayList<>();

    public void add(String rule)
    {
        rules.add(Rule.parse(rule));
    }

    public boolean isClassExcluded(String className)
    {
        className = internalClassName(className);

        for(Rule rule : rules)
        {
            if(rule.type == RuleType.CLASS && rule.matchesClass(className))
            {
                return true;
            }
        }

        return false;
    }

    public boolean isFieldExcluded(String owner, String name, String desc)
    {
        owner = internalClassName(owner);

        for(Rule rule : rules)
        {
            if(rule.type == RuleType.FIELD && rule.matchesMember(owner, name, desc))
            {
                return true;
            }
        }

        return false;
    }

    public boolean isMethodExcluded(String owner, String name, String desc)
    {
        owner = internalClassName(owner);

        for(Rule rule : rules)
        {
            if(rule.type == RuleType.METHOD && rule.matchesMember(owner, name, desc))
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

        private Rule(RuleType type, Pattern classPattern, Pattern memberNamePattern, Pattern descPattern)
        {
            this.type = type;
            this.classPattern = classPattern;
            this.memberNamePattern = memberNamePattern;
            this.descPattern = descPattern;
        }

        public static Rule parse(String raw)
        {
            raw = raw.trim();

            String[] parts = raw.split("\\s+", 2);

            if(parts.length == 1)
            {
                return new Rule(
                        RuleType.CLASS,
                        wildcardToPattern(parts[0]),
                        null,
                        null
                );
            }

            String classPart = parts[0];
            String memberPart = parts[1];

            if(memberPart.contains("(") && memberPart.contains(")"))
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
                        wildcardToPattern(desc)
                );
            }

            return new Rule(
                    RuleType.FIELD,
                    wildcardToPattern(classPart),
                    wildcardToPattern(memberPart),
                    wildcardToPattern("*")
            );
        }

        public boolean matchesClass(String className)
        {
            return classPattern.matcher(className).matches();
        }

        public boolean matchesMember(String owner, String name, String desc)
        {
            return classPattern.matcher(owner).matches()
                    && memberNamePattern.matcher(name).matches()
                    && descPattern.matcher(desc).matches();
        }

        private static Pattern wildcardToPattern(String wildcard)
        {
            StringBuilder regex = new StringBuilder();

            regex.append("^");

            for(int i = 0; i < wildcard.length(); i++)
            {
                char c = wildcard.charAt(i);

                if(c == '*')
                {
                    regex.append(".*");
                }
                else
                {
                    if("\\.[]{}()+-^$?|".indexOf(c) >= 0)
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