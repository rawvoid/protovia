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

import com.palantir.javapoet.CodeBlock;
import io.github.rawvoid.protovia.processor.model.FieldKind;
import io.github.rawvoid.protovia.processor.model.FieldModel;

import static io.github.rawvoid.protovia.processor.gen.GenTypes.CODED_SIZE;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.OPTIONAL;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.PROTO_LISTS;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.PROTO_READER;
import static io.github.rawvoid.protovia.processor.gen.WireTypes.boxed;
import static io.github.rawvoid.protovia.processor.gen.WireTypes.primitiveListSpec;

/**
 * JavaPoet {@link CodeBlock} fragments for wire read/write/size expressions.
 *
 * @author Rawvoid
 */
final class WireCodegen {

    private WireCodegen() {
    }

    static CodeBlock sizeCall(FieldModel field, int number, String value) {
        return switch (field.protoType) {
            case INT32 -> CodeBlock.of("$T.int32($L, $L)", CODED_SIZE, number, value);
            case UINT32 -> CodeBlock.of("$T.uint32($L, $L)", CODED_SIZE, number, value);
            case SINT32 -> CodeBlock.of("$T.sint32($L, $L)", CODED_SIZE, number, value);
            case INT64 -> CodeBlock.of("$T.int64($L, $L)", CODED_SIZE, number, value);
            case UINT64 -> CodeBlock.of("$T.uint64($L, $L)", CODED_SIZE, number, value);
            case SINT64 -> CodeBlock.of("$T.sint64($L, $L)", CODED_SIZE, number, value);
            case BOOL -> CodeBlock.of("$T.bool($L, $L)", CODED_SIZE, number, value);
            case FIXED32, SFIXED32, FLOAT -> CodeBlock.of("$T.fixed32($L)", CODED_SIZE, number);
            case FIXED64, SFIXED64, DOUBLE -> CodeBlock.of("$T.fixed64($L)", CODED_SIZE, number);
            case STRING -> CodeBlock.of("$T.string($L, $L)", CODED_SIZE, number, value);
            case BYTES -> CodeBlock.of("$T.bytes($L, $L)", CODED_SIZE, number, value);
            case ENUM -> CodeBlock.of("$T.enumValue($L, $L)", CODED_SIZE, number, value);
            default -> CodeBlock.of("$T.int32($L, $L)", CODED_SIZE, number, value);
        };
    }

    static CodeBlock sizeNoTag(FieldModel field, String value) {
        return switch (field.protoType) {
            case INT32, ENUM -> CodeBlock.of("$T.int32($L)", CODED_SIZE, value);
            case UINT32 -> CodeBlock.of("$T.uint32($L)", CODED_SIZE, value);
            case SINT32 -> CodeBlock.of("$T.sint32($L)", CODED_SIZE, value);
            case INT64 -> CodeBlock.of("$T.int64($L)", CODED_SIZE, value);
            case UINT64 -> CodeBlock.of("$T.uint64($L)", CODED_SIZE, value);
            case SINT64 -> CodeBlock.of("$T.sint64($L)", CODED_SIZE, value);
            case BOOL -> CodeBlock.of("$T.bool($L)", CODED_SIZE, value);
            case FIXED32, SFIXED32, FLOAT -> CodeBlock.of("4");
            case FIXED64, SFIXED64, DOUBLE -> CodeBlock.of("8");
            default -> CodeBlock.of("$T.int32($L)", CODED_SIZE, value);
        };
    }

    static CodeBlock writeNoTag(String writer, FieldModel field, String value) {
        if (field.byteBuffer) {
            return CodeBlock.of("$L.writeBytesNoTag($L)", writer, value);
        }
        return switch (field.protoType) {
            case INT32, ENUM -> CodeBlock.of("$L.writeInt32NoTag($L)", writer, value);
            case UINT32 -> CodeBlock.of("$L.writeUInt32NoTag($L)", writer, value);
            case SINT32 -> CodeBlock.of("$L.writeSInt32NoTag($L)", writer, value);
            case INT64, UINT64 -> CodeBlock.of("$L.writeUInt64NoTag($L)", writer, value);
            case SINT64 -> CodeBlock.of("$L.writeSInt64NoTag($L)", writer, value);
            case BOOL -> CodeBlock.of("$L.writeBoolNoTag($L)", writer, value);
            case FIXED32, SFIXED32 -> CodeBlock.of("$L.writeFixed32NoTag($L)", writer, value);
            case FLOAT -> CodeBlock.of("$L.writeFloatNoTag($L)", writer, value);
            case FIXED64, SFIXED64 -> CodeBlock.of("$L.writeFixed64NoTag($L)", writer, value);
            case DOUBLE -> CodeBlock.of("$L.writeDoubleNoTag($L)", writer, value);
            case STRING -> CodeBlock.of("$L.writeStringNoTag($L)", writer, value);
            case BYTES -> CodeBlock.of("$L.writeBytesNoTag($L)", writer, value);
            default -> CodeBlock.of("$L.writeInt32NoTag($L)", writer, value);
        };
    }

