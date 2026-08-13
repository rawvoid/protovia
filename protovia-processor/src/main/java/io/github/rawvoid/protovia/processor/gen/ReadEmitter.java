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
import io.github.rawvoid.protovia.processor.model.AccessKind;
import io.github.rawvoid.protovia.processor.model.EnumModel;
import io.github.rawvoid.protovia.processor.model.FieldKind;
import io.github.rawvoid.protovia.processor.model.FieldModel;
import io.github.rawvoid.protovia.processor.model.MessageModel;
import io.github.rawvoid.protovia.processor.model.Names;
import io.github.rawvoid.protovia.processor.model.OneofCaseModel;

import javax.lang.model.element.Modifier;
import java.util.List;
import java.util.stream.Collectors;

import static io.github.rawvoid.protovia.processor.gen.GenNames.enumFrom;
import static io.github.rawvoid.protovia.processor.gen.GenNames.mapEntryRead;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.PROTO_READER;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.UNKNOWN_FIELDS;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.WIRE_TYPE;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.arrayBuilderType;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.collectionEnsure;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.mapEnsure;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.mapReadExpr;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.packedEnsure;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.primitiveAdd;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.readCall;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.toArray;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.wrapOptional;

/**
 * Emits {@code readFrom} / {@code mergeFrom} and related field-read logic.
 *
 * @author Rawvoid
 */
final class ReadEmitter {

    private ReadEmitter() {
    }

    static List<MethodSpec> readMethods(MessageModel model, TypeName msgType) {
        MethodSpec.Builder readFrom = MethodSpec.methodBuilder("readFrom")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(msgType)
            .addParameter(PROTO_READER, "reader");
        if (model.record) {
            readFrom.addStatement("return mergeFrom(reader, null)");
        } else {
            readFrom.addStatement("return mergeFrom(reader, new $L())", model.typeName);
        }
        return List.of(readFrom.build(), mergeFrom(model, msgType));
    }

    private static MethodSpec mergeFrom(MessageModel model, TypeName msgType) {
        CodeBlock.Builder body = CodeBlock.builder();
        if (model.record) {
            readRecord(body, model);
        } else {
            readPojo(body, model);
        }
        return MethodSpec.methodBuilder("mergeFrom")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(msgType)
            .addParameter(PROTO_READER, "reader")
            .addParameter(msgType, "existing")
            .addCode(body.build())
            .build();
    }

    private static void readPojo(CodeBlock.Builder b, MessageModel model) {
        b.addStatement("$L msg = existing != null ? existing : new $L()", model.typeName, model.typeName);
        initArrayBuilders(b, model);
        if (model.unknown != null) {
            unknownInit(b, model);
        }
        readLoop(b, model, false);
        finalizeArrayBuilders(b, model, "msg");
        if (model.unknown != null) {
            unknownStore(b, model);
        }
        b.addStatement("return msg");
    }

    private static void readRecord(CodeBlock.Builder b, MessageModel model) {
        for (MessageModel.RecordComponentModel component : model.recordComponents) {
            recordComponentInit(b, model, component);
        }
        initArrayBuilders(b, model);
        readLoop(b, model, true);
        finalizeArrayBuilders(b, model, null);
        String args = model.recordComponents.stream()
            .map(c -> Names.safeLocal(c.name()))
            .collect(Collectors.joining(", "));
        b.addStatement("return new $L($L)", model.typeName, args);
    }

    private static void initArrayBuilders(CodeBlock.Builder b, MessageModel model) {
        for (FieldModel field : model.fields) {
            if (field.array) {
                b.addStatement("$L $LBuilder = null", arrayBuilderType(field), field.localName);
            }
        }
    }

    private static void finalizeArrayBuilders(CodeBlock.Builder b, MessageModel model, String assignTarget) {
        for (FieldModel field : model.fields) {
            if (!field.array) {
                continue;
            }
            b.beginControlFlow("if ($LBuilder != null)", field.localName);
            if (assignTarget == null) {
                b.addStatement("$L = $L", field.localName, toArray(field, field.localName + "Builder"));
            } else {
                assign(b, field, assignTarget, toArray(field, field.localName + "Builder"));
            }
            b.endControlFlow();
        }
    }

    private static void readLoop(CodeBlock.Builder b, MessageModel model, boolean record) {
        b.addStatement("int tag");
        b.beginControlFlow("while ((tag = reader.readTag()) != 0)");
        b.beginControlFlow("switch (tag)");
        for (FieldModel field : model.fields) {
            readCases(b, field, record, model);
        }
        unknownDefault(b, model);
        b.endControlFlow();
        b.endControlFlow();
    }

