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

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import io.github.rawvoid.protovia.processor.model.*;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static io.github.rawvoid.protovia.processor.gen.GenTypes.*;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.*;
import static io.github.rawvoid.protovia.processor.gen.WireTypes.packedFixedWidth;
import static io.github.rawvoid.protovia.processor.model.Names.enumFrom;
import static io.github.rawvoid.protovia.processor.model.Names.mapEntryRead;

/**
 * Emits {@code readFrom} / {@code mergeFrom} and related field-read logic.
 *
 * @author Rawvoid
 */
final class ReadEmitter {

    private enum ReadMode {
        POJO,
        POJO_SPLIT,
        LOCALS,
        LOCALS_SPLIT;

        boolean usesLocals() {
            return this == LOCALS || this == LOCALS_SPLIT;
        }

        boolean usesColCache() {
            return this == POJO;
        }

        String localRef(FieldModel field) {
            return this == LOCALS_SPLIT ? "_s." + field.localName : field.localName;
        }

        String arrayBuilderRef(FieldModel field) {
            String var = "_builder_" + field.number;
            return switch (this) {
                case POJO, LOCALS -> var;
                case POJO_SPLIT -> "_arr." + var;
                case LOCALS_SPLIT -> "_s." + var;
            };
        }

        String unknownBuilderRef() {
            return this == LOCALS_SPLIT ? "_s._ub" : "_ub";
        }

        String repeatedVar(FieldModel field) {
            if (field.array) {
                return arrayBuilderRef(field);
            }
            return switch (this) {
                case POJO -> collectionCacheVar(field);
                case POJO_SPLIT -> field.accessKind == AccessKind.FIELD ? field.accessOn("msg") : field.localName;
                case LOCALS -> field.localName;
                case LOCALS_SPLIT -> "_s." + field.localName;
            };
        }

        String mapVar(FieldModel field) {
            return switch (this) {
                case POJO -> collectionCacheVar(field);
                case POJO_SPLIT -> field.accessKind == AccessKind.FIELD ? field.accessOn("msg") : field.localName;
                case LOCALS -> field.localName;
                case LOCALS_SPLIT -> "_s." + field.localName;
            };
        }
    }

    private ReadEmitter() {
    }

    static void emit(TypeSpec.Builder type, ClassName codecType, MessageModel model, TypeName msgType) {
        MethodSpec.Builder readFrom = MethodSpec.methodBuilder("readFrom")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(msgType)
            .addParameter(PROTO_READER, "reader");
        if (model.instantiation.usesLocals()) {
            readFrom.addStatement("return mergeFrom(reader, null)");
        } else {
            readFrom.addStatement("return mergeFrom(reader, new $T())", msgType);
        }
        type.addMethod(readFrom.build());

        boolean split = shouldSplitRead(model);
        if (model.instantiation.usesLocals()) {
            if (split) {
                emitLocalsSplit(type, codecType, model, msgType);
            } else {
                type.addMethod(mergeFromLocals(model, msgType));
            }
        } else {
            if (split) {
                emitPojoSplit(type, codecType, model, msgType);
            } else {
                type.addMethod(mergeFromPojo(model, msgType));
            }
        }
    }

    private static boolean shouldSplitRead(MessageModel model) {
        return shouldSplit(model) || (model.record && model.recordComponents.size() > SPLIT_THRESHOLD);
    }

    private static MethodSpec mergeFromPojo(MessageModel model, TypeName msgType) {
        CodeBlock.Builder b = CodeBlock.builder();
        b.addStatement("$T msg = existing != null ? existing : new $T()", msgType, msgType);
        initCollectionCaches(b, model);
        initArrayBuilders(b, model);
        if (model.unknown != null) {
            unknownInit(b, model);
        }
        readLoop(b, model, ReadMode.POJO);
        finalizeArrayBuilders(b, model, ReadMode.POJO, "msg");
        fillAbsentCollections(b, model, true);
        if (model.unknown != null) {
            unknownStore(b, model);
        }
        b.addStatement("return msg");
        return mergeFromSpec(msgType, b.build());
    }

