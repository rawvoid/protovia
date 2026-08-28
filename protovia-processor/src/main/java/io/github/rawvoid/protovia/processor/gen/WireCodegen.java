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

import com.palantir.javapoet.*;
import io.github.rawvoid.protovia.ProtoType;
import io.github.rawvoid.protovia.processor.model.FieldKind;
import io.github.rawvoid.protovia.processor.model.FieldModel;
import io.github.rawvoid.protovia.processor.model.OneofCaseModel;
import io.github.rawvoid.protovia.wire.WireType;

import java.util.function.BiConsumer;

import static io.github.rawvoid.protovia.processor.gen.GenTypes.*;
import static io.github.rawvoid.protovia.processor.gen.WireTypes.*;
import static io.github.rawvoid.protovia.processor.model.Names.enumFrom;
import static io.github.rawvoid.protovia.processor.model.Names.enumNumberOf;

/**
 * Shared {@link CodeBlock} fragments and statement patterns for size / write / read.
 *
 * @author Rawvoid
 */
final class WireCodegen {

    private WireCodegen() {
    }

    static void loadField(CodeBlock.Builder b, FieldModel field) {
        b.addStatement("$T $L = $L", javaType(field), field.localName, field.readExpr);
    }

    static TypeName wireLocalType(FieldModel field) {
        return switch (field.protoType) {
            case INT32, UINT32, SINT32, FIXED32, SFIXED32 -> TypeName.INT;
            case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> TypeName.LONG;
            case BOOL -> TypeName.BOOLEAN;
            case FLOAT -> TypeName.FLOAT;
            case DOUBLE -> TypeName.DOUBLE;
            case STRING -> ClassName.get(String.class);
            case BYTES -> ArrayTypeName.of(TypeName.BYTE);
            default -> field.wireJavaType != null ? TypeName.get(field.wireJavaType) : TypeName.INT;
        };
    }

    static String wireLocal(FieldModel field) {
        return field.localName + "Wire";
    }

    static void assignToWire(CodeBlock.Builder b, FieldModel field, String javaValue) {
        assignToWire(b, field, javaValue, wireLocal(field));
    }

    static void assignToWire(CodeBlock.Builder b, FieldModel field, String javaValue, String wireVar) {
        b.addStatement("$T $L = $L.toWire($L)",
            wireLocalType(field), wireVar, adapterInstance(field), javaValue);
    }

    static String adaptedValue(CodeBlock.Builder b, FieldModel field, String javaValue, String wireName) {
        if (field.adapterType == null) {
            return javaValue;
        }
        assignToWire(b, field, javaValue, wireName);
        return wireName;
    }

    /**
     * Iterates map entries in write order: sorted by wire key when
     * {@link FieldModel#deterministic} is set, otherwise {@code entrySet()}.
     * Size and write must use the same order so {@code SizeCache} stays aligned.
     */
    static void forEachMapEntry(CodeBlock.Builder b, FieldModel field) {
        if (field.deterministic) {
            b.beginControlFlow("for ($T.Entry<$T, $T> e : $T.sortedEntries($L, $L))",
                MAP, boxedType(field.mapKey), boxedType(field.mapValue),
                PROTO_MAPS, field.localName, keyComparator(field));
        } else {
            b.beginControlFlow("for ($T.Entry<$T, $T> e : $L.entrySet())",
                MAP, boxedType(field.mapKey), boxedType(field.mapValue), field.localName);
        }
    }

    static CodeBlock keyComparator(FieldModel field) {
        CodeBlock wireOrder = wireKeyOrder(field.mapKey.protoType);
        if (field.mapKey.adapterType == null) {
            return wireOrder;
        }
        return CodeBlock.of("$T.comparing(k -> $L.toWire(k), $L)",
            COMPARATOR, adapterInstance(field.mapKey), wireOrder);
    }

    static CodeBlock wireKeyOrder(ProtoType protoType) {
        return switch (protoType) {
            case UINT32, FIXED32 -> CodeBlock.of("$T::compareUnsigned", Integer.class);
            case UINT64, FIXED64 -> CodeBlock.of("$T::compareUnsigned", Long.class);
            default -> CodeBlock.of("$T.naturalOrder()", COMPARATOR);
        };
    }

    static String oneofWireLocal(OneofCaseModel c) {
        String accessor = c.accessor;
        if (accessor != null && accessor.endsWith("()")) {
            return accessor.substring(0, accessor.length() - 2) + "Wire";
        }
        return wireLocal(c.payload);
    }

    static CodeBlock fromWire(FieldModel field, CodeBlock read) {
        return CodeBlock.of("$L.fromWire($L)", adapterInstance(field), read);
    }

    static void writeTag(CodeBlock.Builder b, Object tag) {
        b.addStatement("writer.writeUInt32NoTag($L)", tag);
    }

    static void writeCachedMessage(CodeBlock.Builder b, FieldModel field, String value, String sizeLocal) {
        CodeBlock codec = codecInstance(field);
        b.addStatement("int $L = writer.hasCachedSize() ? writer.takeSize() : $L.computeSize($L)",
            sizeLocal, codec, value);
        b.addStatement("writer.writeUInt32NoTag($L)", sizeLocal);
        b.addStatement("$L.writeTo(writer, $L)", codec, value);
    }

