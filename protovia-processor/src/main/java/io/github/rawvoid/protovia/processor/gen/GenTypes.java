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
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import io.github.rawvoid.protovia.processor.model.EnumModel;
import io.github.rawvoid.protovia.processor.model.FieldKind;
import io.github.rawvoid.protovia.processor.model.FieldModel;
import io.github.rawvoid.protovia.processor.model.MessageModel;
import io.github.rawvoid.protovia.processor.model.OneofCaseModel;

import javax.lang.model.type.TypeMirror;

/**
 * JavaPoet type names used by the codec generator.
 *
 * @author Rawvoid
 */
final class GenTypes {

    static final ClassName PROTO_EXCEPTION = ClassName.get("io.github.rawvoid.protovia", "ProtoException");
    static final ClassName UNKNOWN_FIELDS = ClassName.get("io.github.rawvoid.protovia", "UnknownFields");
    static final ClassName PROTO_CODEC = ClassName.get("io.github.rawvoid.protovia.codec", "ProtoCodec");
    static final ClassName CODED_SIZE = ClassName.get("io.github.rawvoid.protovia.wire", "CodedSize");
    static final ClassName PROTO_READER = ClassName.get("io.github.rawvoid.protovia.wire", "ProtoReader");
    static final ClassName PROTO_WRITER = ClassName.get("io.github.rawvoid.protovia.wire", "ProtoWriter");
    static final ClassName SIZE_CACHE = ClassName.get("io.github.rawvoid.protovia.wire", "SizeCache");
    static final ClassName WIRE_TYPE = ClassName.get("io.github.rawvoid.protovia.wire", "WireType");
    static final ClassName PROTO_LISTS = ClassName.get("io.github.rawvoid.protovia.collect", "ProtoLists");
    static final ClassName OPTIONAL = ClassName.get("java.util", "Optional");
    static final ClassName MAP = ClassName.get("java.util", "Map");
    static final ClassName ARRAY_LIST = ClassName.get("java.util", "ArrayList");

    private GenTypes() {
    }

    static ClassName messageType(MessageModel model) {
        return ClassName.get(model.type);
    }

    static ClassName enumType(EnumModel model) {
        return ClassName.get(model.type);
    }

    static TypeName oneofCaseType(OneofCaseModel c) {
        if (c.type == null) {
            return TypeName.get(c.payload.javaType);
        }
        return ClassName.get(c.type);
    }

    static TypeName javaType(FieldModel field) {
        return TypeName.get(field.javaType);
    }

    static TypeName javaType(TypeMirror type) {
        return TypeName.get(type);
    }

    static TypeName boxedType(FieldModel field) {
        if (field.kind == FieldKind.ENUM && field.enumModel != null) {
            return enumType(field.enumModel);
        }
        return javaType(field).box();
    }

    static ClassName codecType(FieldModel field) {
        return className(field.codecName);
    }

    static CodeBlock codecInstance(FieldModel field) {
        return CodeBlock.of("$T.INSTANCE", codecType(field));
    }

    static CodeBlock adapterInstance(FieldModel field) {
        return CodeBlock.of("$T.INSTANCE", ClassName.get(field.adapterType));
    }

    static ClassName implType(FieldModel field) {
        return ClassName.get(field.implType);
    }

    static CodeBlock implConstructorRef(FieldModel field) {
        return CodeBlock.of("$T::new", implType(field));
    }

    static CodeBlock newImpl(FieldModel field) {
        return newImpl(field, null);
    }

    static CodeBlock newImpl(FieldModel field, String copyFrom) {
        ClassName impl = implType(field);
        boolean diamond = !field.implType.getTypeParameters().isEmpty();
        if (copyFrom == null) {
            return diamond ? CodeBlock.of("new $T<>()", impl) : CodeBlock.of("new $T()", impl);
        }
        return diamond
            ? CodeBlock.of("new $T<>($L)", impl, copyFrom)
            : CodeBlock.of("new $T($L)", impl, copyFrom);
    }

    static CodeBlock defaultValue(TypeMirror type) {
        TypeName name = TypeName.get(type);
        if (UNKNOWN_FIELDS.equals(name)) {
            return CodeBlock.of("$T.EMPTY", UNKNOWN_FIELDS);
        }
        if (isOptional(name)) {
            return CodeBlock.of("$T.empty()", OPTIONAL);
        }
        return switch (type.getKind()) {
            case BOOLEAN -> CodeBlock.of("false");
            case BYTE, SHORT, INT, CHAR -> CodeBlock.of("0");
            case LONG -> CodeBlock.of("0L");
            case FLOAT -> CodeBlock.of("0F");
            case DOUBLE -> CodeBlock.of("0D");
            default -> CodeBlock.of("null");
        };
    }

    static CodeBlock enumConstant(EnumModel model, String name) {
        return CodeBlock.of("$T.$L", enumType(model), name);
    }

    private static boolean isOptional(TypeName name) {
        if (OPTIONAL.equals(name)) {
            return true;
        }
        return name instanceof ParameterizedTypeName parameterized && OPTIONAL.equals(parameterized.rawType());
    }

    /**
     * Package/simple split for codec names. Same-package codecs are stored as a
     * bare simple name; well-known codecs are stored as a fully-qualified name.
     */
    private static ClassName className(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return ClassName.get("", name);
        }
        return ClassName.get(name.substring(0, dot), name.substring(dot + 1));
    }
}
