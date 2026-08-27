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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Selects a builder for decoding an immutable {@link ProtoMessage} when the
 * default {@code builder()} / {@code build()} / fluent-setter convention does
 * not apply. Lombok {@code @Builder} and handwritten builders that follow that
 * convention do not need this annotation.
 *
 * @author Rawvoid
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface ProtoBuilder {

    /**
     * Public static factory that returns the builder. Empty means construct
     * {@code new NestedClass()} instead.
     */
    String builderMethod() default "builder";

    /**
     * Nested builder class used when {@link #builderMethod()} is empty.
     */
    String builderClass() default "Builder";

    /**
     * No-arg method on the builder that returns the message.
     */
    String buildMethod() default "build";

    /**
     * Setter prefix: empty uses {@code name} then {@code setName} then
     * {@code withName}; {@code "set"} / {@code "with"} restrict matching.
     */
    String setterPrefix() default "";
}
