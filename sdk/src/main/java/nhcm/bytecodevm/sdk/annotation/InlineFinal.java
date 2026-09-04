package nhcm.bytecodevm.sdk.annotation;

import nhcm.bytecodevm.sdk.enums.Toggle;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Encrypts and removes a static-final field selected for protected inlining. */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface InlineFinal
{
    Toggle enabled() default Toggle.ENABLED;
}
