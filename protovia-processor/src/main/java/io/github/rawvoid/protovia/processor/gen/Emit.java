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
import io.github.rawvoid.protovia.processor.model.FieldModel;
import io.github.rawvoid.protovia.processor.model.OneofCaseModel;

import java.util.function.BiConsumer;

import static io.github.rawvoid.protovia.processor.gen.GenTypes.PROTO_EXCEPTION;

/**
 * Shared emission patterns used across size / write / read emitters.
 *
 * @author Rawvoid
 */
final class Emit {

    private Emit() {
    }

    static void loadField(CodeBlock.Builder b, FieldModel field) {
        b.addStatement("$L $L = $L", field.javaTypeName, field.localName, field.readExpr);
    }

    static void writeTag(CodeBlock.Builder b, Object tag) {
        b.addStatement("writer.writeUInt32NoTag($L)", tag);
    }

    static void writeCachedMessage(CodeBlock.Builder b, String codec, String value, String sizeLocal) {
        b.addStatement("int $L = writer.hasCachedSize() ? writer.takeSize() : $L.computeSize($L)",
            sizeLocal, codec, value);
        b.addStatement("writer.writeUInt32NoTag($L)", sizeLocal);
        b.addStatement("$L.writeTo(writer, $L)", codec, value);
    }

    static void nullElementCheck(CodeBlock.Builder b, FieldModel element, String var, String fieldName) {
        if (!element.primitive) {
            b.beginControlFlow("if ($L == null)", var);
            b.addStatement("throw new $T($S)", PROTO_EXCEPTION, "null element in field " + fieldName);
            b.endControlFlow();
        }
    }

    static void oneofCases(
        CodeBlock.Builder b,
        FieldModel field,
        BiConsumer<CodeBlock.Builder, OneofCaseModel> caseBody) {
        b.beginControlFlow("if ($L != null)", field.localName);
        boolean first = true;
        for (OneofCaseModel c : field.oneofCases) {
            String header = (first ? "if" : "else if")
                + " (" + field.localName + " instanceof " + c.typeName + " _c)";
            if (first) {
                b.beginControlFlow(header);
            } else {
                b.nextControlFlow(header);
            }
            first = false;
            caseBody.accept(b, c);
        }
        if (!first) {
            b.endControlFlow();
        }
        b.endControlFlow();
    }
}
