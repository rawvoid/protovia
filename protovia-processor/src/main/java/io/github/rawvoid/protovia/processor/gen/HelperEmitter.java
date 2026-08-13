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
import io.github.rawvoid.protovia.processor.model.EnumModel;
import io.github.rawvoid.protovia.processor.model.FieldKind;
import io.github.rawvoid.protovia.processor.model.FieldModel;
import io.github.rawvoid.protovia.processor.model.MessageModel;
import io.github.rawvoid.protovia.processor.model.Names;
import io.github.rawvoid.protovia.processor.model.OneofCaseModel;
import io.github.rawvoid.protovia.wire.WireType;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static io.github.rawvoid.protovia.processor.model.Names.enumFrom;
import static io.github.rawvoid.protovia.processor.model.Names.enumNumberOf;
import static io.github.rawvoid.protovia.processor.model.Names.mapEntryRead;
import static io.github.rawvoid.protovia.processor.model.Names.mapEntrySizeOf;
import static io.github.rawvoid.protovia.processor.model.Names.mapEntryWrite;
import static io.github.rawvoid.protovia.processor.model.Names.packedSizeOf;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.PROTO_EXCEPTION;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.PROTO_READER;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.PROTO_WRITER;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.SIZE_CACHE;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.boxedType;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.enumConstant;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.enumType;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.javaType;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.mapEntryPartWrite;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.mapEntrySizeAdd;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.mapMissingDefault;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.mapPartAssign;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.packedElements;
import static io.github.rawvoid.protovia.processor.gen.WireTypes.packedFixedWidth;
import static io.github.rawvoid.protovia.processor.gen.WireTypes.unpackedWire;

/**
 * Builds enum / packed-size / map-entry helper methods for a codec.
 *
 * @author Rawvoid
 */
final class HelperEmitter {

    private HelperEmitter() {
    }

    static List<MethodSpec> helpers(MessageModel model) {
        List<MethodSpec> methods = new ArrayList<>();
        collectEnumHelpers(model, methods);
        collectPackedSizeHelpers(model, methods);
        collectMapHelpers(model, methods);
        return methods;
    }

    private static void collectEnumHelpers(MessageModel model, List<MethodSpec> methods) {
        Set<String> seen = new LinkedHashSet<>();
        for (FieldModel field : model.fields) {
            collectEnums(field, seen, methods);
        }
    }

    private static void collectEnums(FieldModel field, Set<String> seen, List<MethodSpec> methods) {
        if (field.kind == FieldKind.ENUM && field.enumModel != null) {
            addEnumHelper(field.enumModel, seen, methods);
        }
        if (field.element != null) {
            collectEnums(field.element, seen, methods);
        }
        if (field.oneofCases != null) {
            for (OneofCaseModel c : field.oneofCases) {
                if (c.payload != null) {
                    collectEnums(c.payload, seen, methods);
                }
            }
        }
        if (field.mapKey != null) {
            collectEnums(field.mapKey, seen, methods);
        }
        if (field.mapValue != null) {
            collectEnums(field.mapValue, seen, methods);
        }
    }

    private static void addEnumHelper(EnumModel model, Set<String> seen, List<MethodSpec> methods) {
        String key = model.type.getQualifiedName().toString();
        if (!seen.add(key)) {
            return;
        }
        methods.add(buildEnumNumberOf(model));
        methods.add(buildEnumFrom(model));
    }

    private static MethodSpec buildEnumNumberOf(EnumModel model) {
        CodeBlock.Builder body = CodeBlock.builder();
        body.add("return switch (value) {\n");
        body.indent();
        for (EnumModel.Constant c : model.constants) {
            body.addStatement("case $L -> $L", c.name(), c.number());
        }
        if (model.unrecognized != null) {
            body.addStatement("case $L -> throw new $T($S)",
                model.unrecognized,
                PROTO_EXCEPTION,
                model.typeName + "." + model.unrecognized + " has no wire number");
        }
        body.unindent();
        body.add("};\n");
        return MethodSpec.methodBuilder(enumNumberOf(model))
            .addModifiers(Modifier.STATIC)
            .returns(TypeName.INT)
            .addParameter(enumType(model), "value")
            .addCode(body.build())
            .build();
    }

    private static MethodSpec buildEnumFrom(EnumModel model) {
        CodeBlock.Builder body = CodeBlock.builder();
        body.add("return switch (number) {\n");
        body.indent();
        for (EnumModel.Constant c : model.constants) {
            body.addStatement("case $L -> $L", c.number(), enumConstant(model, c.name()));
        }
        if (model.unrecognized != null) {
            body.addStatement("default -> $L", enumConstant(model, model.unrecognized));
        } else {
            body.addStatement("default -> null");
        }
        body.unindent();
        body.add("};\n");
        return MethodSpec.methodBuilder(enumFrom(model))
            .addModifiers(Modifier.STATIC)
            .returns(enumType(model))
            .addParameter(TypeName.INT, "number")
            .addCode(body.build())
            .build();
    }

