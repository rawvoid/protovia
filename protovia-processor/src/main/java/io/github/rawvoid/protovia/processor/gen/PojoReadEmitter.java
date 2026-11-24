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
import io.github.rawvoid.protovia.processor.model.AccessKind;
import io.github.rawvoid.protovia.processor.model.FieldModel;
import io.github.rawvoid.protovia.processor.model.MessageModel;

import javax.lang.model.element.Modifier;
import java.util.List;

import static io.github.rawvoid.protovia.processor.gen.FieldReadEmitter.assign;
import static io.github.rawvoid.protovia.processor.gen.FieldReadEmitter.readCases;
import static io.github.rawvoid.protovia.processor.gen.FieldReadEmitter.readLoop;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.PROTO_READER;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.UNKNOWN_FIELDS;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.UNKNOWN_FIELDS_BUILDER;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.javaType;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.newImpl;
import static io.github.rawvoid.protovia.processor.gen.ReadEmitter.arrayFields;
import static io.github.rawvoid.protovia.processor.gen.ReadEmitter.collectionFields;
import static io.github.rawvoid.protovia.processor.gen.ReadEmitter.finalizeArrayBuilders;
import static io.github.rawvoid.protovia.processor.gen.ReadEmitter.initArrayBuilders;
import static io.github.rawvoid.protovia.processor.gen.ReadEmitter.isCollection;
import static io.github.rawvoid.protovia.processor.gen.ReadEmitter.mergeFromSpec;
import static io.github.rawvoid.protovia.processor.gen.ReadMode.arrayBuilderVar;
import static io.github.rawvoid.protovia.processor.gen.ReadMode.collectionCacheVar;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.arrayBuilderType;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.chunkFields;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.chunkList;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.toArray;

/**
 * Emits {@code mergeFrom} and split helpers for mutable POJO messages.
 *
 * @author Rawvoid
 */
final class PojoReadEmitter {

    private PojoReadEmitter() {
    }

    static void emit(
        TypeSpec.Builder type, ClassName codecType, MessageModel model, TypeName msgType, boolean split) {
        if (split) {
            emitPojoSplit(type, codecType, model, msgType);
        } else {
            type.addMethod(mergeFromPojo(model, msgType));
        }
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
        fillAbsentCollections(b, model);
        if (model.unknown != null) {
            unknownStore(b, model);
        }
        b.addStatement("return msg");
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

    private static void initCollectionCaches(CodeBlock.Builder b, MessageModel model) {
        for (FieldModel field : model.fields) {
            if (isCollection(field)) {
                b.addStatement("$T $L = null", javaType(field), collectionCacheVar(field));
            }
        }
    }

    /**
     * A repeated / map field absent from the wire has no presence in proto3: it is the same as an empty one, and
     * the official implementations hand out empty collections rather than null. A mutable target starts from the
     * model's own (possibly null) fields, so fill them once the read loop is done.
     */
    private static void fillAbsentCollections(CodeBlock.Builder b, MessageModel model) {
        for (FieldModel field : model.fields) {
            if (isCollection(field)) {
                b.beginControlFlow("if ($L == null && $L == null)",
                    collectionCacheVar(field), field.accessOn("msg"));
                assign(b, field, "msg", newImpl(field));
                b.endControlFlow();
            }
        }
    }

    private static void unknownInit(CodeBlock.Builder b, MessageModel model) {
        b.addStatement("$T _ub = $T.newBuilder($L)",
            UNKNOWN_FIELDS_BUILDER, UNKNOWN_FIELDS, model.unknown.accessOn("msg"));
    }

    private static void unknownStore(CodeBlock.Builder b, MessageModel model) {
        MessageModel.UnknownField u = model.unknown;
        if (u.accessKind() == AccessKind.FIELD) {
            b.addStatement("$L = _ub.build()", u.accessOn("msg"));
        } else {
            b.addStatement("msg.$L(_ub.build())", u.setterName());
        }
    }
}
