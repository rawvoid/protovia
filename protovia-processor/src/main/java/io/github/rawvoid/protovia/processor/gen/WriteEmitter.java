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
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import io.github.rawvoid.protovia.processor.model.*;
import io.github.rawvoid.protovia.wire.WireType;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;

import static io.github.rawvoid.protovia.processor.gen.GenTypes.*;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.*;
import static io.github.rawvoid.protovia.processor.gen.WireTypes.*;
import static io.github.rawvoid.protovia.processor.model.Names.*;

/**
 * Emits {@code writeTo} and related field-write logic.
 *
 * @author Rawvoid
 */
final class WriteEmitter {

    private WriteEmitter() {
    }

    static List<MethodSpec> writeToMethods(MessageModel model, TypeName msgType) {
        if (!shouldSplit(model)) {
            return List.of(writeTo(model, msgType));
        }
        List<MethodSpec> methods = new ArrayList<>();
        List<List<FieldModel>> chunks = chunkFields(model.fields);
        CodeBlock.Builder body = CodeBlock.builder();
        for (int i = 0; i < chunks.size(); i++) {
            body.addStatement("_writeTo_$L(writer, value)", i);
        }
        if (model.unknown != null) {
            body.addStatement("_writeUnknown(writer, value)");
        }
        methods.add(MethodSpec.methodBuilder("writeTo")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.VOID)
            .addParameter(PROTO_WRITER, "writer")
            .addParameter(msgType, "value")
            .addCode(body.build())
            .build());
        for (int i = 0; i < chunks.size(); i++) {
            methods.add(writeToChunk(i, chunks.get(i), msgType));
        }
        if (model.unknown != null) {
            methods.add(writeUnknownMethod(model, msgType));
        }
        return methods;
    }

    private static MethodSpec writeTo(MessageModel model, TypeName msgType) {
        CodeBlock.Builder body = CodeBlock.builder();
        for (FieldModel field : model.fields) {
            writeField(body, field);
        }
        emitUnknown(body, model, false);
        return MethodSpec.methodBuilder("writeTo")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.VOID)
            .addParameter(PROTO_WRITER, "writer")
            .addParameter(msgType, "value")
            .addCode(body.build())
            .build();
    }

    private static MethodSpec writeToChunk(int index, List<FieldModel> fields, TypeName msgType) {
        CodeBlock.Builder body = CodeBlock.builder();
        for (FieldModel field : fields) {
            writeField(body, field);
        }
        return MethodSpec.methodBuilder("_writeTo_" + index)
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .returns(TypeName.VOID)
            .addParameter(PROTO_WRITER, "writer")
            .addParameter(msgType, "value")
            .addCode(body.build())
            .build();
    }

    private static MethodSpec writeUnknownMethod(MessageModel model, TypeName msgType) {
        CodeBlock.Builder body = CodeBlock.builder();
        String uLocal = model.unknown.localName();
        body.addStatement("$T $L = $L", UNKNOWN_FIELDS, uLocal, model.unknown.readExpr());
        int maxExcluded = countUnknownEnumExclusions(model);
        if (maxExcluded == 0) {
            body.beginControlFlow("if ($L != null)", uLocal);
            body.addStatement("$L.writeTo(writer)", uLocal);
            body.endControlFlow();
        } else {
            body.beginControlFlow("if ($L != null && !$L.isEmpty())", uLocal, uLocal);
            body.addStatement("int[] _excl = new int[$L]", maxExcluded);
            body.addStatement("int _exclN = _collectExcludedEnums(value, _excl)");
            body.addStatement("$L.writeToExcluding(writer, _excl, _exclN)", uLocal);
            body.endControlFlow();
        }
        return MethodSpec.methodBuilder("_writeUnknown")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .returns(TypeName.VOID)
            .addParameter(PROTO_WRITER, "writer")
            .addParameter(msgType, "value")
            .addCode(body.build())
            .build();
    }

    private static void writeField(CodeBlock.Builder b, FieldModel field) {
        loadField(b, field);
        String tag = Names.tagConstant(field.number);
        int tagValue = WireType.tag(field.number, unpackedWire(field));
        switch (field.kind) {
            case SCALAR -> {
                String valueExpr = field.javaOptional ? field.localName + ".get()" : field.localName;
                b.beginControlFlow("if ($L)", presentCondition(field, field.localName, field.optional, field.javaOptional));
                if (field.adapterType != null) {
                    assignToWire(b, field, valueExpr);
                    writeTag(b, tagValue, tag);
                    b.addStatement("$L", writeNoTag("writer", field, wireLocal(field)));
                } else {
                    writeTag(b, tagValue, tag);
                    b.addStatement("$L", writeNoTag("writer", field, valueExpr));
                }
                b.endControlFlow();
            }
            case ENUM -> writeEnum(b, field, tagValue, tag);
            case MESSAGE -> writeMessage(b, field, tagValue, tag);
            case REPEATED -> writeRepeated(b, field, tagValue);
            case MAP -> writeMap(b, field);
            case ONEOF -> writeOneof(b, field);
        }
    }

    private static void writeMessage(CodeBlock.Builder b, FieldModel field, int tagValue, String tag) {
        String sz = "_sz_" + field.number;
        if (field.javaOptional) {
            String msgVar = "_msg_" + field.number;
            b.beginControlFlow("if ($L != null && $L.isPresent())", field.localName, field.localName);
            b.addStatement("$T $L = $L.get()", optionalValueType(field), msgVar, field.localName);
            writeTag(b, tagValue, tag);
            writeCachedMessage(b, field, msgVar, sz);
            b.endControlFlow();
        } else {
            b.beginControlFlow("if ($L != null)", field.localName);
            writeTag(b, tagValue, tag);
            writeCachedMessage(b, field, field.localName, sz);
            b.endControlFlow();
        }
    }

    private static void writeEnum(CodeBlock.Builder b, FieldModel field, int tagValue, String tag) {
        String helper = enumNumberOf(field.enumModel);
        if (field.javaOptional) {
            String ev = "_ev_" + field.number;
            b.beginControlFlow("if ($L != null && $L.isPresent())", field.localName, field.localName);
            b.addStatement("$T $L = $L.get()", enumType(field.enumModel), ev, field.localName);
            if (field.enumModel.unrecognized != null) {
                b.beginControlFlow("if ($L != $L)", ev, enumConstant(field.enumModel, field.enumModel.unrecognized));
                writeTag(b, tagValue, tag);
                b.addStatement("writer.writeInt32NoTag($L($L))", helper, ev);
                b.endControlFlow();
            } else {
                writeTag(b, tagValue, tag);
                b.addStatement("writer.writeInt32NoTag($L($L))", helper, ev);
            }
            b.endControlFlow();
            return;
        }
        if (field.optional) {
            b.beginControlFlow("if ($L)", enumPresent(field, field.localName));
            writeTag(b, tagValue, tag);
            b.addStatement("writer.writeInt32NoTag($L($L))", helper, field.localName);
            b.endControlFlow();
        } else {
            String numVar = "_num_" + field.number;
            b.beginControlFlow("if ($L)", enumPresent(field, field.localName));
            b.addStatement("int $L = $L($L)", numVar, helper, field.localName);
            b.beginControlFlow("if ($L != 0)", numVar);
            writeTag(b, tagValue, tag);
            b.addStatement("writer.writeInt32NoTag($L)", numVar);
            b.endControlFlow();
            b.endControlFlow();
        }
    }

    private static void writeRepeated(CodeBlock.Builder b, FieldModel field, int tagValue) {
        b.beginControlFlow("if ($L)", presentRepeated(field));
        String tag = Names.tagConstant(field.number);
        if (field.packed && field.packable()) {
            String packed = "_packed_" + field.number;
            b.addStatement("int $L = writer.hasCachedSize() ? writer.takeSize() : $L($L)",
                packed, packedSizeOf(field), field.localName);
            writeTag(b, WireType.tag(field.number, WireType.LEN), tag + "_PACKED");
            b.addStatement("writer.writeUInt32NoTag($L)", packed);
            packedElements(b, field, true);
        } else {
            b.beginControlFlow("for ($T item : $L)", javaType(field.element), field.localName);
            nullElementCheck(b, field.element, "item", field.name);
            if (field.element.kind == FieldKind.ENUM) {
                writeTag(b, tagValue, tag);
                b.addStatement("writer.writeInt32NoTag($L(item))", enumNumberOf(field.element.enumModel));
            } else if (field.element.kind == FieldKind.MESSAGE) {
                writeTag(b, tagValue, tag);
                writeCachedMessage(b, field.element, "item", "itemSize");
            } else {
                String value = WireCodegen.adaptedValue(b, field.element, "item", "itemWire");
                writeTag(b, tagValue, tag);
                b.addStatement("$L", writeNoTag("writer", field.element, value));
            }
            b.endControlFlow();
        }
        b.endControlFlow();
    }

    private static void writeOneof(CodeBlock.Builder b, FieldModel field) {
        oneofCases(b, field, WriteEmitter::oneofCaseWrite);
    }

    private static void oneofCaseWrite(CodeBlock.Builder b, OneofCaseModel c) {
        String value = c.accessor == null ? "_c" : "_c." + c.accessor;
        int tagValue = WireType.tag(c.number, oneofWire(c));
        if (c.empty()) {
            writeTag(b, tagValue, c.tagConstant);
            b.addStatement("writer.writeRawByte(0)");
        } else if (c.selfMessage) {
            writeTag(b, tagValue, c.tagConstant);
            writeCachedMessage(b, c.payload, "_c", "_sz_" + c.number);
        } else if (c.payload.kind == FieldKind.MESSAGE) {
            b.addStatement("$T _p = $L", javaType(c.payload), value);
            b.beginControlFlow("if (_p != null)");
            writeTag(b, tagValue, c.tagConstant);
            writeCachedMessage(b, c.payload, "_p", "_sz_" + c.number);
            b.endControlFlow();
        } else if (c.payload.kind == FieldKind.ENUM) {
            b.beginControlFlow("if ($L)", enumPresent(c.payload, value));
            writeTag(b, tagValue, c.tagConstant);
            b.addStatement("writer.writeInt32NoTag($L($L))", enumNumberOf(c.payload.enumModel), value);
            b.endControlFlow();
        } else {
            String scalarValue = oneofScalarPayload(b, c, value);
            if (c.payload.adapterType != null) {
                String w = oneofWireLocal(c);
                assignToWire(b, c.payload, scalarValue, w);
                writeTag(b, tagValue, c.tagConstant);
                b.addStatement("$L", writeNoTag("writer", c.payload, w));
            } else {
                writeTag(b, tagValue, c.tagConstant);
                b.addStatement("$L", writeNoTag("writer", c.payload, scalarValue));
            }
        }
    }

    private static void writeMap(CodeBlock.Builder b, FieldModel field) {
        b.beginControlFlow("if ($L != null && !$L.isEmpty())", field.localName, field.localName);
        beginMapLoop(b, field, false);
        b.addStatement("$L(writer, $L, $L)", mapEntryWrite(field), mapEntryKeyExpr(field), mapEntryValueExpr(field));
        b.endControlFlow();
        b.endControlFlow();
    }
}