    static CodeBlock readCall(FieldModel field) {
        if (field.byteBuffer) {
            return CodeBlock.of("reader.readByteBuffer()");
        }
        return switch (field.protoType) {
            case INT32 -> CodeBlock.of("reader.readInt32()");
            case UINT32 -> CodeBlock.of("reader.readUInt32()");
            case SINT32 -> CodeBlock.of("reader.readSInt32()");
            case INT64 -> CodeBlock.of("reader.readInt64()");
            case UINT64 -> CodeBlock.of("reader.readUInt64()");
            case SINT64 -> CodeBlock.of("reader.readSInt64()");
            case BOOL -> CodeBlock.of("reader.readBool()");
            case FIXED32 -> CodeBlock.of("reader.readFixed32()");
            case SFIXED32 -> CodeBlock.of("reader.readSFixed32()");
            case FLOAT -> CodeBlock.of("reader.readFloat()");
            case FIXED64 -> CodeBlock.of("reader.readFixed64()");
            case SFIXED64 -> CodeBlock.of("reader.readSFixed64()");
            case DOUBLE -> CodeBlock.of("reader.readDouble()");
            case STRING -> CodeBlock.of("reader.readString()");
            case BYTES -> CodeBlock.of("reader.readBytes()");
            case ENUM -> CodeBlock.of("reader.readEnum()");
            default -> CodeBlock.of("reader.readInt32()");
        };
    }

    static CodeBlock wrapOptional(FieldModel field, CodeBlock expr) {
        if (field.javaOptional) {
            return CodeBlock.of("$T.of($L)", OPTIONAL, expr);
        }
        return expr;
    }

    static CodeBlock wrapOptional(FieldModel field, String expr) {
        return wrapOptional(field, CodeBlock.of("$L", expr));
    }

    static CodeBlock mapReadExpr(FieldModel part) {
        if (part.kind == FieldKind.MESSAGE) {
            return CodeBlock.of("reader.readMessage($L.INSTANCE)", part.codecName);
        }
        if (part.kind == FieldKind.ENUM) {
            return CodeBlock.of("$L(reader.readEnum())", GenNames.enumFrom(part.enumModel));
        }
        return readCall(part);
    }

    static CodeBlock mapMissingDefault(FieldModel part) {
        if (part.kind == FieldKind.MESSAGE) {
            return CodeBlock.of("$L.INSTANCE.readFrom(new $T(new byte[0]))", part.codecName, PROTO_READER);
        }
        if (part.kind == FieldKind.ENUM) {
            return CodeBlock.of("$L(0)", GenNames.enumFrom(part.enumModel));
        }
        if (part.byteArray) {
            return CodeBlock.of("new byte[0]");
        }
        return switch (part.protoType) {
            case BOOL -> CodeBlock.of("false");
            case STRING -> CodeBlock.of("$S", "");
            case FLOAT -> CodeBlock.of("0F");
            case DOUBLE -> CodeBlock.of("0D");
            case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> CodeBlock.of("0L");
            default -> CodeBlock.of("0");
        };
    }

    static CodeBlock collectionEnsure(String container, FieldModel field) {
        CodeBlock ctor = implConstructorRef(field);
        if (field.implTypeName != null && field.implTypeName.contains("Set")) {
            return CodeBlock.of("$T.ensureMutableSet($L, $L)", PROTO_LISTS, container, ctor);
        }
        return CodeBlock.of("$T.ensureMutableList($L, $L)", PROTO_LISTS, container, ctor);
    }

    static CodeBlock mapEnsure(String container, FieldModel field) {
        return CodeBlock.of("$T.ensureMutableMap($L, $L)", PROTO_LISTS, container, implConstructorRef(field));
    }

    static String arrayBuilderType(FieldModel field) {
        PrimitiveListSpec spec = primitiveListSpec(field);
        if (spec != null) {
            return spec.listType().canonicalName();
        }
        return "java.util.ArrayList<" + boxed(field.element) + ">";
    }

    static CodeBlock toArray(FieldModel field, String listVar) {
        PrimitiveListSpec spec = primitiveListSpec(field);
        if (spec != null) {
            return CodeBlock.of("$L.$L()", listVar, spec.toArray());
        }
        return CodeBlock.of("$L.toArray(new $L[0])", listVar, field.arrayComponentType);
    }

    static CodeBlock primitiveAdd(FieldModel field, String list, CodeBlock value) {
        PrimitiveListSpec spec = primitiveListSpec(field);
        if (spec == null) {
            return null;
        }
        return CodeBlock.of("$T.$L($L, $L)", PROTO_LISTS, spec.add(), list, value);
    }

    static CodeBlock packedEnsure(FieldModel field, String list) {
        PrimitiveListSpec spec = primitiveListSpec(field);
        if (spec == null) {
            return null;
        }
        return CodeBlock.of("$T.$L($L, reader.remaining())", PROTO_LISTS, spec.ensure(), list);
    }

    private static CodeBlock implConstructorRef(FieldModel field) {
        String impl = field.implTypeName;
        if (impl.endsWith("<>")) {
            impl = impl.substring(0, impl.length() - 2);
        }
        return CodeBlock.of("$L::new", impl);
    }
}
