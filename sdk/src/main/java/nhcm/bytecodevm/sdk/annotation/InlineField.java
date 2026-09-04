package nhcm.bytecodevm.sdk.annotation;

import nhcm.bytecodevm.sdk.enums.Toggle;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Moves a field into BytecodeVM's keyed encrypted field container. */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface InlineField
{
    Toggle enabled() default Toggle.ENABLED;
}
