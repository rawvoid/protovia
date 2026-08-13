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
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import io.github.rawvoid.protovia.processor.model.MessageModel;

import java.util.ArrayList;
import java.util.List;

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
     * Parses a {@link MessageModel} / {@link io.github.rawvoid.protovia.processor.model.FieldModel}
     * type string into a JavaPoet {@link TypeName} so generated sources use imports
     * instead of {@code java.lang.String} literals.
     */
    static TypeName sourceType(String name) {
        return parseSourceType(name.strip());
    }

    /** Alias for method signatures built from model type strings. */
    static TypeName rawType(String name) {
        return sourceType(name);
    }

    private static TypeName parseSourceType(String name) {
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
                    yield ArrayTypeName.of(parseSourceType(name.substring(0, name.length() - 2)));
                }
                int genericStart = indexOfGenericStart(name);
                if (genericStart > 0) {
                    String raw = name.substring(0, genericStart).strip();
                    String args = name.substring(genericStart + 1, name.length() - 1).strip();
                    List<TypeName> typeArgs = splitGenericArgs(args).stream()
                        .map(GenTypes::parseSourceType)
                        .toList();
                    yield ParameterizedTypeName.get(
                        resolveClassName(raw),
                        typeArgs.toArray(TypeName[]::new));
                }
                yield resolveClassName(name);
            }
        };
    }

    private static int indexOfGenericStart(String name) {
        int depth = 0;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '<' && depth == 0) {
                return i;
            }
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            }
        }
        return -1;
    }

    private static List<String> splitGenericArgs(String args) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            } else if (c == ',' && depth == 0) {
                parts.add(args.substring(start, i).strip());
                start = i + 1;
            }
        }
        if (start <= args.length()) {
            parts.add(args.substring(start).strip());
        }
        return parts;
    }

    private static ClassName resolveClassName(String name) {
        if (!name.contains(".")) {
            return ClassName.get("", name);
        }
        return ClassName.bestGuess(name);
    }
}
