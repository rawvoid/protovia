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
import com.palantir.javapoet.TypeName;
import io.github.rawvoid.protovia.processor.model.AccessKind;
import io.github.rawvoid.protovia.processor.model.EnumModel;
import io.github.rawvoid.protovia.processor.model.FieldKind;
import io.github.rawvoid.protovia.processor.model.FieldModel;
import io.github.rawvoid.protovia.processor.model.MessageModel;
import io.github.rawvoid.protovia.processor.model.Names;
import io.github.rawvoid.protovia.processor.model.OneofCaseModel;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static io.github.rawvoid.protovia.processor.gen.GenTypes.OPTIONAL;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.PROTO_EXCEPTION;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.PROTO_LISTS;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.WIRE_TYPE;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.codecInstance;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.enumConstant;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.enumType;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.javaType;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.oneofCaseType;
import static io.github.rawvoid.protovia.processor.gen.ReadMode.collectionCacheVar;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.collectionEnsure;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.fromWire;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.mapEnsure;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.primitiveAdd;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.readCall;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.wrapOptional;
import static io.github.rawvoid.protovia.processor.gen.WireTypes.packedFixedWidth;
import static io.github.rawvoid.protovia.processor.model.Names.enumFrom;
import static io.github.rawvoid.protovia.processor.model.Names.mapEntryRead;

/**
 * Emits per-field wire-read primitives shared by POJO and immutable read paths.
 *
 * @author Rawvoid
 */
final class FieldReadEmitter {

    private FieldReadEmitter() {
    }

    static void readLoop(CodeBlock.Builder b, MessageModel model, ReadMode mode) {
        b.addStatement("int tag");
        b.beginControlFlow("while ((tag = reader.readTag()) != 0)");
        b.beginControlFlow("switch (tag)");
        for (FieldModel field : model.fields) {
            readCases(b, field, mode, model);
        }
        unknownDefault(b, model, mode);
        b.endControlFlow();
        b.endControlFlow();
    }

    static void readCases(CodeBlock.Builder b, FieldModel field, ReadMode mode, MessageModel model) {
        String tag = Names.tagConstant(field.number);
        switch (field.kind) {
            case SCALAR -> {
                b.beginControlFlow("case $L ->", tag);
                CodeBlock read = readCall(field);
                if (field.adapterType != null) {
                    read = fromWire(field, read);
                }
                store(b, field, mode, wrapOptional(field, read));
                b.endControlFlow();
            }
            case ENUM -> {
                b.beginControlFlow("case $L ->", tag);
                readEnumField(b, field, mode, model, tag);
                b.endControlFlow();
            }
            case MESSAGE -> {
                b.beginControlFlow("case $L ->", tag);
                readMessage(b, field, mode);
                b.endControlFlow();
            }
            case REPEATED -> readRepeated(b, field, mode, tag, model);
            case MAP -> {
                b.beginControlFlow("case $L ->", tag);
                ensureMap(b, field, mode);
                if (field.mapValue.kind == FieldKind.ENUM && model.unknown != null) {
                    b.addStatement("$L(reader, $L, $L)",
                        mapEntryRead(field), mode.mapVar(field), mode.unknownBuilderRef());
                } else {
                    b.addStatement("$L(reader, $L)", mapEntryRead(field), mode.mapVar(field));
                }
                b.endControlFlow();
            }
            case ONEOF -> readOneof(b, field, mode, model);
        }
    }

    private static void readEnumField(
        CodeBlock.Builder b, FieldModel field, ReadMode mode, MessageModel model, String tag) {
        b.addStatement("int _n = reader.readEnum()");
        b.addStatement("$T _e = $L(_n)", enumType(field.enumModel), enumFrom(field.enumModel));
        if (field.enumModel.unrecognized != null) {
            b.beginControlFlow("if (_e == $L)", enumConstant(field.enumModel, field.enumModel.unrecognized));
            mergeUnknownVarint(b, model, mode, tag);
            store(b, field, mode, wrapOptional(field, "_e"));
            b.nextControlFlow("else if (_e != null)");
            removeUnknownField(b, model, mode, field.number);
            store(b, field, mode, wrapOptional(field, "_e"));
            b.endControlFlow();
        } else {
            b.beginControlFlow("if (_e != null)");
            removeUnknownField(b, model, mode, field.number);
            store(b, field, mode, wrapOptional(field, "_e"));
            b.nextControlFlow("else");
            mergeUnknownVarint(b, model, mode, tag);
            store(b, field, mode, field.javaOptional
                ? CodeBlock.of("$T.empty()", OPTIONAL)
                : CodeBlock.of("null"));
            b.endControlFlow();
        }
    }

