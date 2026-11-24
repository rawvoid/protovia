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
import java.util.ArrayList;
import java.util.List;

import static io.github.rawvoid.protovia.processor.gen.GenTypes.*;
import static io.github.rawvoid.protovia.processor.gen.WireCodegen.*;
import static io.github.rawvoid.protovia.processor.gen.WireTypes.*;
import static io.github.rawvoid.protovia.processor.model.Names.*;

/**
 * Emits {@code computeSize} methods and related field-size logic.
 *
 * @author Rawvoid
 */
final class SizeEmitter {

    private SizeEmitter() {
    }

    static List<MethodSpec> computeSizeMethods(MessageModel model, TypeName msgType) {
        List<MethodSpec> methods = new ArrayList<>();
        methods.add(MethodSpec.methodBuilder("computeSize")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.INT)
            .addParameter(msgType, "value")
            .addStatement("return computeSize(value, $T.NOOP)", SIZE_CACHE)
            .build());
        if (!shouldSplit(model)) {
            methods.add(computeSizeWithCache(model, msgType));
            return methods;
        }
        List<List<FieldModel>> chunks = chunkFields(model.fields);
        CodeBlock.Builder body = CodeBlock.builder();
        body.addStatement("int size = 0");
        for (int i = 0; i < chunks.size(); i++) {
            body.addStatement("size += _computeSize_$L(value, cache)", i);
        }
        if (model.unknown != null) {
            body.addStatement("size += _unknownSize(value)");
        }
        body.addStatement("return size");
        methods.add(MethodSpec.methodBuilder("computeSize")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.INT)
            .addParameter(msgType, "value")
            .addParameter(SIZE_CACHE, "cache")
            .addCode(body.build())
            .build());
        for (int i = 0; i < chunks.size(); i++) {
            methods.add(computeSizeChunk(i, chunks.get(i), msgType));
        }
        if (model.unknown != null) {
            methods.add(unknownSizeMethod(model, msgType));
        }
        return methods;
    }

    private static MethodSpec computeSizeWithCache(MessageModel model, TypeName msgType) {
        CodeBlock.Builder body = CodeBlock.builder();
        body.addStatement("int size = 0");
        for (FieldModel field : model.fields) {
            computeField(body, field);
        }
        emitUnknown(body, model, true);
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

    private static MethodSpec computeSizeChunk(int index, List<FieldModel> fields, TypeName msgType) {
        CodeBlock.Builder body = CodeBlock.builder();
        body.addStatement("int size = 0");
        for (FieldModel field : fields) {
            computeField(body, field);
        }
        body.addStatement("return size");
        return MethodSpec.methodBuilder("_computeSize_" + index)
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .returns(TypeName.INT)
            .addParameter(msgType, "value")
            .addParameter(SIZE_CACHE, "cache")
            .addCode(body.build())
            .build();
    }

    private static MethodSpec unknownSizeMethod(MessageModel model, TypeName msgType) {
        CodeBlock.Builder body = CodeBlock.builder();
        String uLocal = model.unknown.localName();
        body.addStatement("$T $L = $L", UNKNOWN_FIELDS, uLocal, model.unknown.readExpr());
        int maxExcluded = countUnknownEnumExclusions(model);
        if (maxExcluded == 0) {
            body.addStatement("return $L != null ? $L.serializedSize() : 0", uLocal, uLocal);
        } else {
            body.beginControlFlow("if ($L != null && !$L.isEmpty())", uLocal, uLocal);
            body.addStatement("int[] _excl = new int[$L]", maxExcluded);
            body.addStatement("int _exclN = _collectExcludedEnums(value, _excl)");
            body.addStatement("return $L.serializedSizeExcluding(_excl, _exclN)", uLocal);
            body.endControlFlow();
            body.addStatement("return 0");
        }
        return MethodSpec.methodBuilder("_unknownSize")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .returns(TypeName.INT)
            .addParameter(msgType, "value")
            .addCode(body.build())
            .build();
    }

    private static void computeField(CodeBlock.Builder b, FieldModel field) {
        loadField(b, field);
        switch (field.kind) {
            case SCALAR -> computeScalar(b, field, field.localName, field.number, field.optional, field.javaOptional);
            case ENUM -> computeEnum(b, field, field.localName, field.number, field.optional);
            case MESSAGE -> computeMessage(b, field);
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

    private static void computeMessage(CodeBlock.Builder b, FieldModel field) {
        String slot = "_slot_" + field.number;
        String sz = "_sz_" + field.number;
        if (field.javaOptional) {
            String msgVar = "_msg_" + field.number;
            b.beginControlFlow("if ($L != null && $L.isPresent())", field.localName, field.localName);
            b.addStatement("$T $L = $L.get()", optionalValueType(field), msgVar, field.localName);
            b.addStatement("int $L = cache.reserve()", slot);
            b.addStatement("int $L = $L.computeSize($L, cache)", sz, codecInstance(field), msgVar);
            b.addStatement("cache.set($L, $L)", slot, sz);
            b.addStatement("size += $T.message($L, $L)", CODED_SIZE, field.number, sz);
            b.endControlFlow();
        } else {
            b.beginControlFlow("if ($L != null)", field.localName);
            b.addStatement("int $L = cache.reserve()", slot);
            b.addStatement("int $L = $L.computeSize($L, cache)", sz, codecInstance(field), field.localName);
            b.addStatement("cache.set($L, $L)", slot, sz);
            b.addStatement("size += $T.message($L, $L)", CODED_SIZE, field.number, sz);
            b.endControlFlow();
        }
    }

    private static void computeEnum(CodeBlock.Builder b, FieldModel field, String var, int number, boolean optional) {
        String helper = enumNumberOf(field.enumModel);
        if (field.javaOptional) {
            String ev = "_ev_" + number;
            b.beginControlFlow("if ($L != null && $L.isPresent())", var, var);
            b.addStatement("$T $L = $L.get()", enumType(field.enumModel), ev, var);
            if (field.enumModel.unrecognized != null) {
                b.beginControlFlow("if ($L != $L)", ev, enumConstant(field.enumModel, field.enumModel.unrecognized));
                b.addStatement("size += $T.enumValue($L, $L($L))", CODED_SIZE, number, helper, ev);
                b.endControlFlow();
            } else {
                b.addStatement("size += $T.enumValue($L, $L($L))", CODED_SIZE, number, helper, ev);
            }
            b.endControlFlow();
            return;
        }
        if (optional) {
            b.beginControlFlow("if ($L)", enumPresent(field, var));
            b.addStatement("size += $T.enumValue($L, $L($L))", CODED_SIZE, number, helper, var);
            b.endControlFlow();
        } else {
            String numVar = "_num_" + number;
            b.beginControlFlow("if ($L)", enumPresent(field, var));
            b.addStatement("int $L = $L($L)", numVar, helper, var);
            b.beginControlFlow("if ($L != 0)", numVar);
            b.addStatement("size += $T.enumValue($L, $L)", CODED_SIZE, number, numVar);
            b.endControlFlow();
            b.endControlFlow();
        }
    }

    private static void computeRepeated(CodeBlock.Builder b, FieldModel field) {
        b.beginControlFlow("if ($L)", presentRepeated(field));
        if (field.packed && field.packable()) {
            String packed = "_packed_" + field.number;
            b.addStatement("int $L = $L($L)", packed, packedSizeOf(field), field.localName);
            b.addStatement("cache.push($L)", packed);
            b.addStatement("size += $T.lengthDelimited($L, $L)", CODED_SIZE, field.number, packed);
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
        beginMapLoop(b, field, true);
        b.addStatement("size += $T.lengthDelimited($L, $L($L, $L, cache))",
            CODED_SIZE, field.number, mapEntrySizeOf(field), mapEntryKeyExpr(field), mapEntryValueExpr(field));
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
            String slot = "_slot_" + c.number;
            String sz = "_sz_" + c.number;
            b.addStatement("int $L = cache.reserve()", slot);
            b.addStatement("int $L = $L.computeSize(_c, cache)", sz, codecInstance(c.payload));
            b.addStatement("cache.set($L, $L)", slot, sz);
            b.addStatement("size += $T.message($L, $L)", CODED_SIZE, c.number, sz);
        } else if (c.payload.kind == FieldKind.MESSAGE) {
            String slot = "_slot_" + c.number;
            String sz = "_sz_" + c.number;
            b.addStatement("$T _p = $L", javaType(c.payload), value);
            b.beginControlFlow("if (_p != null)");
            b.addStatement("int $L = cache.reserve()", slot);
            b.addStatement("int $L = $L.computeSize(_p, cache)", sz, codecInstance(c.payload));
            b.addStatement("cache.set($L, $L)", slot, sz);
            b.addStatement("size += $T.message($L, $L)", CODED_SIZE, c.number, sz);
            b.endControlFlow();
        } else if (c.payload.kind == FieldKind.ENUM) {
            b.beginControlFlow("if ($L)", enumPresent(c.payload, value));
            b.addStatement("size += $T.enumValue($L, $L($L))",
                CODED_SIZE, c.number, enumNumberOf(c.payload.enumModel), value);
            b.endControlFlow();
        } else {
            String scalarValue = oneofScalarPayload(b, c, value);
            if (c.payload.adapterType != null) {
                String w = oneofWireLocal(c);
                assignToWire(b, c.payload, scalarValue, w);
                b.addStatement("size += $L", sizeCall(c.payload, c.number, w));
            } else {
                b.addStatement("size += $L", sizeCall(c.payload, c.number, scalarValue));
            }
        }
    }
}
