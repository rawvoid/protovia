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

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/**
 * One {@code @ProtoField} or {@code @ProtoOneof} member of a message.
 *
 * @author Rawvoid
 */
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
    public final EnumModel enumModel;
    public final TypeElement messageType;
    public final FieldModel element;
    public final FieldModel mapKey;
    public final FieldModel mapValue;
    public final Element origin;
    public final boolean array;
    public final String arrayComponentType;
    public final java.util.List<OneofCaseModel> oneofCases;

    private FieldModel(Builder b) {
        this.number = b.number;
        this.name = b.name;
        this.localName = b.localName;
        this.kind = b.kind;
        this.protoType = b.protoType;
        this.optional = b.optional;
        this.packed = b.packed;
        this.primitive = b.primitive;
        this.javaOptional = b.javaOptional;
        this.byteArray = b.byteArray;
        this.byteBuffer = b.byteBuffer;
        this.accessKind = b.accessKind;
        this.readExpr = b.readExpr;
        this.setterName = b.setterName;
        this.fieldName = b.fieldName;
        this.javaTypeName = b.javaTypeName;
        this.javaType = b.javaType;
        this.implTypeName = b.implTypeName;
        this.implType = b.implType;
        this.codecName = b.codecName;
        this.enumModel = b.enumModel;
        this.messageType = b.messageType;
        this.element = b.element;
        this.mapKey = b.mapKey;
        this.mapValue = b.mapValue;
        this.origin = b.origin;
        this.array = b.array;
        this.arrayComponentType = b.arrayComponentType;
        this.oneofCases = b.oneofCases == null ? java.util.List.of() : java.util.List.copyOf(b.oneofCases);
    }

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
        if (el == null || el.kind != FieldKind.SCALAR || el.protoType == null) {
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

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int number;
        private String name;
        private String localName;
        private FieldKind kind;
        private ProtoType protoType;
        private boolean optional;
        private boolean packed;
        private boolean primitive;
        private boolean javaOptional;
        private boolean byteArray;
        private boolean byteBuffer;
        private AccessKind accessKind;
        private String readExpr;
        private String setterName;
        private String fieldName;
        private String javaTypeName;
        private TypeMirror javaType;
        private String implTypeName;
        private TypeElement implType;
        private String codecName;
        private EnumModel enumModel;
        private TypeElement messageType;
        private FieldModel element;
        private FieldModel mapKey;
        private FieldModel mapValue;
        private Element origin;
        private boolean array;
        private String arrayComponentType;
        private java.util.List<OneofCaseModel> oneofCases;

        public Builder number(int number) {
            this.number = number;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            this.localName = name;
            return this;
        }

        public Builder localName(String localName) {
            this.localName = localName;
            return this;
        }

        public Builder kind(FieldKind kind) {
            this.kind = kind;
            return this;
        }

        public Builder protoType(ProtoType protoType) {
            this.protoType = protoType;
            return this;
        }

        public Builder optional(boolean optional) {
            this.optional = optional;
            return this;
        }

        public Builder packed(boolean packed) {
            this.packed = packed;
            return this;
        }

        public Builder primitive(boolean primitive) {
            this.primitive = primitive;
            return this;
        }

        public Builder javaOptional(boolean javaOptional) {
            this.javaOptional = javaOptional;
            return this;
        }

        public Builder byteArray(boolean byteArray) {
            this.byteArray = byteArray;
            return this;
        }

        public Builder byteBuffer(boolean byteBuffer) {
            this.byteBuffer = byteBuffer;
            return this;
        }

        public Builder accessKind(AccessKind accessKind) {
            this.accessKind = accessKind;
            return this;
        }

        public Builder readExpr(String readExpr) {
            this.readExpr = readExpr;
            return this;
        }

        public Builder setterName(String setterName) {
            this.setterName = setterName;
            return this;
        }

        public Builder fieldName(String fieldName) {
            this.fieldName = fieldName;
            return this;
        }

        public Builder javaTypeName(String javaTypeName) {
            this.javaTypeName = javaTypeName;
            return this;
        }

        public Builder javaType(TypeMirror javaType) {
            this.javaType = javaType;
            return this;
        }

        public Builder implTypeName(String implTypeName) {
            this.implTypeName = implTypeName;
            return this;
        }

        public Builder implType(TypeElement implType) {
            this.implType = implType;
            return this;
        }

        public Builder codecName(String codecName) {
            this.codecName = codecName;
            return this;
        }

        public Builder enumModel(EnumModel enumModel) {
            this.enumModel = enumModel;
            return this;
        }

        public Builder messageType(TypeElement messageType) {
            this.messageType = messageType;
            return this;
        }

        public Builder element(FieldModel element) {
            this.element = element;
            return this;
        }

        public Builder mapKey(FieldModel mapKey) {
            this.mapKey = mapKey;
            return this;
        }

        public Builder mapValue(FieldModel mapValue) {
            this.mapValue = mapValue;
            return this;
        }

        public Builder origin(Element origin) {
            this.origin = origin;
            return this;
        }

        public Builder array(boolean array) {
            this.array = array;
            return this;
        }

        public Builder arrayComponentType(String arrayComponentType) {
            this.arrayComponentType = arrayComponentType;
            return this;
        }

        public Builder oneofCases(java.util.List<OneofCaseModel> oneofCases) {
            this.oneofCases = oneofCases;
            return this;
        }

        public FieldModel build() {
            return new FieldModel(this);
        }
    }
}