    private static void readOneof(CodeBlock.Builder b, FieldModel field, ReadMode mode, MessageModel model) {
        List<Integer> enumCaseNumbers = oneofEnumNumbers(field);
        for (OneofCaseModel c : field.oneofCases) {
            b.beginControlFlow("case $L ->", c.tagConstant);
            if (c.empty()) {
                b.addStatement("int _old = reader.pushLengthDelimited()");
                b.beginControlFlow("while (reader.readTag() != 0)");
                b.addStatement("reader.skipField()");
                b.endControlFlow();
                b.addStatement("reader.popLimit(_old)");
                removeUnknownFields(b, model, mode, enumCaseNumbers);
                store(b, field, mode, CodeBlock.of("new $T()", oneofCaseType(c)));
            } else if (c.selfMessage) {
                readOneofSelfMessage(b, field, mode, c);
                removeUnknownFields(b, model, mode, enumCaseNumbers);
            } else if (c.payload.kind == FieldKind.MESSAGE) {
                readOneofWrappedMessage(b, field, mode, c);
                removeUnknownFields(b, model, mode, enumCaseNumbers);
            } else if (c.payload.kind == FieldKind.ENUM) {
                readOneofEnum(b, field, mode, model, c, enumCaseNumbers);
            } else {
                CodeBlock read = readCall(c.payload);
                if (c.payload.adapterType != null) {
                    read = fromWire(c.payload, read);
                }
                if (c.accessor == null) {
                    store(b, field, mode, read);
                } else {
                    store(b, field, mode, CodeBlock.of("new $T($L)", oneofCaseType(c), read));
                }
                removeUnknownFields(b, model, mode, enumCaseNumbers);
            }
            b.endControlFlow();
        }
    }

    private static void readOneofSelfMessage(
        CodeBlock.Builder b, FieldModel field, ReadMode mode, OneofCaseModel c) {
        TypeName caseType = oneofCaseType(c);
        CodeBlock codec = codecInstance(c.payload);
        if (mode.usesLocals()) {
            store(b, field, mode, CodeBlock.of(
                "reader.readMessage($L, $L instanceof $T _prev ? _prev : null)",
                codec, mode.localRef(field), caseType));
            return;
        }
        b.addStatement("Object _cur = $L", field.accessOn("msg"));
        store(b, field, mode, CodeBlock.of(
            "reader.readMessage($L, _cur instanceof $T _prev ? _prev : null)",
            codec, caseType));
    }

    private static void readOneofWrappedMessage(
        CodeBlock.Builder b, FieldModel field, ReadMode mode, OneofCaseModel c) {
        TypeName caseType = oneofCaseType(c);
        CodeBlock codec = codecInstance(c.payload);
        if (mode.usesLocals()) {
            store(b, field, mode, CodeBlock.of(
                "new $T(reader.readMessage($L, $L instanceof $T _prev ? _prev.$L : null))",
                caseType, codec, mode.localRef(field), caseType, c.accessor));
            return;
        }
        b.addStatement("Object _cur = $L", field.accessOn("msg"));
        store(b, field, mode, CodeBlock.of(
            "new $T(reader.readMessage($L, _cur instanceof $T _prev ? _prev.$L : null))",
            caseType, codec, caseType, c.accessor));
    }

