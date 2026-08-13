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

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeVariableName;
import io.github.rawvoid.protovia.processor.model.MessageModel;

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

    private GenTypes() {
    }

    static ClassName messageType(MessageModel model) {
        return ClassName.bestGuess(fqcn(model));
    }

    static String fqcn(MessageModel model) {
        if (!model.typeName.isEmpty()
            && model.typeName.contains(".")
            && Character.isLowerCase(model.typeName.charAt(0))) {
            return model.typeName;
        }
        return model.packageName.isEmpty() ? model.typeName : model.packageName + "." + model.typeName;
    }

    /**
     * TypeName for generated signatures that must keep relative / generic source names
     * ({@code List<Integer>}, {@code Outer.Inner}, {@code int[]}).
     */
    static TypeName rawType(String name) {
        return switch (name) {
            case "int" -> TypeName.INT;
            case "long" -> TypeName.LONG;
            case "float" -> TypeName.FLOAT;
            case "double" -> TypeName.DOUBLE;
            case "boolean" -> TypeName.BOOLEAN;
            case "byte" -> TypeName.BYTE;
            case "short" -> TypeName.SHORT;
            case "char" -> TypeName.CHAR;
            case "void" -> TypeName.VOID;
            default -> {
                if (name.endsWith("[]")) {
                    yield ArrayTypeName.of(rawType(name.substring(0, name.length() - 2)));
                }
                yield TypeVariableName.get(name);
            }
        };
    }
}
