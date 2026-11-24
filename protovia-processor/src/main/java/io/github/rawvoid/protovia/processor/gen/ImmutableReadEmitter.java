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
import io.github.rawvoid.protovia.processor.model.FieldKind;
import io.github.rawvoid.protovia.processor.model.FieldModel;
import io.github.rawvoid.protovia.processor.model.Instantiation;
import io.github.rawvoid.protovia.processor.model.MessageModel;
import io.github.rawvoid.protovia.processor.model.Names;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static io.github.rawvoid.protovia.processor.gen.FieldReadEmitter.readCases;
import static io.github.rawvoid.protovia.processor.gen.FieldReadEmitter.readLoop;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.PROTO_READER;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.UNKNOWN_FIELDS;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.UNKNOWN_FIELDS_BUILDER;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.defaultValue;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.javaType;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.messageType;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.newImpl;
import static io.github.rawvoid.protovia.processor.gen.ReadEmitter.arrayFields;
import static io.github.rawvoid.protovia.processor.gen.ReadEmitter.finalizeArrayBuilders;
import static io.github.rawvoid.protovia.processor.gen.ReadEmitter.initArrayBuilders;
import static io.github.rawvoid.protovia.processor.gen.ReadEmitter.isCollection;
import static io.github.rawvoid.protovia.processor.gen.ReadEmitter.mergeFromSpec;
import static io.github.rawvoid.protovia.processor.gen.ReadMode.arrayBuilderVar;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.arrayBuilderType;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.chunkFields;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.chunkList;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.toArray;

/**
 * Emits {@code mergeFrom} and split helpers for records, {@code @ProtoCreator}, and {@code @ProtoBuilder} messages.
 *
 * @author Rawvoid
 */
final class ImmutableReadEmitter {

    private ImmutableReadEmitter() {
    }

    static void emit(
        TypeSpec.Builder type, ClassName codecType, MessageModel model, TypeName msgType, boolean split) {
        if (split) {
            emitLocalsSplit(type, codecType, model, msgType);
        } else {
            type.addMethod(mergeFromLocals(model, msgType));
        }
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

    private static void unknownLocalInit(CodeBlock.Builder b, MessageModel model) {
        b.addStatement("$T _ub = $T.newBuilder(existing != null ? $L : null)",
            UNKNOWN_FIELDS_BUILDER, UNKNOWN_FIELDS, model.unknown.accessOn("existing"));
    }
}