    private static void readOneofEnum(
        CodeBlock.Builder b,
        FieldModel field,
        ReadMode mode,
        MessageModel model,
        OneofCaseModel c,
        List<Integer> enumCaseNumbers) {
        EnumModel enums = c.payload.enumModel;
        List<Integer> otherEnumCaseNumbers = enumCaseNumbers.stream()
            .filter(n -> n != c.number)
            .toList();
        b.addStatement("int _n = reader.readEnum()");
        b.addStatement("$T _e = $L(_n)", enumType(enums), enumFrom(enums));
        CodeBlock constructed = c.accessor == null
            ? CodeBlock.of("_e")
            : CodeBlock.of("new $T(_e)", oneofCaseType(c));
        if (enums.unrecognized != null) {
            b.beginControlFlow("if (_e == $L)", enumConstant(enums, enums.unrecognized));
            removeUnknownFields(b, model, mode, otherEnumCaseNumbers);
            mergeUnknownVarint(b, model, mode, c.tagConstant);
            store(b, field, mode, constructed);
            b.nextControlFlow("else if (_e != null)");
            removeUnknownFields(b, model, mode, enumCaseNumbers);
            store(b, field, mode, constructed);
            b.endControlFlow();
        } else {
            b.beginControlFlow("if (_e != null)");
            removeUnknownFields(b, model, mode, enumCaseNumbers);
            store(b, field, mode, constructed);
            b.nextControlFlow("else");
            removeUnknownFields(b, model, mode, otherEnumCaseNumbers);
            mergeUnknownVarint(b, model, mode, c.tagConstant);
            store(b, field, mode, CodeBlock.of("null"));
            b.endControlFlow();
        }
    }

    private static void readRepeated(
        CodeBlock.Builder b, FieldModel field, ReadMode mode, String tag, MessageModel model) {
        if (field.packable()) {
            int width = packedFixedWidth(field.element);
            PrimitiveListSpec spec = PrimitiveListSpec.ofPacked(field);
            boolean directPrimitive = spec != null && (field.array || javaType(field).equals(spec.listType()));
            b.beginControlFlow("case $L, $L_PACKED ->", tag, tag);
            b.beginControlFlow("if (reader.wireType() == $T.LEN)", WIRE_TYPE);
            b.addStatement("int _oldLimit = reader.pushLengthDelimited()");
            if (width == 8) {
                b.addStatement("int _rem = reader.remaining()");
                b.beginControlFlow("if ((_rem & 7) != 0)");
                b.addStatement("throw $T.truncated(\"packed fixed64 length \" + _rem + \" is not a multiple of 8\")",
                    PROTO_EXCEPTION);
                b.endControlFlow();
            } else if (width == 4) {
                b.addStatement("int _rem = reader.remaining()");
                b.beginControlFlow("if ((_rem & 3) != 0)");
                b.addStatement("throw $T.truncated(\"packed fixed32 length \" + _rem + \" is not a multiple of 4\")",
                    PROTO_EXCEPTION);
                b.endControlFlow();
            }
            ensureRepeated(b, field, mode);
            emitPackedLoop(b, field, mode, model, spec, directPrimitive, width);
            b.addStatement("reader.popLimit(_oldLimit)");
            b.nextControlFlow("else");
            ensureRepeated(b, field, mode);
            repeatedAdd(b, field, mode, model);
            b.endControlFlow();
            b.endControlFlow();
        } else {
            b.beginControlFlow("case $L ->", tag);
            ensureRepeated(b, field, mode);
            repeatedAdd(b, field, mode, model);
            b.endControlFlow();
        }
    }

    private static void emitPackedLoop(
        CodeBlock.Builder b,
        FieldModel field,
        ReadMode mode,
        MessageModel model,
        PrimitiveListSpec spec,
        boolean directPrimitive,
        int width) {
        String target = mode.repeatedVar(field);
        if (directPrimitive) {
            emitPackedCapacity(b, spec, target, width);
            b.beginControlFlow("while (reader.remaining() > 0)");
            b.addStatement("$L.$L($L)", target, spec.add(), readCall(field.element));
            b.endControlFlow();
            return;
        }
        if (spec != null) {
            b.beginControlFlow("if ($L instanceof $T _prim)", target, spec.listType());
            emitPackedCapacity(b, spec, "_prim", width);
            b.beginControlFlow("while (reader.remaining() > 0)");
            b.addStatement("_prim.$L($L)", spec.add(), readCall(field.element));
            b.endControlFlow();
            b.nextControlFlow("else");
            b.beginControlFlow("while (reader.remaining() > 0)");
            b.addStatement("$L.add($L)", target, readCall(field.element));
            b.endControlFlow();
            b.endControlFlow();
            return;
        }
        b.beginControlFlow("while (reader.remaining() > 0)");
        repeatedAdd(b, field, mode, model);
        b.endControlFlow();
    }

