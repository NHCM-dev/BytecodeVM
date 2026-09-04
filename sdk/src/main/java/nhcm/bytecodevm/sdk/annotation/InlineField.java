package nhcm.bytecodevm.sdk.annotation;

import nhcm.bytecodevm.sdk.enums.Toggle;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Moves a primitive, String, object, interface, or array field into BytecodeVM's keyed field storage.
 * Reference identity is preserved through encrypted randomized handles, while instance owners are tracked
 * by weak identity keys so field storage does not retain otherwise unreachable objects.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface InlineField
{
    Toggle enabled() default Toggle.ENABLED;
}
