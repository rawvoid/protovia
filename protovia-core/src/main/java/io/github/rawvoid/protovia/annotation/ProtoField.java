/*
 * Copyright 2026 Rawvoid(https://github.com/rawvoid)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.rawvoid.protovia.annotation;

import io.github.rawvoid.protovia.ProtoType;
import io.github.rawvoid.protovia.codec.ProtoAdapter;

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
     * Protobuf field name used by {@code .proto} export and JSON.
     * Defaults to the Java member name. Must be a proto identifier and
     * not a proto keyword. Does not affect the wire format.
     */
    String name() default "";

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

    /**
     * Field-level adapter. {@link ProtoAdapter.Unset} means resolve from
     * {@link ProtoAdapters} / {@link ProtoAdapted}.
     * On a {@code Map} this applies to the key or the value only when
     * the adapter's {@code J} matches that side.
     */
    Class<? extends ProtoAdapter<?, ?>> adapter() default ProtoAdapter.Unset.class;
}
