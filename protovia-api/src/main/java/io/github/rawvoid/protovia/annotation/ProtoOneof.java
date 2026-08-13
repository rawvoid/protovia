package io.github.rawvoid.protovia.annotation;

import java.lang.annotation.*;

/**
 * Marks a field, getter, or record component as a proto3 oneof.
 * The Java type must be a {@code sealed} interface (or class) whose permitted
 * subtypes each have {@link ProtoOneofCase}. The member has no field number.
 *
 * @author Rawvoid
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.CLASS)
public @interface ProtoOneof {
}
