package io.github.rawvoid.protovia.annotation;

import io.github.rawvoid.protovia.ProtoType;

import java.lang.annotation.*;

/**
 * Marks a field, JavaBean getter, or record component as a Protobuf field.
 * Unannotated members are ignored.
 *
 * @author Rawvoid
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.CLASS)
public @interface ProtoField {

    /**
     * Field number. Must be unique in the message, in {@code [1, 536870911]},
     * and not in the reserved range {@code [19000, 19999]}.
     */
    int number();

    /**
     * Wire type. {@link ProtoType#AUTO} infers from the Java type.
     */
    ProtoType type() default ProtoType.AUTO;

    /**
     * When the Java type is a map, overrides the key's protobuf type.
     */
    ProtoType keyType() default ProtoType.AUTO;

    /**
     * When the Java type is a map, overrides the value's protobuf type.
     */
    ProtoType valueType() default ProtoType.AUTO;

    /**
     * Pack packable repeated scalars (proto3 default). Ignored for non-packable types.
     */
    boolean packed() default true;

    /**
     * proto3 explicit presence: a present default value is written to the wire.
     * Requires a boxed / {@link java.util.Optional} Java type, not a primitive.
     */
    boolean optional() default false;
}