    private static void collectPackedSizeHelpers(MessageModel model, List<MethodSpec> methods) {
        for (FieldModel field : model.fields) {
            if (field.kind != FieldKind.REPEATED || !field.packed || !field.packable()) {
                continue;
            }
            CodeBlock.Builder body = CodeBlock.builder();
            int width = packedFixedWidth(field.element);
            if (width > 0) {
                if (!field.element.primitive) {
                    packedElements(body, field, false);
                }
                String count = field.array ? "values.length" : "values.size()";
                body.addStatement("return $L * $L", count, width);
            } else {
                body.addStatement("int packed = 0");
                packedElements(body, field, false);
                body.addStatement("return packed");
            }
            methods.add(MethodSpec.methodBuilder(packedSizeOf(field))
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(TypeName.INT)
                .addParameter(javaType(field), "values")
                .addCode(body.build())
                .build());
        }
    }

    private static void collectMapHelpers(MessageModel model, List<MethodSpec> methods) {
        for (FieldModel field : model.fields) {
            if (field.kind != FieldKind.MAP) {
                continue;
            }
            methods.add(mapEntrySize(field));
            methods.add(mapEntryWriteMethod(field));
            methods.add(mapEntryReadMethod(field));
        }
    }

    private static MethodSpec mapEntrySize(FieldModel field) {
        CodeBlock.Builder body = CodeBlock.builder();
        body.beginControlFlow("if (k == null || v == null)");
        body.addStatement("throw new $T($S)", PROTO_EXCEPTION, "map entry for field " + field.name + " cannot contain null");
        body.endControlFlow();
        body.addStatement("int entrySlot = cache.reserve()");
        body.addStatement("int entrySize = 0");
        mapEntrySizeAdd(body, field.mapKey, "k", 1, "entrySize");
        mapEntrySizeAdd(body, field.mapValue, "v", 2, "entrySize");
        body.addStatement("cache.set(entrySlot, entrySize)");
        body.addStatement("return entrySize");
        return MethodSpec.methodBuilder(mapEntrySizeOf(field))
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .returns(TypeName.INT)
            .addParameter(boxedType(field.mapKey), "k")
            .addParameter(boxedType(field.mapValue), "v")
            .addParameter(SIZE_CACHE, "cache")
            .addCode(body.build())
            .build();
    }

    private static MethodSpec mapEntryWriteMethod(FieldModel field) {
        CodeBlock.Builder body = CodeBlock.builder();
        body.beginControlFlow("if (k == null || v == null)");
        body.addStatement("throw new $T($S)", PROTO_EXCEPTION, "map entry for field " + field.name + " cannot contain null");
        body.endControlFlow();
        body.addStatement("int entrySize = writer.hasCachedSize() ? writer.takeSize() : $L(k, v, $T.NOOP)",
            mapEntrySizeOf(field), SIZE_CACHE);
        body.addStatement("writer.writeUInt32NoTag($L)", Names.tagConstant(field.number));
        body.addStatement("writer.writeUInt32NoTag(entrySize)");
        mapEntryPartWrite(body, field.mapKey, "k", 1);
        mapEntryPartWrite(body, field.mapValue, "v", 2);
        return MethodSpec.methodBuilder(mapEntryWrite(field))
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .returns(TypeName.VOID)
            .addParameter(PROTO_WRITER, "writer")
            .addParameter(boxedType(field.mapKey), "k")
            .addParameter(boxedType(field.mapValue), "v")
            .addCode(body.build())
            .build();
    }

    private static MethodSpec mapEntryReadMethod(FieldModel field) {
        CodeBlock.Builder body = CodeBlock.builder();
        body.addStatement("$T k = $L", boxedType(field.mapKey), mapMissingDefault(field.mapKey));
        if (field.mapValue.kind == FieldKind.MESSAGE) {
            body.addStatement("$T v = null", boxedType(field.mapValue));
        } else {
            body.addStatement("$T v = $L", boxedType(field.mapValue), mapMissingDefault(field.mapValue));
        }
        body.addStatement("int oldLimit = reader.beginPacked()");
        body.addStatement("int tag");
        body.beginControlFlow("while ((tag = reader.readTag()) != 0)");
        body.beginControlFlow("switch (tag)");
        body.beginControlFlow("case $L ->", WireType.tag(1, unpackedWire(field.mapKey)));
        mapPartAssign(body, field.mapKey, "k");
        body.endControlFlow();
        body.beginControlFlow("case $L ->", WireType.tag(2, unpackedWire(field.mapValue)));
        mapPartAssign(body, field.mapValue, "v");
        body.endControlFlow();
        body.addStatement("default -> reader.skipField()");
        body.endControlFlow();
        body.endControlFlow();
        body.addStatement("reader.popLimit(oldLimit)");
        if (field.mapValue.kind == FieldKind.MESSAGE) {
            body.beginControlFlow("if (v == null)");
            body.addStatement("v = $L", mapMissingDefault(field.mapValue));
            body.endControlFlow();
        }
        body.addStatement("target.put(k, v)");
        return MethodSpec.methodBuilder(mapEntryRead(field))
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .returns(TypeName.VOID)
            .addParameter(PROTO_READER, "reader")
            .addParameter(javaType(field), "target")
            .addCode(body.build())
            .build();
    }
}
