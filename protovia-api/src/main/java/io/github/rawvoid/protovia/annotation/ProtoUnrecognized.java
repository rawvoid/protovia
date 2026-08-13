package io.github.rawvoid.protovia.annotation;

import java.lang.annotation.*;

/**
 * Java-only sentinel for an unknown enum number. Must not have {@link ProtoEnumValue}.
 * Never written to the wire; the original number is kept in {@code UnknownFields} if present.
 *
 * @author Rawvoid
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
public @interface ProtoUnrecognized {
}
