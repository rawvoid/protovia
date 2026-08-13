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
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import io.github.rawvoid.protovia.processor.model.FieldKind;
import io.github.rawvoid.protovia.processor.model.FieldModel;
import io.github.rawvoid.protovia.processor.model.MessageModel;
import io.github.rawvoid.protovia.processor.model.Names;
import io.github.rawvoid.protovia.processor.model.OneofCaseModel;
import io.github.rawvoid.protovia.wire.WireType;

import javax.lang.model.element.Modifier;

import static io.github.rawvoid.protovia.processor.gen.GenTypes.PROTO_CODEC;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.messageType;
import static io.github.rawvoid.protovia.processor.gen.WireTypes.oneofWire;
import static io.github.rawvoid.protovia.processor.gen.WireTypes.unpackedWire;

/**
 * Emits a {@code XxxProtoCodec} source file from a {@link MessageModel}.
 *
 * @author Rawvoid
 */
public final class CodecGenerator {

    public String generate(MessageModel model) {
        TypeName msgType = messageType(model);
        ClassName codecType = model.packageName.isEmpty()
            ? ClassName.get("", model.codecSimpleName)
            : ClassName.get(model.packageName, model.codecSimpleName);

        TypeSpec.Builder type = TypeSpec.classBuilder(model.codecSimpleName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(ParameterizedTypeName.get(PROTO_CODEC, msgType));

        type.addField(FieldSpec.builder(codecType, "INSTANCE")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .initializer("new $T()", codecType)
            .build());
        type.addMethod(MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PRIVATE)
            .build());

        emitTags(type, model);

        type.addMethod(MethodSpec.methodBuilder("type")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(ClassName.get(Class.class), msgType))
            .addStatement("return $T.class", msgType)
            .build());
        type.addMethod(MethodSpec.methodBuilder("protoFullName")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(ClassName.get(String.class))
            .addStatement("return $S", model.protoFullName())
            .build());
        type.addMethod(MethodSpec.methodBuilder("cachesNestedSizes")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.BOOLEAN)
            .addStatement("return true")
            .build());

        SizeEmitter.computeSizeMethods(model, msgType).forEach(type::addMethod);
        type.addMethod(WriteEmitter.writeTo(model, msgType));
        ReadEmitter.readMethods(model, msgType).forEach(type::addMethod);
        HelperEmitter.helpers(model).forEach(type::addMethod);

        JavaFile file = JavaFile.builder(model.packageName, type.build())
            .skipJavaLangImports(true)
            .indent("    ")
            .build();
        return compactTagConstants(file.toString());
    }

    /**
     * JavaPoet inserts a blank line after every field. TAG_* constants are
     * denser without it.
     */
    private static String compactTagConstants(String source) {
        return source.replaceAll(
            "(private static final int TAG_\\w+ = \\d+;)\n\n(?=    private static final int TAG_)",
            "$1\n");
    }

    private static void emitTags(TypeSpec.Builder type, MessageModel model) {
        for (FieldModel field : model.fields) {
            if (field.kind == FieldKind.ONEOF) {
                for (OneofCaseModel c : field.oneofCases) {
                    type.addField(tagField(c.tagConstant, WireType.tag(c.number, oneofWire(c))));
                }
                continue;
            }
            String tag = Names.tagConstant(field.number);
            type.addField(tagField(tag, WireType.tag(field.number, unpackedWire(field))));
            if (field.kind == FieldKind.REPEATED && field.packable()) {
                type.addField(tagField(tag + "_PACKED", WireType.tag(field.number, WireType.LEN)));
            }
        }
    }

    private static FieldSpec tagField(String name, int value) {
        return FieldSpec.builder(TypeName.INT, name)
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer("$L", value)
            .build();
    }
}
