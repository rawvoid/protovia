package io.github.rawvoid.protovia.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Java class or record as a Protobuf message. The processor generates
 * {@code <SimpleName>ProtoCodec} in the same package.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface ProtoMessage {

    /**
     * Protobuf message name. Defaults to the Java simple class name.
     */
    String name() default "";

    /**
     * Protobuf package for Any {@code type_url} and {@code .proto} export.
     * Full name is {@code packageName + "." + name} when package is non-empty.
     */
    String packageName() default "";
}
