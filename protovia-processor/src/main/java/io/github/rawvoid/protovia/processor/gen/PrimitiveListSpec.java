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

package io.github.rawvoid.protovia.processor.gen;

import com.palantir.javapoet.ClassName;
import io.github.rawvoid.protovia.processor.model.FieldKind;
import io.github.rawvoid.protovia.processor.model.FieldModel;

import static io.github.rawvoid.protovia.processor.gen.GenTypes.PROTO_LISTS;

/**
 * Specialized primitive list types from {@code protovia-runtime}.
 *
 * @author Rawvoid
 */
enum PrimitiveListSpec {
    INT("IntArrayList", "addInt", "ensureIntCapacity", "toIntArray", "getInt"),
    LONG("LongArrayList", "addLong", "ensureLongCapacity", "toLongArray", "getLong"),
    FLOAT("FloatArrayList", "addFloat", "ensureFloatCapacity", "toFloatArray", "getFloat"),
    DOUBLE("DoubleArrayList", "addDouble", "ensureDoubleCapacity", "toDoubleArray", "getDouble"),
    BOOLEAN("BooleanArrayList", "addBoolean", "ensureBooleanCapacity", "toBooleanArray", "getBoolean");

    private final String simpleName;
    private final String add;
    private final String ensure;
    private final String toArray;
    private final String get;

    PrimitiveListSpec(String simpleName, String add, String ensure, String toArray, String get) {
        this.simpleName = simpleName;
        this.add = add;
        this.ensure = ensure;
        this.toArray = toArray;
        this.get = get;
    }

    static PrimitiveListSpec of(FieldModel field) {
        if (field.primitiveListType() == null) {
            return null;
        }
        FieldModel el = field.kind == FieldKind.REPEATED ? field.element : field;
        if (el == null || el.protoType == null || el.adapterType != null) {
            return null;
        }
        return switch (el.protoType) {
            case INT32, UINT32, SINT32, FIXED32, SFIXED32 -> INT;
            case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> LONG;
            case FLOAT -> FLOAT;
            case DOUBLE -> DOUBLE;
            case BOOL -> BOOLEAN;
            default -> null;
        };
    }

    ClassName listType() {
        return ClassName.get(PROTO_LISTS.packageName(), simpleName);
    }

    String add() {
        return add;
    }

    String ensure() {
        return ensure;
    }

    String toArray() {
        return toArray;
    }

    String get() {
        return get;
    }
}
