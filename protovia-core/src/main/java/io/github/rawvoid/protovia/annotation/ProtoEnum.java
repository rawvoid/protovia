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

import java.lang.annotation.*;

/**
 * Marks a Java enum as a Protobuf enum. Each constant must have {@link ProtoEnumValue}.
 * Generated {@code .proto} constants are prefixed with the enum type name
 * and the Java constant is snake-cased to {@code UPPER_SNAKE_CASE}
 * ({@code Status.ACTIVE} / {@code Status.ActiveUser} → {@code STATUS_ACTIVE} /
 * {@code STATUS_ACTIVE_USER}).
 *
 * @author Rawvoid
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface ProtoEnum {

    /**
     * Protobuf enum name. Defaults to the Java simple class name.
     */
    String name() default "";

    /**
     * Protobuf package for {@code .proto} export.
     * Blank (the default) uses the Java package of the annotated type.
     * Full name is {@code packageName + "." + name} when package is non-empty.
     */
    String packageName() default "";
}
