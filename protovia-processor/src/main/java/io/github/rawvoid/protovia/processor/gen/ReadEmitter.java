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

import javax.lang.model.element.Modifier;
import java.util.List;
import java.util.stream.Collectors;

import static io.github.rawvoid.protovia.processor.gen.GenTypes.*;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.*;
import static io.github.rawvoid.protovia.processor.model.Names.enumFrom;
import static io.github.rawvoid.protovia.processor.model.Names.mapEntryRead;

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
        if (model.instantiation.usesLocals()) {
            readFrom.addStatement("return mergeFrom(reader, null)");
        } else {
            readFrom.addStatement("return mergeFrom(reader, new $T())", messageType(model));
        }
        return List.of(readFrom.build(), mergeFrom(model, msgType));
    }

    private static MethodSpec mergeFrom(MessageModel model, TypeName msgType) {
        CodeBlock.Builder body = CodeBlock.builder();
        if (model.instantiation.usesLocals()) {
            readLocals(body, model);
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
        b.addStatement("$T msg = existing != null ? existing : new $T()", messageType(model), messageType(model));
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

    private static void readLocals(CodeBlock.Builder b, MessageModel model) {
        if (model.record) {
            for (MessageModel.RecordComponentModel component : model.recordComponents) {
                recordComponentInit(b, model, component);
            }
        } else {
            for (FieldModel field : model.fields) {
                fieldLocalInit(b, field);
            }
            if (model.unknown != null) {
                unknownLocalInit(b, model);
            }
        }
        initArrayBuilders(b, model);
        readLoop(b, model, true);
        finalizeArrayBuilders(b, model, null);
        instantiate(b, model);
    }

    private static void instantiate(CodeBlock.Builder b, MessageModel model) {
        TypeName msgType = messageType(model);
        switch (model.instantiation) {
            case Instantiation.Constructor(var slots) -> {
                String args = slots.stream()
                    .map(Instantiation.Slot::localName)
                    .collect(Collectors.joining(", "));
                b.addStatement("return new $T($L)", msgType, args);
            }
            case Instantiation.Factory(var method, var slots) -> {
                String args = slots.stream()
                    .map(Instantiation.Slot::localName)
                    .collect(Collectors.joining(", "));
                b.addStatement("return $T.$L($L)", msgType, method, args);
            }
            case Instantiation.Builder(var factory, var nested, var build, var bindings) -> {
                if (factory != null && !factory.isEmpty()) {
                    b.addStatement("var builder = $T.$L()", msgType, factory);
                } else {
                    b.addStatement("var builder = new $T.$L()", msgType, nested);
                }
                for (Instantiation.BuilderBinding binding : bindings) {
                    b.addStatement("builder.$L($L)", binding.setterName(), binding.localName());
                }
                b.addStatement("return builder.$L()", build);
            }
            case Instantiation.Mutable() -> throw new IllegalStateException("locals path requires a constructor, factory, or builder");
        }
    }

    private static void fieldLocalInit(CodeBlock.Builder b, FieldModel field) {
        String local = field.localName;
        String fromExisting = field.accessOn("existing");
        if (field.kind == FieldKind.REPEATED && !field.array || field.kind == FieldKind.MAP) {
            b.addStatement("$T $L = existing != null && $L != null ? $L : $L",
                javaType(field), local, fromExisting, newImpl(field, fromExisting), defaultValue(field.javaType));
            return;
        }
        b.addStatement("$T $L = existing != null ? $L : $L",
            javaType(field), local, fromExisting, defaultValue(field.javaType));
    }

    private static void unknownLocalInit(CodeBlock.Builder b, MessageModel model) {
        MessageModel.UnknownField u = model.unknown;
        String fromExisting = u.accessOn("existing");
        b.addStatement("$T $L = existing != null && $L != null ? $L : $T.EMPTY",
            UNKNOWN_FIELDS, u.localName(), fromExisting, fromExisting, UNKNOWN_FIELDS);
    }

    private static void initArrayBuilders(CodeBlock.Builder b, MessageModel model) {
        for (FieldModel field : model.fields) {
            if (field.array) {
                b.addStatement("$T $LBuilder = null", WireCodegen.arrayBuilderType(field), field.localName);
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
                CodeBlock read = readCall(field);
                if (field.adapterType != null) {
                    read = fromWire(field, read);
                }
                store(b, field, record, wrapOptional(field, read));
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
            case ONEOF -> readOneof(b, field, record, model);
        }
    }

    private static void readEnumField(
        CodeBlock.Builder b, FieldModel field, boolean record, MessageModel model, String tag) {
        b.addStatement("int _n = reader.readEnum()");
        b.addStatement("$T _e = $L(_n)", enumType(field.enumModel), enumFrom(field.enumModel));
        if (field.enumModel.unrecognized != null) {
            b.beginControlFlow("if (_e == $L)", enumConstant(field.enumModel, field.enumModel.unrecognized));
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

    private static void readOneof(CodeBlock.Builder b, FieldModel field, boolean record, MessageModel model) {
        for (OneofCaseModel c : field.oneofCases) {
            b.beginControlFlow("case $L ->", c.tagConstant);
            if (c.empty()) {
                b.addStatement("int _old = reader.pushLengthDelimited()");
                b.beginControlFlow("while (reader.readTag() != 0)");
                b.addStatement("reader.skipField()");
                b.endControlFlow();
                b.addStatement("reader.popLimit(_old)");
                store(b, field, record, CodeBlock.of("new $T()", oneofCaseType(c)));
            } else if (c.selfMessage) {
                store(b, field, record, CodeBlock.of("reader.readMessage($L)", codecInstance(c.payload)));
            } else if (c.payload.kind == FieldKind.MESSAGE) {
                store(b, field, record,
                    CodeBlock.of("new $T(reader.readMessage($L))", oneofCaseType(c), codecInstance(c.payload)));
            } else if (c.payload.kind == FieldKind.ENUM) {
                readOneofEnum(b, field, record, model, c);
            } else {
                CodeBlock read = readCall(c.payload);
                if (c.payload.adapterType != null) {
                    read = fromWire(c.payload, read);
                }
                if (c.accessor == null) {
                    store(b, field, record, read);
                } else {
                    store(b, field, record, CodeBlock.of("new $T($L)", oneofCaseType(c), read));
                }
            }
            b.endControlFlow();
        }
    }

    private static void readOneofEnum(
        CodeBlock.Builder b, FieldModel field, boolean record, MessageModel model, OneofCaseModel c) {
        EnumModel enums = c.payload.enumModel;
        b.addStatement("int _n = reader.readEnum()");
        b.addStatement("$T _e = $L(_n)", enumType(enums), enumFrom(enums));
        CodeBlock constructed = c.accessor == null
            ? CodeBlock.of("_e")
            : CodeBlock.of("new $T(_e)", oneofCaseType(c));
        if (enums.unrecognized != null) {
            b.beginControlFlow("if (_e == $L)", enumConstant(enums, enums.unrecognized));
            mergeUnknownVarint(b, model, c.tagConstant);
            store(b, field, record, constructed);
            b.nextControlFlow("else if (_e != null)");
            store(b, field, record, constructed);
            b.endControlFlow();
        } else {
            mergeUnknownVarintIfNull(b, model, c.tagConstant, "_e");
            b.beginControlFlow("if (_e != null)");
            store(b, field, record, constructed);
            b.endControlFlow();
        }
    }

    private static void readRepeated(CodeBlock.Builder b, FieldModel field, boolean record, String tag, MessageModel model) {
        if (field.packable()) {
            b.beginControlFlow("case $L, $L_PACKED ->", tag, tag);
            b.beginControlFlow("if (reader.wireType() == $T.LEN)", WIRE_TYPE);
            b.addStatement("int oldLimit = reader.pushLengthDelimited()");
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
            b.addStatement("$LBuilder = new $T()", field.localName, WireCodegen.arrayBuilderType(field));
            seedArrayBuilder(b, field, record);
            b.endControlFlow();
            return;
        }
        if (record) {
            b.beginControlFlow("if ($L == null || $L.isEmpty())", field.localName, field.localName);
            b.addStatement("$L = $L", field.localName, newImpl(field));
            b.endControlFlow();
            return;
        }
        if (field.accessKind == AccessKind.FIELD) {
            String access = field.accessOn("msg");
            b.addStatement("$L = $L", access, collectionEnsure(access, field));
        } else {
            b.addStatement("$T $L = $L", javaType(field), field.localName, field.accessOn("msg"));
            b.addStatement("$L = $L", field.localName, collectionEnsure(field.localName, field));
            assign(b, field, "msg", field.localName);
        }
    }

    private static void unknownInit(CodeBlock.Builder b, MessageModel model) {
        MessageModel.UnknownField u = model.unknown;
        String current = u.accessOn("msg");
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
            b.addStatement("$L = $L", u.accessOn("msg"), u.localName());
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
            b.addStatement("$T $L = existing != null && $L != null ? $L : $L",
                javaType(component.type()), local, fromExisting, newImpl(field, fromExisting), defaultValue(component.type()));
            return;
        }
        b.addStatement("$T $L = existing != null ? $L : $L",
            javaType(component.type()), local, fromExisting, defaultValue(component.type()));
    }

    private static void readMessage(CodeBlock.Builder b, FieldModel field, boolean record) {
        CodeBlock codec = codecInstance(field);
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
            String getter = field.accessOn("msg");
            b.addStatement("$T _cur = $L", javaType(field), getter);
            assign(b, field, "msg", wrapOptional(field,
                CodeBlock.of("reader.readMessage($L, _cur != null && _cur.isPresent() ? _cur.get() : null)", codec)));
            return;
        }
        String current = field.accessOn("msg");
        assign(b, field, "msg", CodeBlock.of("reader.readMessage($L, $L)", codec, current));
    }

    private static void seedArrayBuilder(CodeBlock.Builder b, FieldModel field, boolean record) {
        String existing = record
            ? field.localName
            : field.accessOn("msg");
        b.beginControlFlow("if ($L != null)", existing);
        b.beginControlFlow("for ($T item : $L)", javaType(field.element), existing);
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
            b.addStatement("$T _item = $L(_n)", enumType(enums), enumFrom(enums));
            String tag = Names.tagConstant(field.number);
            if (enums.unrecognized != null) {
                b.beginControlFlow("if (_item == $L)", enumConstant(enums, enums.unrecognized));
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
            ? CodeBlock.of("reader.readMessage($L)", codecInstance(field.element))
            : readCall(field.element);
        if (field.element.adapterType != null) {
            addend = fromWire(field.element, addend);
        }
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
        return field.accessOn("msg");
    }

    private static void ensureMap(CodeBlock.Builder b, FieldModel field, boolean record) {
        if (record) {
            b.beginControlFlow("if ($L == null)", field.localName);
            b.addStatement("$L = $L", field.localName, newImpl(field));
            b.endControlFlow();
            return;
        }
        if (field.accessKind == AccessKind.FIELD) {
            String access = field.accessOn("msg");
            b.addStatement("$L = $L", access, mapEnsure(access, field));
        } else {
            b.addStatement("$T $L = $L", javaType(field), field.localName, field.accessOn("msg"));
            b.addStatement("$L = $L", field.localName, mapEnsure(field.localName, field));
            assign(b, field, "msg", field.localName);
        }
    }

    private static String mapVar(FieldModel field, boolean record) {
        if (record) {
            return field.localName;
        }
        if (field.accessKind == AccessKind.FIELD) {
            return field.accessOn("msg");
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
            b.addStatement("$L = $L", field.accessOn(target), expr);
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

}
