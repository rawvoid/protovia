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
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import io.github.rawvoid.protovia.processor.model.FieldKind;
import io.github.rawvoid.protovia.processor.model.FieldModel;
import io.github.rawvoid.protovia.processor.model.MessageModel;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;

import static io.github.rawvoid.protovia.processor.gen.GenTypes.PROTO_READER;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.SPLIT_THRESHOLD;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.shouldSplit;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.toArray;

/**
 * Emits {@code readFrom} / {@code mergeFrom} and delegates to POJO or immutable read emitters.
 *
 * @author Rawvoid
 */
final class ReadEmitter {

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
            ImmutableReadEmitter.emit(type, codecType, model, msgType, split);
        } else {
            PojoReadEmitter.emit(type, codecType, model, msgType, split);
        }
    }

    private static boolean shouldSplitRead(MessageModel model) {
        return shouldSplit(model) || (model.record && model.recordComponents.size() > SPLIT_THRESHOLD);
    }

    static MethodSpec mergeFromSpec(TypeName msgType, CodeBlock body) {
        return MethodSpec.methodBuilder("mergeFrom")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(msgType)
            .addParameter(PROTO_READER, "reader")
            .addParameter(msgType, "existing")
            .addCode(body)
            .build();
    }

    static boolean isCollection(FieldModel field) {
        return field.kind == FieldKind.REPEATED && !field.array || field.kind == FieldKind.MAP;
    }

    static List<FieldModel> arrayFields(MessageModel model) {
        List<FieldModel> result = new ArrayList<>();
        for (FieldModel field : model.fields) {
            if (field.array) {
                result.add(field);
            }
        }
        return result;
    }

    static List<FieldModel> collectionFields(MessageModel model) {
        List<FieldModel> result = new ArrayList<>();
        for (FieldModel field : model.fields) {
            if (isCollection(field)) {
                result.add(field);
            }
        }
        return result;
    }

    static void initArrayBuilders(CodeBlock.Builder b, MessageModel model) {
        for (FieldModel field : model.fields) {
            if (field.array) {
                b.addStatement("$T $L = null", WireCodegen.arrayBuilderType(field), ReadMode.arrayBuilderVar(field));
            }
        }
    }

    static void finalizeArrayBuilders(
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
                FieldReadEmitter.assign(b, field, assignTarget, toArray(field, builderVar));
            }
            b.endControlFlow();
        }
    }
}