    private static MethodSpec mergeFromLocals(MessageModel model, TypeName msgType) {
        CodeBlock.Builder b = CodeBlock.builder();
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
        readLoop(b, model, ReadMode.LOCALS);
        finalizeArrayBuilders(b, model, ReadMode.LOCALS, null);
        if (model.unknown != null) {
            b.addStatement("$T $L = _ub.build()", UNKNOWN_FIELDS, model.unknown.localName());
        }
        instantiate(b, model, false);
        return mergeFromSpec(msgType, b.build());
    }

    private static void emitPojoSplit(
        TypeSpec.Builder type, ClassName codecType, MessageModel model, TypeName msgType) {
        List<FieldModel> arrayFields = arrayFields(model);
        List<FieldModel> collectionFields = collectionFields(model);
        boolean hasArrays = !arrayFields.isEmpty();
        ClassName arrayStateType = codecType.nestedClass("_ArrayState");

        if (hasArrays) {
            TypeSpec.Builder arrState = TypeSpec.classBuilder("_ArrayState")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);
            for (FieldModel field : arrayFields) {
                arrState.addField(FieldSpec.builder(arrayBuilderType(field), arrayBuilderVar(field)).build());
            }
            type.addType(arrState.build());
        }

        List<List<FieldModel>> chunks = chunkFields(model.fields);
        CodeBlock.Builder b = CodeBlock.builder();
        b.addStatement("$T msg = existing != null ? existing : new $T()", msgType, msgType);
        if (hasArrays) {
            b.addStatement("$T _arr = new $T()", arrayStateType, arrayStateType);
        }
        if (model.unknown != null) {
            unknownInit(b, model);
        }
        b.addStatement("int tag");
        b.beginControlFlow("while ((tag = reader.readTag()) != 0)");
        String extraArgs = (hasArrays ? ", _arr" : "") + (model.unknown != null ? ", _ub" : "");
        for (int i = 0; i < chunks.size(); i++) {
            b.beginControlFlow("if (_readChunk_$L(reader, tag, msg$L))", i, extraArgs);
            b.addStatement("continue");
            b.endControlFlow();
        }
        if (model.unknown != null) {
            b.addStatement("_ub.merge(reader)");
        } else {
            b.addStatement("reader.skipField()");
        }
        b.endControlFlow();

        List<List<FieldModel>> arrayChunks = hasArrays ? chunkList(arrayFields) : List.of();
        for (int i = 0; i < arrayChunks.size(); i++) {
            b.addStatement("_finalizeArrays_$L(_arr, msg)", i);
        }
        List<List<FieldModel>> collectionChunks = collectionFields.isEmpty() ? List.of() : chunkList(collectionFields);
        for (int i = 0; i < collectionChunks.size(); i++) {
            b.addStatement("_fillAbsent_$L(msg)", i);
        }
        if (model.unknown != null) {
            unknownStore(b, model);
        }
        b.addStatement("return msg");
        type.addMethod(mergeFromSpec(msgType, b.build()));

