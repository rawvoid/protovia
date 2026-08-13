package io.github.rawvoid.protovia.annotation;

import io.github.rawvoid.protovia.UnknownFields;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the single {@link UnknownFields} slot on a {@link ProtoMessage}.
 * Unrecognized tags are appended here and written back after known fields.
 * At most one per message. The Java type must be {@link UnknownFields}.
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.CLASS)
public @interface ProtoUnknown {
}
