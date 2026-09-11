package nhcm.bytecodevm.generator.abstracts;

import nhcm.bytecodevm.config.BytecodeVMConfig;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Collection;

public abstract class Transformer
{
    protected final BytecodeVMConfig config;
    protected final String configKey;

    public Transformer(BytecodeVMConfig config, String configKey)
    {
        this.config = config;
        this.configKey = configKey;
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
                included(owner),
                excluded(owner));
    }

    protected boolean shouldEncrypt(ClassNode owner, FieldNode field, Boolean sdkOverride)
    {
        return enabledByRules(
                sdkOverride,
                included(owner, field),
                excluded(owner, field));
    }

    protected boolean shouldEncrypt(ClassNode owner, MethodNode method, Boolean sdkOverride)
    {
        return enabledByRules(
                sdkOverride,
                included(owner, method),
                excluded(owner, method));
    }

    private boolean included(ClassNode owner)
    {
        return config.matchRules.statementMatches("all", owner) &&
               ("all".equals(configKey) || config.matchRules.statementMatches(configKey, owner));
    }

    private boolean included(ClassNode owner, FieldNode field)
    {
        return config.matchRules.statementMatches("all", owner, field) &&
               ("all".equals(configKey) || config.matchRules.statementMatches(configKey, owner, field));
    }

    private boolean included(ClassNode owner, MethodNode method)
    {
        return config.matchRules.statementMatches("all", owner, method) &&
               ("all".equals(configKey) || config.matchRules.statementMatches(configKey, owner, method));
    }

    private boolean excluded(ClassNode owner)
    {
        return config.matchRules.classExcluded("all", owner) ||
               (!"all".equals(configKey) && config.matchRules.classExcluded(configKey, owner));
    }

    private boolean excluded(ClassNode owner, FieldNode field)
    {
        return config.matchRules.fieldExcluded("all", owner, field) ||
               (!"all".equals(configKey) && config.matchRules.fieldExcluded(configKey, owner, field));
    }

    private boolean excluded(ClassNode owner, MethodNode method)
    {
        return config.matchRules.methodExcluded("all", owner, method) ||
               (!"all".equals(configKey) && config.matchRules.methodExcluded(configKey, owner, method));
    }

    private static boolean enabledByRules(Boolean sdkOverride, boolean included, boolean excluded)
    {
        if (excluded || Boolean.FALSE.equals(sdkOverride))
        {
            return false;
        }
        return Boolean.TRUE.equals(sdkOverride) || included;
    }

}