        for (int i = 0; i < chunks.size(); i++) {
            type.addMethod(pojoReadChunk(i, chunks.get(i), model, msgType, hasArrays ? arrayStateType : null));
        }
        for (int i = 0; i < arrayChunks.size(); i++) {
            type.addMethod(pojoFinalizeArraysChunk(i, arrayChunks.get(i), msgType, arrayStateType));
        }
        for (int i = 0; i < collectionChunks.size(); i++) {
            type.addMethod(pojoFillAbsentChunk(i, collectionChunks.get(i), msgType));
        }
    }

    private static MethodSpec pojoReadChunk(
        int index, List<FieldModel> fields, MessageModel model, TypeName msgType, ClassName arrayStateType) {
        CodeBlock.Builder b = CodeBlock.builder();
        b.beginControlFlow("switch (tag)");
        for (FieldModel field : fields) {
            readCases(b, field, ReadMode.POJO_SPLIT, model);
        }
        b.beginControlFlow("default ->");
        b.addStatement("return false");
        b.endControlFlow();
        b.endControlFlow();
        b.addStatement("return true");
        MethodSpec.Builder m = MethodSpec.methodBuilder("_readChunk_" + index)
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .returns(TypeName.BOOLEAN)
            .addParameter(PROTO_READER, "reader")
            .addParameter(TypeName.INT, "tag")
            .addParameter(msgType, "msg");
        if (arrayStateType != null) {
            m.addParameter(arrayStateType, "_arr");
        }
        if (model.unknown != null) {
            m.addParameter(UNKNOWN_FIELDS_BUILDER, "_ub");
        }
        return m.addCode(b.build()).build();
    }

    private static MethodSpec pojoFinalizeArraysChunk(
        int index, List<FieldModel> fields, TypeName msgType, ClassName arrayStateType) {
        CodeBlock.Builder b = CodeBlock.builder();
        for (FieldModel field : fields) {
            String builderVar = ReadMode.POJO_SPLIT.arrayBuilderRef(field);
            b.beginControlFlow("if ($L != null)", builderVar);
            assign(b, field, "msg", toArray(field, builderVar));
            b.endControlFlow();
        }
        return MethodSpec.methodBuilder("_finalizeArrays_" + index)
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .returns(TypeName.VOID)
            .addParameter(arrayStateType, "_arr")
            .addParameter(msgType, "msg")
            .addCode(b.build())
            .build();
    }

    private static MethodSpec pojoFillAbsentChunk(int index, List<FieldModel> fields, TypeName msgType) {
        CodeBlock.Builder b = CodeBlock.builder();
        for (FieldModel field : fields) {
            b.beginControlFlow("if ($L == null)", field.accessOn("msg"));
            assign(b, field, "msg", newImpl(field));
            b.endControlFlow();
        }
        return MethodSpec.methodBuilder("_fillAbsent_" + index)
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .returns(TypeName.VOID)
            .addParameter(msgType, "msg")
            .addCode(b.build())
            .build();
    }

    private static void emitLocalsSplit(
        TypeSpec.Builder type, ClassName codecType, MessageModel model, TypeName msgType) {
        ClassName stateType = codecType.nestedClass("_ReadState");
        List<FieldModel> arrayFields = arrayFields(model);

        TypeSpec.Builder state = TypeSpec.classBuilder("_ReadState")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);
        if (model.record) {
            for (MessageModel.RecordComponentModel component : model.recordComponents) {
                if (model.unknown != null && component.name().equals(model.unknown.name())) {
                    continue;
                }
                state.addField(FieldSpec.builder(javaType(component.type()), Names.safeLocal(component.name())).build());
            }
        } else {
            for (FieldModel field : model.fields) {
                state.addField(FieldSpec.builder(javaType(field), field.localName).build());
            }
        }
        for (FieldModel field : arrayFields) {
            state.addField(FieldSpec.builder(arrayBuilderType(field), arrayBuilderVar(field)).build());
        }
        if (model.unknown != null) {
            state.addField(FieldSpec.builder(UNKNOWN_FIELDS_BUILDER, "_ub").build());
        }
        type.addType(state.build());

        List<MethodSpec> initMethods = buildStateInitMethods(model, msgType, stateType);
        List<List<FieldModel>> chunks = chunkFields(model.fields);
        List<List<FieldModel>> arrayChunks = arrayFields.isEmpty() ? List.of() : chunkList(arrayFields);

        CodeBlock.Builder b = CodeBlock.builder();
        b.addStatement("$T _s = new $T()", stateType, stateType);
        for (int i = 0; i < initMethods.size(); i++) {
            b.addStatement("_initState_$L(_s, existing)", i);
        }
        if (model.unknown != null) {
            b.addStatement("_s._ub = $T.newBuilder(existing != null ? $L : null)",
                UNKNOWN_FIELDS, model.unknown.accessOn("existing"));
        }
        b.addStatement("int tag");
        b.beginControlFlow("while ((tag = reader.readTag()) != 0)");
        for (int i = 0; i < chunks.size(); i++) {
            b.beginControlFlow("if (_readChunk_$L(reader, tag, _s))", i);
            b.addStatement("continue");
            b.endControlFlow();
        }
        if (model.unknown != null) {
            b.addStatement("_s._ub.merge(reader)");
        } else {
            b.addStatement("reader.skipField()");
        }
        b.endControlFlow();
        for (int i = 0; i < arrayChunks.size(); i++) {
            b.addStatement("_finalizeArrays_$L(_s)", i);
        }
        if (model.unknown != null) {
            b.addStatement("$T $L = _s._ub.build()", UNKNOWN_FIELDS, model.unknown.localName());
        }
        instantiate(b, model, true);
        type.addMethod(mergeFromSpec(msgType, b.build()));

        initMethods.forEach(type::addMethod);
        for (int i = 0; i < chunks.size(); i++) {
            type.addMethod(localsReadChunk(i, chunks.get(i), model, stateType));
        }
        for (int i = 0; i < arrayChunks.size(); i++) {
            type.addMethod(localsFinalizeArraysChunk(i, arrayChunks.get(i), stateType));
        }
    }

    private static List<MethodSpec> buildStateInitMethods(
        MessageModel model, TypeName msgType, ClassName stateType) {
        List<MethodSpec> methods = new ArrayList<>();
        if (model.record) {
            List<MessageModel.RecordComponentModel> components = new ArrayList<>();
            for (MessageModel.RecordComponentModel component : model.recordComponents) {
                if (model.unknown != null && component.name().equals(model.unknown.name())) {
                    continue;
                }
                components.add(component);
            }
            List<List<MessageModel.RecordComponentModel>> chunks = chunkList(components);
            for (int i = 0; i < chunks.size(); i++) {
                CodeBlock.Builder b = CodeBlock.builder();
                for (MessageModel.RecordComponentModel component : chunks.get(i)) {
                    String target = "_s." + Names.safeLocal(component.name());
                    String fromExisting = "existing." + component.name() + "()";
                    FieldModel field = component.field();
                    if (field != null && isCollection(field)) {
                        assignCollectionState(b, field, target, fromExisting);
                    } else {
                        b.addStatement("$L = existing != null ? $L : $L",
                            target, fromExisting, defaultValue(component.type()));
                    }
                }
                methods.add(stateInitMethodSpec(i, stateType, msgType, b.build()));
            }
        } else {
            List<List<FieldModel>> chunks = chunkList(model.fields);
            for (int i = 0; i < chunks.size(); i++) {
                CodeBlock.Builder b = CodeBlock.builder();
                for (FieldModel field : chunks.get(i)) {
                    String target = "_s." + field.localName;
                    String fromExisting = field.accessOn("existing");
                    if (isCollection(field)) {
                        assignCollectionState(b, field, target, fromExisting);
                    } else {
                        b.addStatement("$L = existing != null ? $L : $L",
                            target, fromExisting, defaultValue(field.javaType));
                    }
                }
                methods.add(stateInitMethodSpec(i, stateType, msgType, b.build()));
            }
        }
        return methods;
    }

    private static MethodSpec stateInitMethodSpec(
        int index, ClassName stateType, TypeName msgType, CodeBlock body) {
        return MethodSpec.methodBuilder("_initState_" + index)
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .returns(TypeName.VOID)
            .addParameter(stateType, "_s")
            .addParameter(msgType, "existing")
            .addCode(body)
            .build();
    }

    private static void assignCollectionState(
        CodeBlock.Builder b, FieldModel field, String target, String fromExisting) {
        if (field.implCopyConstructor) {
            b.addStatement("$L = existing != null && $L != null ? $L : $L",
                target, fromExisting, newImpl(field, fromExisting), newImpl(field));
            return;
        }
        b.addStatement("$L = $L", target, newImpl(field));
        b.beginControlFlow("if (existing != null && $L != null)", fromExisting);
        if (field.kind == FieldKind.MAP) {
            b.addStatement("$L.putAll($L)", target, fromExisting);
        } else {
            b.addStatement("$L.addAll($L)", target, fromExisting);
        }
        b.endControlFlow();
    }

    private static MethodSpec localsReadChunk(
        int index, List<FieldModel> fields, MessageModel model, ClassName stateType) {
        CodeBlock.Builder b = CodeBlock.builder();
        b.beginControlFlow("switch (tag)");
        for (FieldModel field : fields) {
            readCases(b, field, ReadMode.LOCALS_SPLIT, model);
        }
        b.beginControlFlow("default ->");
        b.addStatement("return false");
        b.endControlFlow();
        b.endControlFlow();
        b.addStatement("return true");
        return MethodSpec.methodBuilder("_readChunk_" + index)
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .returns(TypeName.BOOLEAN)
            .addParameter(PROTO_READER, "reader")
            .addParameter(TypeName.INT, "tag")
            .addParameter(stateType, "_s")
            .addCode(b.build())
            .build();
    }

    private static MethodSpec localsFinalizeArraysChunk(
        int index, List<FieldModel> fields, ClassName stateType) {
        CodeBlock.Builder b = CodeBlock.builder();
        for (FieldModel field : fields) {
            String builderVar = ReadMode.LOCALS_SPLIT.arrayBuilderRef(field);
            b.beginControlFlow("if ($L != null)", builderVar);
            b.addStatement("$L = $L", ReadMode.LOCALS_SPLIT.localRef(field), toArray(field, builderVar));
            b.endControlFlow();
        }
        return MethodSpec.methodBuilder("_finalizeArrays_" + index)
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .returns(TypeName.VOID)
            .addParameter(stateType, "_s")
            .addCode(b.build())
            .build();
    }

    private static MethodSpec mergeFromSpec(TypeName msgType, CodeBlock body) {
        return MethodSpec.methodBuilder("mergeFrom")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(msgType)
            .addParameter(PROTO_READER, "reader")
            .addParameter(msgType, "existing")
            .addCode(body)
            .build();
    }

    private static void instantiate(CodeBlock.Builder b, MessageModel model, boolean splitLocals) {
        TypeName msgType = messageType(model);
        switch (model.instantiation) {
            case Instantiation.Constructor(var slots) -> {
                String args = slots.stream()
                    .map(slot -> slotExpr(slot.localName(), slot.unknown(), splitLocals))
                    .collect(Collectors.joining(", "));
                b.addStatement("return new $T($L)", msgType, args);
            }
            case Instantiation.Factory(var method, var slots) -> {
                String args = slots.stream()
                    .map(slot -> slotExpr(slot.localName(), slot.unknown(), splitLocals))
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
                    b.addStatement("builder.$L($L)",
                        binding.setterName(),
                        slotExpr(binding.localName(), binding.unknown(), splitLocals));
                }
                b.addStatement("return builder.$L()", build);
            }
            case Instantiation.Mutable() -> throw new IllegalStateException("locals path requires a constructor, factory, or builder");
        }
    }

    private static String slotExpr(String localName, boolean unknown, boolean splitLocals) {
        return splitLocals && !unknown ? "_s." + localName : localName;
    }

    private static void fieldLocalInit(CodeBlock.Builder b, FieldModel field) {
        String local = field.localName;
        String fromExisting = field.accessOn("existing");
        if (isCollection(field)) {
            initCollectionLocal(b, field, javaType(field), local, fromExisting);
            return;
        }
        b.addStatement("$T $L = existing != null ? $L : $L",
            javaType(field), local, fromExisting, defaultValue(field.javaType));
    }

    private static void initCollectionLocal(
        CodeBlock.Builder b, FieldModel field, TypeName typeName, String local, String fromExisting) {
        if (field.implCopyConstructor) {
            b.addStatement("$T $L = existing != null && $L != null ? $L : $L",
                typeName, local, fromExisting, newImpl(field, fromExisting), newImpl(field));
            return;
        }
        b.addStatement("$T $L = $L", typeName, local, newImpl(field));
        b.beginControlFlow("if (existing != null && $L != null)", fromExisting);
        if (field.kind == FieldKind.MAP) {
            b.addStatement("$L.putAll($L)", local, fromExisting);
        } else {
            b.addStatement("$L.addAll($L)", local, fromExisting);
        }
        b.endControlFlow();
    }

    private static boolean isCollection(FieldModel field) {
        return field.kind == FieldKind.REPEATED && !field.array || field.kind == FieldKind.MAP;
    }

    private static List<FieldModel> arrayFields(MessageModel model) {
        List<FieldModel> result = new ArrayList<>();
        for (FieldModel field : model.fields) {
            if (field.array) {
                result.add(field);
            }
        }
        return result;
    }

    private static List<FieldModel> collectionFields(MessageModel model) {
        List<FieldModel> result = new ArrayList<>();
        for (FieldModel field : model.fields) {
            if (isCollection(field)) {
                result.add(field);
            }
        }
        return result;
    }

    private static String collectionCacheVar(FieldModel field) {
        return "_col_" + field.number;
    }

    private static void initCollectionCaches(CodeBlock.Builder b, MessageModel model) {
        for (FieldModel field : model.fields) {
            if (isCollection(field)) {
                b.addStatement("$T $L = null", javaType(field), collectionCacheVar(field));
            }
        }
    }

    private static void unknownLocalInit(CodeBlock.Builder b, MessageModel model) {
        b.addStatement("$T _ub = $T.newBuilder(existing != null ? $L : null)",
            UNKNOWN_FIELDS_BUILDER, UNKNOWN_FIELDS, model.unknown.accessOn("existing"));
    }

    /**
     * A repeated / map field absent from the wire has no presence in proto3: it is the same as an empty one, and
     * the official implementations hand out empty collections rather than null. A mutable target starts from the
     * model's own (possibly null) fields, so fill them once the read loop is done.
     */
    private static void fillAbsentCollections(CodeBlock.Builder b, MessageModel model, boolean useColCache) {
        for (FieldModel field : model.fields) {
            if (isCollection(field)) {
                if (useColCache) {
                    b.beginControlFlow("if ($L == null && $L == null)",
                        collectionCacheVar(field), field.accessOn("msg"));
                } else {
                    b.beginControlFlow("if ($L == null)", field.accessOn("msg"));
                }
                assign(b, field, "msg", newImpl(field));
                b.endControlFlow();
            }
        }
    }

    private static String arrayBuilderVar(FieldModel field) {
        return "_builder_" + field.number;
    }

    private static void initArrayBuilders(CodeBlock.Builder b, MessageModel model) {
        for (FieldModel field : model.fields) {
            if (field.array) {
                b.addStatement("$T $L = null", WireCodegen.arrayBuilderType(field), arrayBuilderVar(field));
            }
        }
    }

    private static void finalizeArrayBuilders(
        CodeBlock.Builder b, MessageModel model, ReadMode mode, String assignTarget) {
        for (FieldModel field : model.fields) {
            if (!field.array) {
                continue;
            }
            String builderVar = mode.arrayBuilderRef(field);
            b.beginControlFlow("if ($L != null)", builderVar);
            if (assignTarget == null) {
                b.addStatement("$L = $L", mode.localRef(field), toArray(field, builderVar));
            } else {
                assign(b, field, assignTarget, toArray(field, builderVar));
            }
            b.endControlFlow();
        }
    }

    private static void readLoop(CodeBlock.Builder b, MessageModel model, ReadMode mode) {
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

    private static void readCases(CodeBlock.Builder b, FieldModel field, ReadMode mode, MessageModel model) {
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

    private static void unknownInit(CodeBlock.Builder b, MessageModel model) {
        b.addStatement("$T _ub = $T.newBuilder($L)",
            UNKNOWN_FIELDS_BUILDER, UNKNOWN_FIELDS, model.unknown.accessOn("msg"));
    }

    private static void unknownDefault(CodeBlock.Builder b, MessageModel model, ReadMode mode) {
        if (model.unknown != null) {
            b.addStatement("default -> $L.merge(reader)", mode.unknownBuilderRef());
        } else {
            b.addStatement("default -> reader.skipField()");
        }
    }

    private static void unknownStore(CodeBlock.Builder b, MessageModel model) {
        MessageModel.UnknownField u = model.unknown;
        if (u.accessKind() == AccessKind.FIELD) {
            b.addStatement("$L = _ub.build()", u.accessOn("msg"));
        } else {
            b.addStatement("msg.$L(_ub.build())", u.setterName());
        }
    }

    private static void recordComponentInit(
        CodeBlock.Builder b, MessageModel model, MessageModel.RecordComponentModel component) {
        String local = Names.safeLocal(component.name());
        String fromExisting = "existing." + component.name() + "()";
        if (model.unknown != null && component.name().equals(model.unknown.name())) {
            b.addStatement("$T _ub = $T.newBuilder(existing != null ? $L : null)",
                UNKNOWN_FIELDS_BUILDER, UNKNOWN_FIELDS, fromExisting);
            return;
        }
        FieldModel field = component.field();
        if (field != null && isCollection(field)) {
            initCollectionLocal(b, field, javaType(component.type()), local, fromExisting);
            return;
        }
        b.addStatement("$T $L = existing != null ? $L : $L",
            javaType(component.type()), local, fromExisting, defaultValue(component.type()));
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

    private static void assign(CodeBlock.Builder b, FieldModel field, String target, Object expr) {
        if (field.accessKind == AccessKind.FIELD) {
            b.addStatement("$L = $L", field.accessOn(target), expr);
        } else {
            b.addStatement("$L.$L($L)", target, field.setterName, expr);
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
