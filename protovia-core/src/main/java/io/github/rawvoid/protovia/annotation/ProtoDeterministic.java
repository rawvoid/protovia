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
 * Writes {@code Map} fields in sorted wire-key order so the same logical
 * entries produce the same bytes. Default encoding still follows
 * {@link java.util.Map#entrySet()} iteration order.
 *
 * <p>Resolution, first match wins:
 * field / getter / record component → {@code @ProtoMessage} type → mixin
 * superclasses (near to far) → the leaf type's {@code package-info} →
 * {@code false}. Nested messages are resolved independently.
 *
 * <pre>{@code
 * @ProtoField(number = 1)
 * @ProtoDeterministic
 * Map<String, Integer> scores;
 *
 * @ProtoMessage
 * @ProtoDeterministic
 * public class SignedEnvelope { ... }
 *
 * @ProtoDeterministic
 * package example.v1;
 * }</pre>
 *
 * {@code @ProtoDeterministic(false)} opts a field or type out of a broader
 * default. Not valid on non-map members or {@link ProtoEnum} types.
 *
 * @author Rawvoid
 */
@Documented
@Target({
    ElementType.FIELD,
    ElementType.METHOD,
    ElementType.RECORD_COMPONENT,
    ElementType.TYPE,
    ElementType.PACKAGE
})
@Retention(RetentionPolicy.CLASS)
public @interface ProtoDeterministic {

    /**
     * {@code true} sorts map entries by wire key; {@code false} keeps
     * iteration order even when a broader scope is enabled.
     */
    boolean value() default true;
}
