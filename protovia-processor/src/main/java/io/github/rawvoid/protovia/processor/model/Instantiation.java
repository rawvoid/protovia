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

package io.github.rawvoid.protovia.processor.model;

import java.util.List;

/**
 * How generated {@code mergeFrom} produces an instance. Decode has two shapes:
 * mutate a {@code new T()} ( {@link Mutable} ) or accumulate locals and then
 * construct (every other variant).
 *
 * @author Rawvoid
 */
public sealed interface Instantiation {

    Mutable MUTABLE = new Mutable();

    /**
     * @return {@code true} when decode writes locals and calls a constructor,
     * factory, or builder rather than setters on {@code msg}
     */
    default boolean usesLocals() {
        return !(this instanceof Mutable);
    }

    /**
     * JavaBean: public no-arg constructor plus setters / assignable fields.
     */
    record Mutable() implements Instantiation {
    }

    /**
     * {@code new Type(a, b, c)} in {@link Slot} order.
     */
    record Constructor(List<Slot> slots) implements Instantiation {
    }

    /**
     * {@code Type.factory(a, b, c)} in {@link Slot} order.
     */
    record Factory(String methodName, List<Slot> slots) implements Instantiation {
    }

    /**
     * {@code Type.builder()} or {@code new Type.Builder()}, setters, {@code build()}.
     */
    record Builder(
        String factoryMethod,
        String nestedClass,
        String buildMethod,
        List<BuilderBinding> bindings) implements Instantiation {
    }

    /**
     * One constructor / factory argument, bound to a proto member or record component.
     *
     * @param localName generated local holding the value
     * @param field     proto field, or {@code null} for unknown / unannotated record components
     * @param unknown   whether this slot is the {@code @ProtoUnknown} member
     */
    record Slot(String localName, FieldModel field, boolean unknown) {
    }

    /**
     * One builder setter call {@code builder.setter(localName)}.
     */
    record BuilderBinding(String setterName, String localName, FieldModel field, boolean unknown) {
    }
}