    static void nullElementCheck(CodeBlock.Builder b, FieldModel element, String var, String fieldName) {
        if (!element.primitive) {
            b.beginControlFlow("if ($L == null)", var);
            b.addStatement("throw new $T($S)", PROTO_EXCEPTION, "null element in field " + fieldName);
            b.endControlFlow();
        }
    }

    static void oneofCases(
        CodeBlock.Builder b,
        FieldModel field,
        BiConsumer<CodeBlock.Builder, OneofCaseModel> caseBody) {
        b.beginControlFlow("if ($L != null)", field.localName);
        boolean first = true;
        for (OneofCaseModel c : field.oneofCases) {
            if (first) {
                b.beginControlFlow("if ($L instanceof $T _c)", field.localName, oneofCaseType(c));
            } else {
                b.nextControlFlow("else if ($L instanceof $T _c)", field.localName, oneofCaseType(c));
            }
            first = false;
            caseBody.accept(b, c);
        }
        if (!first) {
            b.nextControlFlow("else");
            b.addStatement("throw new $T($S + $L.getClass().getName())",
                PROTO_EXCEPTION,
                "oneof '" + field.name + "' value has unexpected type ",
                field.localName);
            b.endControlFlow();
        }
        b.endControlFlow();
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
            return CodeBlock.of("reader.readMessage($L)", codecInstance(part));
        }
        if (part.kind == FieldKind.ENUM) {
            return CodeBlock.of("$L(reader.readEnum())", enumFrom(part.enumModel));
        }
        return readCall(part);
    }

    static CodeBlock mapMissingDefault(FieldModel part) {
        if (part.kind == FieldKind.MESSAGE) {
            return CodeBlock.of("$L.readFrom(new $T(new byte[0]))", codecInstance(part), PROTO_READER);
        }
        if (part.kind == FieldKind.ENUM) {
            return CodeBlock.of("$L(0)", enumFrom(part.enumModel));
        }
        if (part.adapterType != null) {
            return switch (part.protoType) {
                case BOOL -> CodeBlock.of("false");
                case STRING -> CodeBlock.of("$S", "");
                case BYTES -> CodeBlock.of("new byte[0]");
                case FLOAT -> CodeBlock.of("0F");
                case DOUBLE -> CodeBlock.of("0D");
                case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> CodeBlock.of("0L");
                default -> CodeBlock.of("0");
            };
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
        if (field.implType != null && field.implType.getQualifiedName().toString().contains("Set")) {
            return CodeBlock.of("$T.ensureMutableSet($L, $L)", PROTO_LISTS, container, ctor);
        }
        return CodeBlock.of("$T.ensureMutableList($L, $L)", PROTO_LISTS, container, ctor);
    }

    static CodeBlock mapEnsure(String container, FieldModel field) {
        return CodeBlock.of("$T.ensureMutableMap($L, $L)", PROTO_LISTS, container, implConstructorRef(field));
    }

    static TypeName arrayBuilderType(FieldModel field) {
        PrimitiveListSpec spec = PrimitiveListSpec.of(field);
        if (spec != null) {
            return spec.listType();
        }
        return ParameterizedTypeName.get(ARRAY_LIST, boxedType(field.element));
    }

    static CodeBlock toArray(FieldModel field, String listVar) {
        PrimitiveListSpec spec = PrimitiveListSpec.of(field);
        if (spec != null) {
            return CodeBlock.of("$L.$L()", listVar, spec.toArray());
        }
        return CodeBlock.of("$L.toArray(new $T[0])", listVar, javaType(field.element));
    }

    static CodeBlock primitiveAdd(FieldModel field, String list, CodeBlock value) {
        PrimitiveListSpec spec = PrimitiveListSpec.of(field);
        if (spec == null) {
            return null;
        }
        return CodeBlock.of("$T.$L($L, $L)", PROTO_LISTS, spec.add(), list, value);
    }

    static CodeBlock packedEnsure(FieldModel field, String list) {
        PrimitiveListSpec spec = PrimitiveListSpec.of(field);
        if (spec == null) {
            return null;
        }
        return CodeBlock.of("$T.$L($L, reader.remaining())", PROTO_LISTS, spec.ensure(), list);
    }

    static void packedElements(CodeBlock.Builder b, FieldModel field, boolean write) {
        String list = write ? field.localName : "values";
        PrimitiveListSpec spec = PrimitiveListSpec.of(field);
        int width = packedFixedWidth(field.element);
        if (spec != null && !field.array && (write || width == 0)) {
            String prim = field.localName + "Prim";
            b.beginControlFlow("if ($L instanceof $T $L)", list, spec.listType(), prim);
            primitivePackedLoop(b, field, spec, prim, write);
            b.nextControlFlow("else");
            boxedPackedLoop(b, field, list, write);
            b.endControlFlow();
            return;
        }
        if (spec != null && !field.array && !write && width > 0) {
            b.beginControlFlow("if (!($L instanceof $T))", list, spec.listType());
            boxedPackedLoop(b, field, list, false);
            b.endControlFlow();
            return;
        }
        boxedPackedLoop(b, field, list, write);
    }

    private static void primitivePackedLoop(
        CodeBlock.Builder b, FieldModel field, PrimitiveListSpec spec, String prim, boolean write) {
        b.beginControlFlow("for (int _i = 0, _n = $L.size(); _i < _n; _i++)", prim);
        String access = prim + "." + spec.get() + "(_i)";
        if (write) {
            b.addStatement("$L", writeNoTag("writer", field.element, access));
        } else {
            b.addStatement("packed += $L", sizeNoTag(field.element, access));
        }
        b.endControlFlow();
    }

    private static void boxedPackedLoop(CodeBlock.Builder b, FieldModel field, String list, boolean write) {
        b.beginControlFlow("for ($T item : $L)", javaType(field.element), list);
        nullElementCheck(b, field.element, "item", field.name);
        String value = adaptedValue(b, field.element, "item", "itemWire");
        if (write) {
            if (field.element.kind == FieldKind.ENUM) {
                b.addStatement("writer.writeInt32NoTag($L(item))", enumNumberOf(field.element.enumModel));
            } else {
                b.addStatement("$L", writeNoTag("writer", field.element, value));
            }
        } else if (packedFixedWidth(field.element) == 0) {
            if (field.element.kind == FieldKind.ENUM) {
                b.addStatement("packed += $T.enumValue($L(item))",
                    CODED_SIZE, enumNumberOf(field.element.enumModel));
            } else {
                b.addStatement("packed += $L", sizeNoTag(field.element, value));
            }
        }
        b.endControlFlow();
    }

    static void mapEntrySizeAdd(CodeBlock.Builder b, FieldModel part, String var, int number, String sizeVar) {
        if (part.kind == FieldKind.MESSAGE) {
            b.addStatement("int $LSlot = cache.reserve()", var);
            b.addStatement("int $LSize = $L.computeSize($L, cache)", var, codecInstance(part), var);
            b.addStatement("cache.set($LSlot, $LSize)", var, var);
            b.addStatement("$L += $T.message($L, $LSize)", sizeVar, CODED_SIZE, number, var);
            return;
        }
        if (part.kind == FieldKind.ENUM) {
            b.addStatement("int $LN = $L($L)", var, enumNumberOf(part.enumModel), var);
            b.beginControlFlow("if ($LN != 0)", var);
            b.addStatement("$L += $T.enumValue($L, $LN)", sizeVar, CODED_SIZE, number, var);
            b.endControlFlow();
            return;
        }
        String sizeValue = var;
        if (part.adapterType != null) {
            sizeValue = var + "Wire";
            assignToWire(b, part, var, sizeValue);
            b.beginControlFlow("if ($L)", wireDefaultPresent(part.protoType, sizeValue));
        } else {
            b.beginControlFlow("if ($L)", mapDefaultSkip(part, var));
        }
        b.addStatement("$L += $L", sizeVar, sizeCall(part, number, sizeValue));
        b.endControlFlow();
    }

    static void mapEntryPartWrite(CodeBlock.Builder b, FieldModel part, String var, int number) {
        int tag = WireType.tag(number, unpackedWire(part));
        if (part.kind == FieldKind.MESSAGE) {
            writeTag(b, tag);
            writeCachedMessage(b, part, var, var + "Size");
            return;
        }
        if (part.kind == FieldKind.ENUM) {
            b.addStatement("int $LN = $L($L)", var, enumNumberOf(part.enumModel), var);
            b.beginControlFlow("if ($LN != 0)", var);
            writeTag(b, tag);
            b.addStatement("writer.writeInt32NoTag($LN)", var);
            b.endControlFlow();
            return;
        }
        String writeValue = var;
        if (part.adapterType != null) {
            writeValue = var + "Wire";
            assignToWire(b, part, var, writeValue);
            b.beginControlFlow("if ($L)", wireDefaultPresent(part.protoType, writeValue));
        } else {
            b.beginControlFlow("if ($L)", mapDefaultSkip(part, var));
        }
        writeTag(b, tag);
        b.addStatement("$L", writeNoTag("writer", part, writeValue));
        b.endControlFlow();
    }

    static void mapPartAssign(CodeBlock.Builder b, FieldModel part, String var) {
        if (part.kind == FieldKind.ENUM) {
            b.addStatement("int $LN = reader.readEnum()", var);
            b.addStatement("$T $LE = $L($LN)", enumType(part.enumModel), var, enumFrom(part.enumModel), var);
            if (part.enumModel.unrecognized != null) {
                b.beginControlFlow("if ($LE != null && $LE != $L)",
                    var, var, enumConstant(part.enumModel, part.enumModel.unrecognized));
            } else {
                b.beginControlFlow("if ($LE != null)", var);
            }
            b.addStatement("$L = $LE", var, var);
            b.endControlFlow();
            return;
        }
        b.addStatement("$L = $L", var, mapReadExpr(part));
    }
}
