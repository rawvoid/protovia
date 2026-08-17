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
import io.github.rawvoid.protovia.processor.model.OneofCaseModel;

import javax.lang.model.element.Modifier;
import java.util.List;

import static io.github.rawvoid.protovia.processor.model.Names.enumNumberOf;
import static io.github.rawvoid.protovia.processor.model.Names.mapEntrySizeOf;
import static io.github.rawvoid.protovia.processor.model.Names.packedSizeOf;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.CODED_SIZE;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.MAP;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.SIZE_CACHE;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.UNKNOWN_FIELDS;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.boxedType;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.codecInstance;
import static io.github.rawvoid.protovia.processor.gen.GenTypes.javaType;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.assignToWire;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.loadField;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.nullElementCheck;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.oneofCases;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.oneofWireLocal;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.sizeCall;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.wireLocal;
import static io.github.rawvoid.protovia.processor.gen.WireTypes.enumPresent;
import static io.github.rawvoid.protovia.processor.gen.WireTypes.presentCondition;
import static io.github.rawvoid.protovia.processor.gen.WireTypes.presentRepeated;

/**
 * Emits {@code computeSize} methods and related field-size logic.
 *
 * @author Rawvoid
 */
final class SizeEmitter {

    private SizeEmitter() {
    }

    static List<MethodSpec> computeSizeMethods(MessageModel model, TypeName msgType) {
        MethodSpec simple = MethodSpec.methodBuilder("computeSize")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.INT)
            .addParameter(msgType, "value")
            .addStatement("return computeSize(value, $T.NOOP)", SIZE_CACHE)
            .build();
        return List.of(simple, computeSizeWithCache(model, msgType));
    }

    private static MethodSpec computeSizeWithCache(MessageModel model, TypeName msgType) {
        CodeBlock.Builder body = CodeBlock.builder();
        body.addStatement("int size = 0");
        for (FieldModel field : model.fields) {
            computeField(body, field);
        }
        if (model.unknown != null) {
            body.addStatement("$T $L = $L", UNKNOWN_FIELDS, model.unknown.localName(), model.unknown.readExpr());
            body.beginControlFlow("if ($L != null)", model.unknown.localName());
            body.addStatement("size += $L.serializedSize()", model.unknown.localName());
            body.endControlFlow();
        }
        body.addStatement("return size");
        return MethodSpec.methodBuilder("computeSize")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.INT)
            .addParameter(msgType, "value")
            .addParameter(SIZE_CACHE, "cache")
            .addCode(body.build())
            .build();
    }

    private static void computeField(CodeBlock.Builder b, FieldModel field) {
        loadField(b, field);
        switch (field.kind) {
            case SCALAR -> computeScalar(b, field, field.localName, field.number, field.optional, field.javaOptional);
            case ENUM -> computeEnum(b, field, field.localName, field.number, field.optional);
            case MESSAGE -> {
                b.beginControlFlow("if ($L != null)", field.localName);
                b.addStatement("int $LSlot = cache.reserve()", field.localName);
                b.addStatement("int $LSize = $L.computeSize($L, cache)",
                    field.localName, codecInstance(field), field.localName);
                b.addStatement("cache.set($LSlot, $LSize)", field.localName, field.localName);
                b.addStatement("size += $T.message($L, $LSize)", CODED_SIZE, field.number, field.localName);
                b.endControlFlow();
            }
            case REPEATED -> computeRepeated(b, field);
            case MAP -> computeMap(b, field);
            case ONEOF -> computeOneof(b, field);
        }
    }

    private static void computeScalar(
        CodeBlock.Builder b, FieldModel field, String var, int number, boolean optional, boolean javaOptional) {
        String valueExpr = javaOptional ? var + ".get()" : var;
        b.beginControlFlow("if ($L)", presentCondition(field, var, optional, javaOptional));
        if (field.adapterType != null) {
            assignToWire(b, field, valueExpr);
            b.addStatement("size += $L", sizeCall(field, number, wireLocal(field)));
        } else {
            b.addStatement("size += $L", sizeCall(field, number, valueExpr));
        }
        b.endControlFlow();
    }

    private static void computeEnum(CodeBlock.Builder b, FieldModel field, String var, int number, boolean optional) {
        String helper = enumNumberOf(field.enumModel);
        if (optional) {
            b.beginControlFlow("if ($L)", enumPresent(field, var));
            b.addStatement("size += $T.enumValue($L, $L($L))", CODED_SIZE, number, helper, var);
            b.endControlFlow();
        } else {
            b.beginControlFlow("if ($L)", enumPresent(field, var));
            b.addStatement("int $LNumber = $L($L)", var, helper, var);
            b.beginControlFlow("if ($LNumber != 0)", var);
            b.addStatement("size += $T.enumValue($L, $LNumber)", CODED_SIZE, number, var);
            b.endControlFlow();
            b.endControlFlow();
        }
    }

    private static void computeRepeated(CodeBlock.Builder b, FieldModel field) {
        b.beginControlFlow("if ($L)", presentRepeated(field));
        if (field.packed && field.packable()) {
            b.addStatement("int $LPacked = $L($L)", field.localName, packedSizeOf(field), field.localName);
            b.addStatement("cache.push($LPacked)", field.localName);
            b.addStatement("size += $T.lengthDelimited($L, $LPacked)", CODED_SIZE, field.number, field.localName);
        } else {
            b.beginControlFlow("for ($T item : $L)", javaType(field.element), field.localName);
            nullElementCheck(b, field.element, "item", field.name);
            if (field.element.kind == FieldKind.ENUM) {
                b.addStatement("size += $T.enumValue($L, $L(item))",
                    CODED_SIZE, field.number, enumNumberOf(field.element.enumModel));
            } else if (field.element.kind == FieldKind.MESSAGE) {
                b.addStatement("int itemSlot = cache.reserve()");
                b.addStatement("int itemSize = $L.computeSize(item, cache)", codecInstance(field.element));
                b.addStatement("cache.set(itemSlot, itemSize)");
                b.addStatement("size += $T.message($L, itemSize)", CODED_SIZE, field.number);
            } else {
                String value = WireCodegen.adaptedValue(b, field.element, "item", "itemWire");
                b.addStatement("size += $L", sizeCall(field.element, field.number, value));
            }
            b.endControlFlow();
        }
        b.endControlFlow();
    }

    private static void computeMap(CodeBlock.Builder b, FieldModel field) {
        b.beginControlFlow("if ($L != null && !$L.isEmpty())", field.localName, field.localName);
        b.beginControlFlow("for ($T.Entry<$T, $T> e : $L.entrySet())",
            MAP, boxedType(field.mapKey), boxedType(field.mapValue), field.localName);
        b.addStatement("size += $T.lengthDelimited($L, $L(e.getKey(), e.getValue(), cache))",
            CODED_SIZE, field.number, mapEntrySizeOf(field));
        b.endControlFlow();
        b.endControlFlow();
    }

    private static void computeOneof(CodeBlock.Builder b, FieldModel field) {
        oneofCases(b, field, SizeEmitter::oneofCaseSize);
    }

    private static void oneofCaseSize(CodeBlock.Builder b, OneofCaseModel c) {
        String value = c.accessor == null ? "_c" : "_c." + c.accessor;
        if (c.empty()) {
            b.addStatement("size += $T.lengthDelimited($L, 0)", CODED_SIZE, c.number);
        } else if (c.selfMessage) {
            b.addStatement("int $L_slot = cache.reserve()", c.tagConstant);
            b.addStatement("int $L_sz = $L.computeSize(_c, cache)", c.tagConstant, codecInstance(c.payload));
            b.addStatement("cache.set($L_slot, $L_sz)", c.tagConstant, c.tagConstant);
            b.addStatement("size += $T.message($L, $L_sz)", CODED_SIZE, c.number, c.tagConstant);
        } else if (c.payload.kind == FieldKind.MESSAGE) {
            b.addStatement("$T _p = $L", javaType(c.payload), value);
            b.beginControlFlow("if (_p != null)");
            b.addStatement("int $L_slot = cache.reserve()", c.tagConstant);
            b.addStatement("int $L_sz = $L.computeSize(_p, cache)", c.tagConstant, codecInstance(c.payload));
            b.addStatement("cache.set($L_slot, $L_sz)", c.tagConstant, c.tagConstant);
            b.addStatement("size += $T.message($L, $L_sz)", CODED_SIZE, c.number, c.tagConstant);
            b.endControlFlow();
        } else if (c.payload.kind == FieldKind.ENUM) {
            b.beginControlFlow("if ($L)", enumPresent(c.payload, value));
            b.addStatement("size += $T.enumValue($L, $L($L))",
                CODED_SIZE, c.number, enumNumberOf(c.payload.enumModel), value);
            b.endControlFlow();
        } else if (c.payload.adapterType != null) {
            String w = oneofWireLocal(c);
            assignToWire(b, c.payload, value, w);
            b.addStatement("size += $L", sizeCall(c.payload, c.number, w));
        } else {
            b.addStatement("size += $L", sizeCall(c.payload, c.number, value));
        }
    }
}
