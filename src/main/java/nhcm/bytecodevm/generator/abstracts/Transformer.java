package nhcm.bytecodevm.generator.abstracts;

import nhcm.bytecodevm.config.BytecodeVMConfig;
import nhcm.bytecodevm.config.TargetMatcher;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Collection;

public abstract class Transformer
{
    protected final BytecodeVMConfig config;
    protected final TargetMatcher include;
    protected final TargetMatcher exclude;
    protected final String configKey;

    public Transformer(BytecodeVMConfig config, String configKey)
    {
        this.config = config;
        this.configKey = configKey;
        this.include = matcher(config.matchRules.includes(configKey));
        this.exclude = matcher(config.matchRules.exclusions(configKey));
    }

    /**
     * Edit bytecodes of input classNodes
     * @param classNodes all jar classes for transformation
     * @return amounts of changes commited
     */
    public abstract int transform(Collection<ClassNode> classNodes);

    protected boolean shouldEncrypt(ClassNode owner, Boolean sdkOverride)
    {
        return enabledByRules(
                sdkOverride,
                includeMatches(owner),
                exclude.isClassMatched(owner));
    }

    protected boolean shouldEncrypt(ClassNode owner, FieldNode field, Boolean sdkOverride)
    {
        return enabledByRules(
                sdkOverride,
                includeMatches(owner, field),
                exclude.isFieldContextMatched(owner, field));
    }

    protected boolean shouldEncrypt(ClassNode owner, MethodNode method, Boolean sdkOverride)
    {
        return enabledByRules(
                sdkOverride,
                includeMatches(owner, method),
                exclude.isMethodContextMatched(owner, method));
    }

    private static boolean enabledByRules(Boolean sdkOverride, boolean included, boolean excluded)
    {
        if (Boolean.FALSE.equals(sdkOverride))
        {
            return false;
        }
        return Boolean.TRUE.equals(sdkOverride) || (included && !excluded);
    }

    protected boolean includeMatches(ClassNode owner)
    {
        return config.matchRules.includes(configKey).length == 0 ||
               include.isClassMatched(owner);
    }

    protected boolean includeMatches(ClassNode owner, FieldNode field)
    {
        return config.matchRules.includes(configKey).length == 0 ||
               include.isFieldContextMatched(owner, field);
    }

    protected boolean includeMatches(ClassNode owner, MethodNode method)
    {
        return config.matchRules.includes(configKey).length == 0 ||
               include.isMethodContextMatched(owner, method);
    }

    private static TargetMatcher matcher(String[] rules)
    {
        TargetMatcher matcher = new TargetMatcher();
        for (String rule : rules)
        {
            matcher.add(rule);
        }
        return matcher;
    }
}