    private static void readCases(CodeBlock.Builder b, FieldModel field, boolean record, MessageModel model) {
        String tag = Names.tagConstant(field.number);
        switch (field.kind) {
            case SCALAR -> {
                b.beginControlFlow("case $L ->", tag);
                store(b, field, record, wrapOptional(field, readCall(field)));
                b.endControlFlow();
            }
            case ENUM -> {
                b.beginControlFlow("case $L ->", tag);
                readEnumField(b, field, record, model, tag);
                b.endControlFlow();
            }
            case MESSAGE -> {
                b.beginControlFlow("case $L ->", tag);
                readMessage(b, field, record);
                b.endControlFlow();
            }
            case REPEATED -> readRepeated(b, field, record, tag, model);
            case MAP -> {
                b.beginControlFlow("case $L ->", tag);
                ensureMap(b, field, record);
                b.addStatement("$L(reader, $L)", mapEntryRead(field), mapVar(field, record));
                b.endControlFlow();
            }
            case ONEOF -> readOneof(b, field, record);
        }
    }

    private static void readEnumField(
        CodeBlock.Builder b, FieldModel field, boolean record, MessageModel model, String tag) {
        b.addStatement("int _n = reader.readEnum()");
        b.addStatement("$L _e = $L(_n)", field.enumModel.typeName, enumFrom(field.enumModel));
        if (field.enumModel.unrecognized != null) {
            b.beginControlFlow("if (_e == $L.$L)", field.enumModel.typeName, field.enumModel.unrecognized);
            mergeUnknownVarint(b, model, tag);
            store(b, field, record, wrapOptional(field, "_e"));
            b.nextControlFlow("else if (_e != null)");
            store(b, field, record, wrapOptional(field, "_e"));
            b.endControlFlow();
        } else {
            mergeUnknownVarintIfNull(b, model, tag, "_e");
            b.beginControlFlow("if (_e != null)");
            store(b, field, record, wrapOptional(field, "_e"));
            b.endControlFlow();
        }
    }

    private static void readOneof(CodeBlock.Builder b, FieldModel field, boolean record) {
        for (OneofCaseModel c : field.oneofCases) {
            b.beginControlFlow("case $L ->", c.tagConstant);
            if (c.empty()) {
                b.addStatement("int _old = reader.beginPacked()");
                b.beginControlFlow("while (reader.readTag() != 0)");
                b.addStatement("reader.skipField()");
                b.endControlFlow();
                b.addStatement("reader.popLimit(_old)");
                store(b, field, record, CodeBlock.of("new $L()", c.typeName));
            } else if (c.selfMessage) {
                store(b, field, record, CodeBlock.of("reader.readMessage($L.INSTANCE)", c.payload.codecName));
            } else if (c.payload.kind == FieldKind.MESSAGE) {
                store(b, field, record,
                    CodeBlock.of("new $L(reader.readMessage($L.INSTANCE))", c.typeName, c.payload.codecName));
            } else if (c.payload.kind == FieldKind.ENUM) {
                store(b, field, record,
                    CodeBlock.of("new $L($L(reader.readEnum()))", c.typeName, enumFrom(c.payload.enumModel)));
            } else {
                store(b, field, record, CodeBlock.of("new $L($L)", c.typeName, readCall(c.payload)));
            }
            b.endControlFlow();
        }
    }

    private static void readRepeated(CodeBlock.Builder b, FieldModel field, boolean record, String tag, MessageModel model) {
        if (field.packable()) {
            b.beginControlFlow("case $L, $L_PACKED ->", tag, tag);
            b.beginControlFlow("if (reader.wireType() == $T.LEN)", WIRE_TYPE);
            b.addStatement("int oldLimit = reader.beginPacked()");
            ensureRepeated(b, field, record);
            ensurePackedCapacity(b, field, record);
            b.beginControlFlow("while (reader.remaining() > 0)");
            repeatedAdd(b, field, record, model);
            b.endControlFlow();
            b.addStatement("reader.popLimit(oldLimit)");
            b.nextControlFlow("else");
            ensureRepeated(b, field, record);
            repeatedAdd(b, field, record, model);
            b.endControlFlow();
            b.endControlFlow();
        } else {
            b.beginControlFlow("case $L ->", tag);
            ensureRepeated(b, field, record);
            repeatedAdd(b, field, record, model);
            b.endControlFlow();
        }
    }

    private static void ensureRepeated(CodeBlock.Builder b, FieldModel field, boolean record) {
        if (field.array) {
            b.beginControlFlow("if ($LBuilder == null)", field.localName);
            b.addStatement("$LBuilder = new $L()", field.localName, arrayBuilderType(field));
            seedArrayBuilder(b, field, record);
            b.endControlFlow();
            return;
        }
        if (record) {
            b.beginControlFlow("if ($L == null || $L.isEmpty())", field.localName, field.localName);
            b.addStatement("$L = new $L()", field.localName, field.implTypeName);
            b.endControlFlow();
            return;
        }
        if (field.accessKind == AccessKind.FIELD) {
            b.addStatement("msg.$L = $L", field.fieldName, collectionEnsure("msg." + field.fieldName, field));
        } else {
            b.addStatement("$L $L = $L", field.javaTypeName, field.localName, field.readExpr.replace("value.", "msg."));
            b.addStatement("$L = $L", field.localName, collectionEnsure(field.localName, field));
            assign(b, field, "msg", field.localName);
        }
    }

