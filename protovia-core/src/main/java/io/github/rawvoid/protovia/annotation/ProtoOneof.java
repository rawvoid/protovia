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
 * Marks a field, JavaBean getter, or record component as a proto3 oneof.
 * The group itself has no field number; each {@link Case#number()} belongs
 * to the parent message. {@code sealed} is optional and is not consulted.
 *
 * <pre>{@code
 * @ProtoOneof({
 *     @ProtoOneof.Case(number = 10, of = Email.class),
 *     @ProtoOneof.Case(number = 11, of = Home.class)
 * })
 * private Target target;
 * }</pre>
 *
 * @author Rawvoid
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.CLASS)
public @interface ProtoOneof {

    /**
     * Protobuf oneof group name used by {@code .proto} export.
     * Defaults to the Java member name. Must be a proto identifier and
     * not a proto keyword. The group itself has no field number.
     */
    String name() default "";

    /**
     * Cases in declaration order. At least one is required
     * (proto3 allows a single-field oneof). An empty array is rejected.
     */
    Case[] value();

    /**
     * One parent-message field that this oneof may hold.
     * Only usable as an element of {@link ProtoOneof#value()}.
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({})
    @interface Case {

        /**
         * Parent-message field number, in {@code [1, 536870911]},
         * not in {@code [19000, 19999]}. Must be unique in the message.
         */
        int number();

        /**
         * Runtime type stored in the oneof field: a 0- or 1-component record,
         * a {@link ProtoMessage}, or a naked scalar / enum / {@code byte[]}
         * (for example {@code String.class}). Duplicate or overlapping
         * {@code of} types in one oneof are rejected.
         */
        Class<?> of();

        /**
         * Protobuf field name of this case used by {@code .proto} export.
         * Defaults from the Java payload shape (type simple name, or the
         * message component name). Must be a proto identifier and not a
         * proto keyword.
         */
        String name() default "";

        /**
         * Wire type of this case's scalar payload.
         * {@link ProtoType#AUTO} infers from the Java type or the adapter.
         */
        ProtoType type() default ProtoType.AUTO;

        /**
         * Case-level adapter for a naked scalar or a 1-component scalar record.
         * {@link ProtoAdapter.Unset} means resolve from
         * {@link ProtoAdapters} / {@link ProtoAdapted}.
         */
        Class<? extends ProtoAdapter<?, ?>> adapter() default ProtoAdapter.Unset.class;
    }
}
