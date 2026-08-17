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

import io.github.rawvoid.protovia.ProtoType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;

/**
 * One {@code @ProtoField} or {@code @ProtoOneof} member of a message.
 *
 * @author Rawvoid
 */
@Getter
@Builder(builderClassName = "Builder", toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class FieldModel {

    public final int number;
    public final String name;
    public final String localName;
    public final FieldKind kind;
    public final ProtoType protoType;
    public final boolean optional;
    public final boolean packed;
    public final boolean primitive;
    public final boolean javaOptional;
    public final boolean byteArray;
    public final boolean byteBuffer;
    public final AccessKind accessKind;
    public final String readExpr;
    public final String setterName;
    public final String fieldName;
    public final String javaTypeName;
    public final TypeMirror javaType;
    public final String implTypeName;
    public final TypeElement implType;
    public final String codecName;
    public final TypeElement adapterType;
    public final TypeMirror wireJavaType;
    public final EnumModel enumModel;
    public final TypeElement messageType;
    public final FieldModel element;
    public final FieldModel mapKey;
    public final FieldModel mapValue;
    public final Element origin;
    public final boolean array;
    public final String arrayComponentType;
    @Singular("oneofCase")
    public final List<OneofCaseModel> oneofCases;

    public boolean packable() {
        if (kind != FieldKind.REPEATED || element == null) {
            return false;
        }
        return switch (element.kind) {
            case SCALAR -> element.protoType != ProtoType.STRING && element.protoType != ProtoType.BYTES;
            case ENUM -> true;
            default -> false;
        };
    }

    public String primitiveListClass() {
        FieldModel el = kind == FieldKind.REPEATED ? element : this;
        if (el == null || el.kind != FieldKind.SCALAR || el.protoType == null || el.adapterType != null) {
            return null;
        }
        return switch (el.protoType) {
            case INT32, UINT32, SINT32, FIXED32, SFIXED32 -> "io.github.rawvoid.protovia.collect.IntArrayList";
            case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> "io.github.rawvoid.protovia.collect.LongArrayList";
            case FLOAT -> "io.github.rawvoid.protovia.collect.FloatArrayList";
            case DOUBLE -> "io.github.rawvoid.protovia.collect.DoubleArrayList";
            case BOOL -> "io.github.rawvoid.protovia.collect.BooleanArrayList";
            default -> null;
        };
    }

    public String primitiveListType() {
        if (array) {
            return element != null && element.primitive ? primitiveListClass() : null;
        }
        if (implType != null) {
            String qn = implType.getQualifiedName().toString();
            if (qn.startsWith("io.github.rawvoid.protovia.collect.")) {
                return qn;
            }
        }
        return null;
    }
}
