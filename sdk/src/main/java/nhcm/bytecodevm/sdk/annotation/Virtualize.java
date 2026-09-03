package nhcm.bytecodevm.sdk.annotation;

import nhcm.bytecodevm.sdk.annotation.config.SuperInstructionOptions;
import nhcm.bytecodevm.sdk.annotation.config.VMOptions;
import nhcm.bytecodevm.sdk.enums.CallPolicy;
import nhcm.bytecodevm.sdk.enums.Toggle;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Selects a class or method for bytecode virtualization.
 * Values left at {@code CONFIG} are resolved from an enclosing annotation
 * and then from the obfuscator YAML configuration.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Virtualize
{
    /** Controls whether this target is virtualized. */
    Toggle enabled() default Toggle.ENABLED;

    /** Per-target VM overrides. */
    VMOptions vm() default @VMOptions;

    /** Pre-encrypts string constants selected for this target before virtualization. */
    Toggle preEncryptStrings() default Toggle.ENABLED;

    /** Pre-encrypts primitive number constants selected for this target before virtualization. */
    Toggle preEncryptNumbers() default Toggle.ENABLED;

    /** Per-target SuperInstruction overrides. */
    SuperInstructionOptions superInstructions() default @SuperInstructionOptions;

    /** Controls integrity protection for this target. */
    Toggle integrityCheck() default Toggle.CONFIG;

    /** Controls call-graph expansion from this target method. */
    CallPolicy calls() default CallPolicy.CONFIG;
}
