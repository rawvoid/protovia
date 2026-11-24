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
import io.github.rawvoid.protovia.processor.model.CollectionKind;
import io.github.rawvoid.protovia.processor.model.FieldKind;
import io.github.rawvoid.protovia.processor.model.FieldModel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import static io.github.rawvoid.protovia.processor.gen.GenTypes.PROTO_LISTS;

/**
 * Specialized primitive list types from {@code protovia-core}.
 *
 * @author Rawvoid
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
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

    ClassName listType() {
        return ClassName.get(PROTO_LISTS.packageName(), simpleName);
    }

    static PrimitiveListSpec of(FieldModel field) {
        if (field.primitiveListType() == null) {
            return null;
        }
        return forElement(field);
    }

    /**
     * Returns the specialized primitive list spec for any repeated field whose runtime backing
     * collection can be a {@code protovia.collect.*ArrayList} (primitive arrays, {@code List<T>},
     * {@code Collection<T>}, or concrete {@code *ArrayList}).
     */
    static PrimitiveListSpec ofPacked(FieldModel field) {
        if (field.primitiveListType() != null) {
            return forElement(field);
        }
        if (!field.array
            && field.collectionKind == CollectionKind.COLLECTION
            && field.implType != null
            && field.implType.getQualifiedName().toString().startsWith("io.github.rawvoid.protovia.collect.")) {
            return forElement(field);
        }
        return null;
    }

    private static PrimitiveListSpec forElement(FieldModel field) {
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
}
