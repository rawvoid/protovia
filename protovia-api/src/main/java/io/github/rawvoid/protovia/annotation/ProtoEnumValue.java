package io.github.rawvoid.protovia.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Protobuf number of an enum constant. Number {@code 0} is required (proto3).
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
public @interface ProtoEnumValue {

    /**
     * Protobuf enum number. Use {@code @ProtoEnumValue(0)} for the proto3 zero value.
     */
    int value();
}
