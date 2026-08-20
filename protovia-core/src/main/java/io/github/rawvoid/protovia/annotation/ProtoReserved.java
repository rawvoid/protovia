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
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Occupies retired protobuf field numbers and names so they cannot be reused.
 * Repeatable. Meaningful only on {@link ProtoMessage} and {@link ProtoEnum}.
 *
 * <pre>{@code
 * @ProtoMessage
 * @ProtoReserved(numbers = {4, 5}, names = "legacy_tag")
 * @ProtoReserved(ranges = @ProtoReserved.Range(from = 10, to = 12))
 * public class User { ... }
 * }</pre>
 *
 * {@code names} are proto export names (today: Java member / enum constant names).
 * Overlapping declarations are idempotent. Reusing a reserved number or name
 * on a current field, oneof case, or enum constant fails compilation.
 *
 * @author Rawvoid
 */
@Documented
@Repeatable(ProtoReserved.List.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface ProtoReserved {

    /**
     * Individual reserved numbers. Message numbers follow field-number rules
     * ({@code [1, 536870911]}, not {@code [19000, 19999]}). Enum numbers are
     * any {@code int32}.
     */
    int[] numbers() default {};

    /**
     * Inclusive reserved ranges, matching proto {@code 10 to 12}.
     */
    Range[] ranges() default {};

    /**
     * Reserved proto names. Must be identifiers {@code [_A-Za-z][_A-Za-z0-9]*}.
     */
    String[] names() default {};

    /**
     * Inclusive number range.
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({})
    @interface Range {

        int from();

        int to();
    }

    /**
     * Container for repeated {@link ProtoReserved}.
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE)
    @interface List {

        ProtoReserved[] value();
    }
}
