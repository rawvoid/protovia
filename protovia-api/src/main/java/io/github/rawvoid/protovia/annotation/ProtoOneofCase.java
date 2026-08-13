package io.github.rawvoid.protovia.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Field number of one permitted subtype of a {@link ProtoOneof} sealed type.
 * The type may be top-level or nested. Number belongs to the parent message.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface ProtoOneofCase {

    int value();
}
