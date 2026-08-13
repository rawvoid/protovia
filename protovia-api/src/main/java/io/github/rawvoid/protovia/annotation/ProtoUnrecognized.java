package io.github.rawvoid.protovia.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Java-only sentinel for an unknown enum number. Must not have {@link ProtoEnumValue}.
 * Never written to the wire; the original number is kept in {@code UnknownFields} if present.
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
public @interface ProtoUnrecognized {
}
