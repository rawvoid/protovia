package io.github.rawvoid.protovia.annotation;

import io.github.rawvoid.protovia.UnknownFields;

import java.lang.annotation.*;

/**
 * Marks the single {@link UnknownFields} slot on a {@link ProtoMessage}.
 * Unrecognized tags are appended here and written back after known fields.
 * At most one per message. The Java type must be {@link UnknownFields}.
 *
 * @author Rawvoid
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.CLASS)
public @interface ProtoUnknown {
}
