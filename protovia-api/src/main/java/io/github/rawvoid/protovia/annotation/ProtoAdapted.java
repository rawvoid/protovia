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

import io.github.rawvoid.protovia.codec.ProtoAdapter;

import java.lang.annotation.*;

/**
 * Default {@link ProtoAdapter} for this user-owned Java type.
 * Not inherited: a subclass must declare its own mapping or use a field-level adapter.
 *
 * @author Rawvoid
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface ProtoAdapted {

    /**
     * Adapter that converts this type to a proto scalar.
     */
    Class<? extends ProtoAdapter<?, ?>> value();
}