    private static void unknownInit(CodeBlock.Builder b, MessageModel model) {
        MessageModel.UnknownField u = model.unknown;
        String current = u.readExpr().replace("value.", "msg.");
        b.addStatement("$T $L = $L != null ? $L : $T.EMPTY",
            UNKNOWN_FIELDS, u.localName(), current, current, UNKNOWN_FIELDS);
    }

    private static void unknownDefault(CodeBlock.Builder b, MessageModel model) {
        if (model.unknown != null) {
            b.addStatement("default -> $L = $T.merge($L, reader)",
                model.unknown.localName(), UNKNOWN_FIELDS, model.unknown.localName());
        } else {
            b.addStatement("default -> reader.skipField()");
        }
    }

    private static void unknownStore(CodeBlock.Builder b, MessageModel model) {
        MessageModel.UnknownField u = model.unknown;
        if (u.accessKind() == AccessKind.FIELD) {
            b.addStatement("msg.$L = $L", u.fieldName(), u.localName());
        } else {
            b.addStatement("msg.$L($L)", u.setterName(), u.localName());
        }
    }

    private static void recordComponentInit(
        CodeBlock.Builder b, MessageModel model, MessageModel.RecordComponentModel component) {
        String local = Names.safeLocal(component.name());
        String fromExisting = "existing." + component.name() + "()";
        if (model.unknown != null && component.name().equals(model.unknown.name())) {
            b.addStatement("$T $L = existing != null && $L != null ? $L : $T.EMPTY",
                UNKNOWN_FIELDS, local, fromExisting, fromExisting, UNKNOWN_FIELDS);
            return;
        }
        FieldModel field = component.field();
        if (field != null && (field.kind == FieldKind.REPEATED && !field.array || field.kind == FieldKind.MAP)) {
            b.addStatement("$L $L = existing != null && $L != null ? new $L($L) : $L",
                component.typeName(), local, fromExisting, field.implTypeName, fromExisting, component.defaultExpr());
            return;
        }
        b.addStatement("$L $L = existing != null ? $L : $L",
            component.typeName(), local, fromExisting, component.defaultExpr());
    }

    private static void readMessage(CodeBlock.Builder b, FieldModel field, boolean record) {
        String codec = field.codecName + ".INSTANCE";
        if (record) {
            String current = field.localName;
            if (field.javaOptional) {
                b.addStatement("$L = $L", current, wrapOptional(field,
                    CodeBlock.of("reader.readMessage($L, $L != null && $L.isPresent() ? $L.get() : null)",
                        codec, current, current, current)));
                return;
            }
            b.addStatement("$L = reader.readMessage($L, $L)", current, codec, current);
            return;
        }
        if (field.javaOptional) {
            String getter = field.accessKind == AccessKind.FIELD
                ? "msg." + field.fieldName
                : field.readExpr.replace("value.", "msg.");
            b.addStatement("$L _cur = $L", field.javaTypeName, getter);
            assign(b, field, "msg", wrapOptional(field,
                CodeBlock.of("reader.readMessage($L, _cur != null && _cur.isPresent() ? _cur.get() : null)", codec)));
            return;
        }
        String current = field.accessKind == AccessKind.FIELD
            ? "msg." + field.fieldName
            : field.readExpr.replace("value.", "msg.");
        assign(b, field, "msg", CodeBlock.of("reader.readMessage($L, $L)", codec, current));
    }

    private static void seedArrayBuilder(CodeBlock.Builder b, FieldModel field, boolean record) {
        String existing = record
            ? field.localName
            : field.accessKind == AccessKind.FIELD
                ? "msg." + field.fieldName
                : field.readExpr.replace("value.", "msg.");
        b.beginControlFlow("if ($L != null)", existing);
        b.beginControlFlow("for ($L item : $L)", field.arrayComponentType, existing);
        CodeBlock add = primitiveAdd(field, field.localName + "Builder", CodeBlock.of("item"));
        if (add != null) {
            b.addStatement("$L", add);
        } else {
            b.addStatement("$LBuilder.add(item)", field.localName);
        }
        b.endControlFlow();
        b.endControlFlow();
    }

    private static void ensurePackedCapacity(CodeBlock.Builder b, FieldModel field, boolean record) {
        CodeBlock ensure = packedEnsure(field, repeatedVar(field, record));
        if (ensure != null) {
            b.addStatement("$L", ensure);
        }
    }

