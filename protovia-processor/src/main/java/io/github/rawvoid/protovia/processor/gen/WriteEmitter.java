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
import io.github.rawvoid.protovia.processor.model.FieldKind;
import io.github.rawvoid.protovia.processor.model.FieldModel;
import io.github.rawvoid.protovia.processor.model.MessageModel;
import io.github.rawvoid.protovia.processor.model.Names;
import io.github.rawvoid.protovia.processor.model.OneofCaseModel;

import javax.lang.model.element.Modifier;

import static io.github.rawvoid.protovia.processor.model.Names.enumNumberOf;
import static io.github.rawvoid.protovia.processor.model.Names.mapEntryWrite;
import static io.github.rawvoid.protovia.processor.model.Names.packedSizeOf;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.MAP;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.PROTO_WRITER;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.UNKNOWN_FIELDS;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.boxedType;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.javaType;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.assignToWire;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.loadField;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.nullElementCheck;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.oneofCases;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.oneofWireLocal;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.packedElements;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.writeCachedMessage;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.wireLocal;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.writeNoTag;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.writeTag;
import static io.github.rawvoid.protovia.processor.gen.WireTypes.enumPresent;
import static io.github.rawvoid.protovia.processor.gen.WireTypes.presentCondition;
import static io.github.rawvoid.protovia.processor.gen.WireTypes.presentRepeated;

/**
 * Emits {@code writeTo} and related field-write logic.
 *
 * @author Rawvoid
 */
final class WriteEmitter {

    private WriteEmitter() {
    }

    static MethodSpec writeTo(MessageModel model, TypeName msgType) {
        CodeBlock.Builder body = CodeBlock.builder();
        for (FieldModel field : model.fields) {
            writeField(body, field);
        }
        if (model.unknown != null) {
            body.addStatement("$T $L = $L", UNKNOWN_FIELDS, model.unknown.localName(), model.unknown.readExpr());
            body.beginControlFlow("if ($L != null)", model.unknown.localName());
            body.addStatement("$L.writeTo(writer)", model.unknown.localName());
            body.endControlFlow();
        }
        return MethodSpec.methodBuilder("writeTo")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.VOID)
            .addParameter(PROTO_WRITER, "writer")
            .addParameter(msgType, "value")
            .addCode(body.build())
            .build();
    }

    private static void writeField(CodeBlock.Builder b, FieldModel field) {
        loadField(b, field);
        String tag = Names.tagConstant(field.number);
        switch (field.kind) {
            case SCALAR -> {
                String valueExpr = field.javaOptional ? field.localName + ".get()" : field.localName;
                b.beginControlFlow("if ($L)", presentCondition(field, field.localName, field.optional, field.javaOptional));
                if (field.adapterType != null) {
                    assignToWire(b, field, valueExpr);
                    writeTag(b, tag);
                    b.addStatement("$L", writeNoTag("writer", field, wireLocal(field)));
                } else {
                    writeTag(b, tag);
                    b.addStatement("$L", writeNoTag("writer", field, valueExpr));
                }
                b.endControlFlow();
            }
            case ENUM -> writeEnum(b, field, tag);
            case MESSAGE -> {
                b.beginControlFlow("if ($L != null)", field.localName);
                writeTag(b, tag);
                writeCachedMessage(b, field, field.localName, field.localName + "Size");
                b.endControlFlow();
            }
            case REPEATED -> writeRepeated(b, field);
            case MAP -> writeMap(b, field);
            case ONEOF -> writeOneof(b, field);
        }
    }

    private static void writeEnum(CodeBlock.Builder b, FieldModel field, String tag) {
        String helper = enumNumberOf(field.enumModel);
        if (field.optional) {
            b.beginControlFlow("if ($L)", enumPresent(field, field.localName));
            writeTag(b, tag);
            b.addStatement("writer.writeInt32NoTag($L($L))", helper, field.localName);
            b.endControlFlow();
        } else {
            b.beginControlFlow("if ($L)", enumPresent(field, field.localName));
            b.addStatement("int $LNumber = $L($L)", field.localName, helper, field.localName);
            b.beginControlFlow("if ($LNumber != 0)", field.localName);
            writeTag(b, tag);
            b.addStatement("writer.writeInt32NoTag($LNumber)", field.localName);
            b.endControlFlow();
            b.endControlFlow();
        }
    }

    private static void writeRepeated(CodeBlock.Builder b, FieldModel field) {
        b.beginControlFlow("if ($L)", presentRepeated(field));
        String tag = Names.tagConstant(field.number);
        if (field.packed && field.packable()) {
            b.addStatement("int $LPacked = writer.hasCachedSize() ? writer.takeSize() : $L($L)",
                field.localName, packedSizeOf(field), field.localName);
            b.addStatement("writer.writeUInt32NoTag($L_PACKED)", tag);
            b.addStatement("writer.writeUInt32NoTag($LPacked)", field.localName);
            packedElements(b, field, true);
        } else {
            b.beginControlFlow("for ($T item : $L)", javaType(field.element), field.localName);
            nullElementCheck(b, field.element, "item", field.name);
            if (field.element.kind == FieldKind.ENUM) {
                writeTag(b, tag);
                b.addStatement("writer.writeInt32NoTag($L(item))", enumNumberOf(field.element.enumModel));
            } else if (field.element.kind == FieldKind.MESSAGE) {
                writeTag(b, tag);
                writeCachedMessage(b, field.element, "item", "itemSize");
            } else {
                String value = WireCodegen.adaptedValue(b, field.element, "item", "itemWire");
                writeTag(b, tag);
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
        if (c.empty()) {
            writeTag(b, c.tagConstant);
            b.addStatement("writer.writeUInt32NoTag(0)");
        } else if (c.selfMessage) {
            writeTag(b, c.tagConstant);
            writeCachedMessage(b, c.payload, "_c", c.tagConstant + "_sz");
        } else if (c.payload.kind == FieldKind.MESSAGE) {
            b.addStatement("$T _p = $L", javaType(c.payload), value);
            b.beginControlFlow("if (_p != null)");
            writeTag(b, c.tagConstant);
            writeCachedMessage(b, c.payload, "_p", c.tagConstant + "_sz");
            b.endControlFlow();
        } else if (c.payload.kind == FieldKind.ENUM) {
            b.beginControlFlow("if ($L)", enumPresent(c.payload, value));
            writeTag(b, c.tagConstant);
            b.addStatement("writer.writeInt32NoTag($L($L))", enumNumberOf(c.payload.enumModel), value);
            b.endControlFlow();
        } else if (c.payload.adapterType != null) {
            String w = oneofWireLocal(c);
            assignToWire(b, c.payload, value, w);
            writeTag(b, c.tagConstant);
            b.addStatement("$L", writeNoTag("writer", c.payload, w));
        } else {
            writeTag(b, c.tagConstant);
            b.addStatement("$L", writeNoTag("writer", c.payload, value));
        }
    }

    private static void writeMap(CodeBlock.Builder b, FieldModel field) {
        b.beginControlFlow("if ($L != null && !$L.isEmpty())", field.localName, field.localName);
        b.beginControlFlow("for ($T.Entry<$T, $T> e : $L.entrySet())",
            MAP, boxedType(field.mapKey), boxedType(field.mapValue), field.localName);
        b.addStatement("$L(writer, e.getKey(), e.getValue())", mapEntryWrite(field));
        b.endControlFlow();
        b.endControlFlow();
    }
}
