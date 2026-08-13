package io.github.rawvoid.protovia.annotation;

import java.lang.annotation.*;

/**
 * Protobuf number of an enum constant. Number {@code 0} is required (proto3).
 *
 * @author Rawvoid
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