    private static void emitPackedCapacity(
        CodeBlock.Builder b, PrimitiveListSpec spec, String listExpr, int width) {
        if (width == 8) {
            b.beginControlFlow("if (_rem > 0)");
            b.addStatement("$T.$L($L, _rem >>> 3)", PROTO_LISTS, spec.ensure(), listExpr);
            b.endControlFlow();
        } else if (width == 4) {
            b.beginControlFlow("if (_rem > 0)");
            b.addStatement("$T.$L($L, _rem >>> 2)", PROTO_LISTS, spec.ensure(), listExpr);
            b.endControlFlow();
        } else if (width == 1) {
            b.addStatement("$T.$L($L, reader.remaining())", PROTO_LISTS, spec.ensure(), listExpr);
        }
    }

    private static void ensureRepeated(CodeBlock.Builder b, FieldModel field, ReadMode mode) {
        if (field.array) {
            String builderVar = mode.arrayBuilderRef(field);
            b.beginControlFlow("if ($L == null)", builderVar);
            b.addStatement("$L = new $T()", builderVar, WireCodegen.arrayBuilderType(field));
            seedArrayBuilder(b, field, mode);
            b.endControlFlow();
            return;
        }
        if (mode.usesLocals()) {
            // The locals path starts every collection out as a fresh mutable instance, so there is nothing to ensure.
            return;
        }
        if (mode.usesColCache()) {
            String col = collectionCacheVar(field);
            b.beginControlFlow("if ($L == null)", col);
            b.addStatement("$L = $L", col, collectionEnsure(field.accessOn("msg"), field));
            assign(b, field, "msg", col);
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

    private static void readMessage(CodeBlock.Builder b, FieldModel field, ReadMode mode) {
        CodeBlock codec = codecInstance(field);
        if (mode.usesLocals()) {
            String current = mode.localRef(field);
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

    private static void seedArrayBuilder(CodeBlock.Builder b, FieldModel field, ReadMode mode) {
        String existing = mode.usesLocals()
            ? mode.localRef(field)
            : field.accessOn("msg");
        String builderVar = mode.arrayBuilderRef(field);
        PrimitiveListSpec spec = PrimitiveListSpec.of(field);
        b.beginControlFlow("if ($L != null)", existing);
        b.beginControlFlow("for ($T item : $L)", javaType(field.element), existing);
        if (spec != null) {
            b.addStatement("$L.$L(item)", builderVar, spec.add());
        } else {
            b.addStatement("$L.add(item)", builderVar);
        }
        b.endControlFlow();
        b.endControlFlow();
    }

    private static void repeatedAdd(CodeBlock.Builder b, FieldModel field, ReadMode mode, MessageModel model) {
        if (field.element.kind == FieldKind.ENUM) {
            EnumModel enums = field.element.enumModel;
            b.addStatement("int _n = reader.readEnum()");
            b.addStatement("$T _item = $L(_n)", enumType(enums), enumFrom(enums));
            String tag = Names.tagConstant(field.number);
            if (enums.unrecognized != null) {
                b.beginControlFlow("if (_item == $L)", enumConstant(enums, enums.unrecognized));
                mergeUnknownVarint(b, model, mode, tag);
                b.nextControlFlow("else if (_item != null)");
                repeatedAddValue(b, field, mode, CodeBlock.of("_item"));
                b.endControlFlow();
            } else {
                b.beginControlFlow("if (_item != null)");
                repeatedAddValue(b, field, mode, CodeBlock.of("_item"));
                if (model.unknown != null) {
                    b.nextControlFlow("else");
                    mergeUnknownVarint(b, model, mode, tag);
                }
                b.endControlFlow();
            }
            return;
        }
        CodeBlock addend = field.element.kind == FieldKind.MESSAGE
            ? CodeBlock.of("reader.readMessage($L)", codecInstance(field.element))
            : readCall(field.element);
        if (field.element.adapterType != null) {
            addend = fromWire(field.element, addend);
        }
        repeatedAddValue(b, field, mode, addend);
    }

    private static void repeatedAddValue(CodeBlock.Builder b, FieldModel field, ReadMode mode, CodeBlock addend) {
        String target = mode.repeatedVar(field);
        PrimitiveListSpec spec = PrimitiveListSpec.of(field);
        if (spec != null && (field.array || javaType(field).equals(spec.listType()))) {
            b.addStatement("$L.$L($L)", target, spec.add(), addend);
            return;
        }
        CodeBlock add = primitiveAdd(field, target, addend);
        if (add != null) {
            b.addStatement("$L", add);
        } else {
            b.addStatement("$L.add($L)", target, addend);
        }
    }

    private static void ensureMap(CodeBlock.Builder b, FieldModel field, ReadMode mode) {
        if (mode.usesLocals()) {
            // Same as a repeated local: the map exists before the first entry is read.
            return;
        }
        if (mode.usesColCache()) {
            String col = collectionCacheVar(field);
            b.beginControlFlow("if ($L == null)", col);
            b.addStatement("$L = $L", col, mapEnsure(field.accessOn("msg"), field));
            assign(b, field, "msg", col);
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

    private static void store(CodeBlock.Builder b, FieldModel field, ReadMode mode, CodeBlock expr) {
        if (mode.usesLocals()) {
            b.addStatement("$L = $L", mode.localRef(field), expr);
        } else {
            assign(b, field, "msg", expr);
        }
    }

    static void assign(CodeBlock.Builder b, FieldModel field, String target, Object expr) {
        if (field.accessKind == AccessKind.FIELD) {
            b.addStatement("$L = $L", field.accessOn(target), expr);
        } else {
            b.addStatement("$L.$L($L)", target, field.setterName, expr);
        }
    }

    private static void unknownDefault(CodeBlock.Builder b, MessageModel model, ReadMode mode) {
        if (model.unknown != null) {
            b.addStatement("default -> $L.merge(reader)", mode.unknownBuilderRef());
        } else {
            b.addStatement("default -> reader.skipField()");
        }
    }

    private static List<Integer> oneofEnumNumbers(FieldModel field) {
        List<Integer> numbers = new ArrayList<>();
        for (OneofCaseModel c : field.oneofCases) {
            if (c.payload != null && c.payload.kind == FieldKind.ENUM) {
                numbers.add(c.number);
            }
        }
        return numbers;
    }

    private static void mergeUnknownVarint(CodeBlock.Builder b, MessageModel model, ReadMode mode, String tag) {
        if (model.unknown != null) {
            b.addStatement("$L.mergeVarint($L, _n)", mode.unknownBuilderRef(), tag);
        }
    }

    private static void removeUnknownField(CodeBlock.Builder b, MessageModel model, ReadMode mode, int fieldNumber) {
        if (model.unknown != null) {
            b.addStatement("$L.removeField($L)", mode.unknownBuilderRef(), fieldNumber);
        }
    }

    private static void removeUnknownFields(
        CodeBlock.Builder b, MessageModel model, ReadMode mode, List<Integer> fieldNumbers) {
        if (model.unknown == null || fieldNumbers.isEmpty()) {
            return;
        }
        if (fieldNumbers.size() == 1) {
            b.addStatement("$L.removeField($L)", mode.unknownBuilderRef(), fieldNumbers.getFirst());
            return;
        }
        String args = fieldNumbers.stream().map(String::valueOf).collect(Collectors.joining(", "));
        b.addStatement("$L.removeFields($L)", mode.unknownBuilderRef(), args);
    }
}
