package nhcm.bytecodevm.config.sdk;

import nhcm.bytecodevm.config.BytecodeVMConfig;
import nhcm.bytecodevm.enums.VMStructure;

/** Nullable SDK overrides; null means use the YAML-derived value. */
public record SdkAnnotationOptions(
        boolean present,
        Boolean enabled,
        Boolean preEncryptStrings,
        Boolean preEncryptNumbers,
        VMStructure vmStructure,
        Boolean encrypt,
        Boolean shuffle,
        Boolean obfuscate,
        Boolean integrityCheck,
        SdkCallPolicy callPolicy,
        Boolean superInstruction,
        BytecodeVMConfig.SuperInstructionMode superInstructionMode,
        Integer superInstructionCombineMin,
        Integer superInstructionCombineMax,
        Integer superInstructionMinFrequency)
{
    public static SdkAnnotationOptions empty()
    {
        return new SdkAnnotationOptions(
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /** Applies explicitly supplied child values over enclosing class values. */
    public SdkAnnotationOptions overlay(SdkAnnotationOptions child)
    {
        if (!child.present)
        {
            return this;
        }
        return new SdkAnnotationOptions(
                present || child.present,
                choose(enabled, child.enabled),
                choose(preEncryptStrings, child.preEncryptStrings),
                choose(preEncryptNumbers, child.preEncryptNumbers),
                choose(vmStructure, child.vmStructure),
                choose(encrypt, child.encrypt),
                choose(shuffle, child.shuffle),
                choose(obfuscate, child.obfuscate),
                choose(integrityCheck, child.integrityCheck),
                choose(callPolicy, child.callPolicy),
                choose(superInstruction, child.superInstruction),
                choose(superInstructionMode, child.superInstructionMode),
                choose(superInstructionCombineMin, child.superInstructionCombineMin),
                choose(superInstructionCombineMax, child.superInstructionCombineMax),
                choose(superInstructionMinFrequency, child.superInstructionMinFrequency));
    }

    private static <T> T choose(T parent, T child)
    {
        return child == null ? parent : child;
    }

    public enum SdkCallPolicy
    {
        NONE,
        INCLUDE,
        EXCLUDE
    }
}
