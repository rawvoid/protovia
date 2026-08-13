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
import io.github.rawvoid.protovia.wire.WireType;

import javax.lang.model.element.Modifier;

import static io.github.rawvoid.protovia.processor.gen.GenNames.enumNumberOf;
import static io.github.rawvoid.protovia.processor.gen.GenNames.mapEntryWrite;
import static io.github.rawvoid.protovia.processor.gen.GenNames.packedSizeOf;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.CODED_SIZE;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.MAP;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.PROTO_WRITER;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.UNKNOWN_FIELDS;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.sourceType;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.sizeNoTag;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.writeNoTag;
import static io.github.rawvoid.protovia.processor.gen.WireTypes.boxedType;
import static io.github.rawvoid.protovia.processor.gen.WireTypes.enumPresent;
import static io.github.rawvoid.protovia.processor.gen.WireTypes.mapDefaultSkip;
import static io.github.rawvoid.protovia.processor.gen.WireTypes.packedFixedWidth;
import static io.github.rawvoid.protovia.processor.gen.WireTypes.presentCondition;
import static io.github.rawvoid.protovia.processor.gen.WireTypes.presentRepeated;
import static io.github.rawvoid.protovia.processor.gen.WireTypes.primitiveListSpec;
import static io.github.rawvoid.protovia.processor.gen.WireTypes.unpackedWire;

/**
 * Emits {@code writeTo} and related field-write logic, including packed loops.
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
        Emit.loadField(b, field);
        String tag = Names.tagConstant(field.number);
        switch (field.kind) {
            case SCALAR -> {
                String valueExpr = field.javaOptional ? field.localName + ".get()" : field.localName;
                b.beginControlFlow("if ($L)", presentCondition(field, field.localName, field.optional, field.javaOptional));
                Emit.writeTag(b, tag);
                b.addStatement("$L", writeNoTag("writer", field, valueExpr));
                b.endControlFlow();
            }
            case ENUM -> writeEnum(b, field, tag);
            case MESSAGE -> {
                b.beginControlFlow("if ($L != null)", field.localName);
                Emit.writeTag(b, tag);
                Emit.writeCachedMessage(b, field.codecName + ".INSTANCE", field.localName, field.localName + "Size");
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
            Emit.writeTag(b, tag);
            b.addStatement("writer.writeInt32NoTag($L($L))", helper, field.localName);
            b.endControlFlow();
        } else {
            b.beginControlFlow("if ($L)", enumPresent(field, field.localName));
            b.addStatement("int $LNumber = $L($L)", field.localName, helper, field.localName);
            b.beginControlFlow("if ($LNumber != 0)", field.localName);
            Emit.writeTag(b, tag);
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
            b.beginControlFlow("for ($T item : $L)", sourceType(field.element.javaTypeName), field.localName);
            Emit.nullElementCheck(b, field.element, "item", field.name);
            Emit.writeTag(b, tag);
            if (field.element.kind == FieldKind.ENUM) {
                b.addStatement("writer.writeInt32NoTag($L(item))", enumNumberOf(field.element.enumModel));
            } else if (field.element.kind == FieldKind.MESSAGE) {
                Emit.writeCachedMessage(b, field.element.codecName + ".INSTANCE", "item", "itemSize");
            } else {
                b.addStatement("$L", writeNoTag("writer", field.element, "item"));
            }
            b.endControlFlow();
        }
        b.endControlFlow();
    }

    static void packedElements(CodeBlock.Builder b, FieldModel field, boolean write) {
        String list = write ? field.localName : "values";
        PrimitiveListSpec spec = primitiveListSpec(field);
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
        b.beginControlFlow("for ($T item : $L)", sourceType(field.element.javaTypeName), list);
        Emit.nullElementCheck(b, field.element, "item", field.name);
        if (write) {
            if (field.element.kind == FieldKind.ENUM) {
                b.addStatement("writer.writeInt32NoTag($L(item))", enumNumberOf(field.element.enumModel));
            } else {
                b.addStatement("$L", writeNoTag("writer", field.element, "item"));
            }
        } else if (packedFixedWidth(field.element) == 0) {
            if (field.element.kind == FieldKind.ENUM) {
                b.addStatement("packed += $T.enumValue($L(item))",
                    CODED_SIZE, enumNumberOf(field.element.enumModel));
            } else {
                b.addStatement("packed += $L", sizeNoTag(field.element, "item"));
            }
        }
        b.endControlFlow();
    }

    private static void writeOneof(CodeBlock.Builder b, FieldModel field) {
        Emit.oneofCases(b, field, WriteEmitter::oneofCaseWrite);
    }

    private static void oneofCaseWrite(CodeBlock.Builder b, OneofCaseModel c) {
        if (c.empty()) {
            Emit.writeTag(b, c.tagConstant);
            b.addStatement("writer.writeUInt32NoTag(0)");
        } else if (c.selfMessage) {
            Emit.writeTag(b, c.tagConstant);
            Emit.writeCachedMessage(b, c.payload.codecName + ".INSTANCE", "_c", c.tagConstant + "_sz");
        } else if (c.payload.kind == FieldKind.MESSAGE) {
            b.addStatement("$T _p = _c.$L", sourceType(c.payload.javaTypeName), c.accessor);
            b.beginControlFlow("if (_p != null)");
            Emit.writeTag(b, c.tagConstant);
            Emit.writeCachedMessage(b, c.payload.codecName + ".INSTANCE", "_p", c.tagConstant + "_sz");
            b.endControlFlow();
        } else if (c.payload.kind == FieldKind.ENUM) {
            Emit.writeTag(b, c.tagConstant);
            b.addStatement("writer.writeInt32NoTag($L(_c.$L))", enumNumberOf(c.payload.enumModel), c.accessor);
        } else {
            Emit.writeTag(b, c.tagConstant);
            b.addStatement("$L", writeNoTag("writer", c.payload, "_c." + c.accessor));
        }
    }

    private static void writeMap(CodeBlock.Builder b, FieldModel field) {
        b.beginControlFlow("if ($L != null && !$L.isEmpty())", field.localName, field.localName);
        b.beginControlFlow("for ($T.Entry<$T, $T> e : $L.entrySet())",
            MAP, WireTypes.boxedType(field.mapKey), WireTypes.boxedType(field.mapValue), field.localName);
        b.addStatement("$L(writer, e.getKey(), e.getValue())", mapEntryWrite(field));
        b.endControlFlow();
        b.endControlFlow();
    }

    static void mapEntryPartWrite(CodeBlock.Builder b, FieldModel part, String var, int number) {
        int tag = WireType.tag(number, unpackedWire(part));
        if (part.kind == FieldKind.MESSAGE) {
            Emit.writeTag(b, tag);
            Emit.writeCachedMessage(b, part.codecName + ".INSTANCE", var, var + "Size");
            return;
        }
        if (part.kind == FieldKind.ENUM) {
            b.addStatement("int $LN = $L($L)", var, enumNumberOf(part.enumModel), var);
            b.beginControlFlow("if ($LN != 0)", var);
            Emit.writeTag(b, tag);
            b.addStatement("writer.writeInt32NoTag($LN)", var);
            b.endControlFlow();
            return;
        }
        b.beginControlFlow("if ($L)", mapDefaultSkip(part, var));
        Emit.writeTag(b, tag);
        b.addStatement("$L", writeNoTag("writer", part, var));
        b.endControlFlow();
    }
}