    private static void repeatedAdd(CodeBlock.Builder b, FieldModel field, boolean record, MessageModel model) {
        if (field.element.kind == FieldKind.ENUM) {
            EnumModel enums = field.element.enumModel;
            b.addStatement("int _n = reader.readEnum()");
            b.addStatement("$L _item = $L(_n)", enums.typeName, enumFrom(enums));
            String tag = Names.tagConstant(field.number);
            if (enums.unrecognized != null) {
                b.beginControlFlow("if (_item == $L.$L)", enums.typeName, enums.unrecognized);
                mergeUnknownVarint(b, model, tag);
                b.nextControlFlow("else if (_item != null)");
            } else {
                mergeUnknownVarintIfNull(b, model, tag, "_item");
                b.beginControlFlow("if (_item != null)");
            }
            repeatedAddValue(b, field, record, CodeBlock.of("_item"));
            b.endControlFlow();
            return;
        }
        CodeBlock addend = field.element.kind == FieldKind.MESSAGE
            ? CodeBlock.of("reader.readMessage($L.INSTANCE)", field.element.codecName)
            : readCall(field.element);
        repeatedAddValue(b, field, record, addend);
    }

    private static void repeatedAddValue(CodeBlock.Builder b, FieldModel field, boolean record, CodeBlock addend) {
        String target = repeatedVar(field, record);
        CodeBlock add = primitiveAdd(field, target, addend);
        if (add != null) {
            b.addStatement("$L", add);
        } else {
            b.addStatement("$L.add($L)", target, addend);
        }
    }

    private static String repeatedVar(FieldModel field, boolean record) {
        if (field.array) {
            return field.localName + "Builder";
        }
        if (record || field.accessKind != AccessKind.FIELD) {
            return field.localName;
        }
        return "msg." + field.fieldName;
    }

    private static void ensureMap(CodeBlock.Builder b, FieldModel field, boolean record) {
        if (record) {
            b.beginControlFlow("if ($L == null)", field.localName);
            b.addStatement("$L = new $L()", field.localName, field.implTypeName);
            b.endControlFlow();
            return;
        }
        if (field.accessKind == AccessKind.FIELD) {
            b.addStatement("msg.$L = $L", field.fieldName, mapEnsure("msg." + field.fieldName, field));
        } else {
            b.addStatement("$L $L = $L", field.javaTypeName, field.localName, field.readExpr.replace("value.", "msg."));
            b.addStatement("$L = $L", field.localName, mapEnsure(field.localName, field));
            assign(b, field, "msg", field.localName);
        }
    }

    private static String mapVar(FieldModel field, boolean record) {
        if (record) {
            return field.localName;
        }
        if (field.accessKind == AccessKind.FIELD) {
            return "msg." + field.fieldName;
        }
        return field.localName;
    }

    private static void store(CodeBlock.Builder b, FieldModel field, boolean record, CodeBlock expr) {
        if (record) {
            b.addStatement("$L = $L", field.localName, expr);
        } else {
            assign(b, field, "msg", expr);
        }
    }

    private static void assign(CodeBlock.Builder b, FieldModel field, String target, Object expr) {
        if (field.accessKind == AccessKind.FIELD) {
            b.addStatement("$L.$L = $L", target, field.fieldName, expr);
        } else {
            b.addStatement("$L.$L($L)", target, field.setterName, expr);
        }
    }

    private static void mergeUnknownVarint(CodeBlock.Builder b, MessageModel model, String tag) {
        if (model.unknown != null) {
            b.addStatement("$L = $T.mergeVarint($L, $L, _n)",
                model.unknown.localName(), UNKNOWN_FIELDS, model.unknown.localName(), tag);
        }
    }

    private static void mergeUnknownVarintIfNull(CodeBlock.Builder b, MessageModel model, String tag, String enumVar) {
        if (model.unknown != null) {
            b.beginControlFlow("if ($L == null)", enumVar);
            b.addStatement("$L = $T.mergeVarint($L, $L, _n)",
                model.unknown.localName(), UNKNOWN_FIELDS, model.unknown.localName(), tag);
            b.endControlFlow();
        }
    }

    static void mapPartAssign(CodeBlock.Builder b, FieldModel part, String var) {
        if (part.kind == FieldKind.ENUM) {
            b.addStatement("int $LN = reader.readEnum()", var);
            b.addStatement("$L $LE = $L($LN)", part.enumModel.typeName, var, enumFrom(part.enumModel), var);
            if (part.enumModel.unrecognized != null) {
                b.beginControlFlow("if ($LE != null && $LE != $L.$L)",
                    var, var, part.enumModel.typeName, part.enumModel.unrecognized);
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
