package io.github.rawvoid.protovia.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field, getter, or record component as a proto3 oneof.
 * The Java type must be a {@code sealed} interface (or class) whose permitted
 * subtypes each have {@link ProtoOneofCase}. The member has no field number.
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.CLASS)
public @interface ProtoOneof {
}
