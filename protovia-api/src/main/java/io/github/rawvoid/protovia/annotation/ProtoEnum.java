package io.github.rawvoid.protovia.annotation;

import java.lang.annotation.*;

/**
 * Marks a Java enum as a Protobuf enum. Each constant must have {@link ProtoEnumValue}.
 *
 * @author Rawvoid
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface ProtoEnum {

    /**
     * Protobuf enum name. Defaults to the Java simple class name.
     */
    String name() default "";
}
