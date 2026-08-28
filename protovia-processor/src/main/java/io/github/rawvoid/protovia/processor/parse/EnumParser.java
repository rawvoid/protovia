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

package io.github.rawvoid.protovia.processor.parse;

import io.github.rawvoid.protovia.annotation.ProtoEnum;
import io.github.rawvoid.protovia.annotation.ProtoEnumValue;
import io.github.rawvoid.protovia.annotation.ProtoUnrecognized;
import io.github.rawvoid.protovia.processor.model.EnumModel;
import io.github.rawvoid.protovia.processor.model.Names;
import io.github.rawvoid.protovia.processor.model.ProtoIdent;
import io.github.rawvoid.protovia.processor.model.Reserved;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates {@code @ProtoEnum} types.
 *
 * @author Rawvoid
 */
final class EnumParser {

    private final Diagnostics diag;
    private final ReservedParser reserved;

    EnumParser(Diagnostics diag, ReservedParser reserved) {
        this.diag = diag;
        this.reserved = reserved;
    }

    /**
     * @return model, or {@code null} if the enum is invalid
     */
    EnumModel parse(TypeElement type) {
        diag.push();
        try {
            return doParse(type);
        } finally {
            diag.popAndMerge();
        }
    }

    private EnumModel doParse(TypeElement type) {
        if (type.getKind() != ElementKind.ENUM) {
            diag.error(type, "@ProtoEnum is only valid on enum types");
            return null;
        }
        ProtoEnum meta = type.getAnnotation(ProtoEnum.class);
        String protoPackage = meta == null || meta.packageName().isBlank() ? "" : meta.packageName().trim();
        String protoEnumName = meta == null || meta.name().isBlank()
            ? type.getSimpleName().toString()
            : meta.name().trim();

        Reserved reservedNumbers = reserved.parse(type, ReservedParser.Scope.ENUM);
        List<EnumModel.Constant> constants = new ArrayList<>();
        Set<Integer> numbers = new HashSet<>();
        boolean hasZero = false;
        String unrecognized = null;
        for (VariableElement constant : ElementFilter.fieldsIn(type.getEnclosedElements())) {
            if (constant.getKind() != ElementKind.ENUM_CONSTANT) {
                continue;
            }
            boolean sentinel = constant.getAnnotation(ProtoUnrecognized.class) != null;
            ProtoEnumValue value = constant.getAnnotation(ProtoEnumValue.class);
            if (sentinel) {
                if (value != null) {
                    diag.error(constant, "@ProtoUnrecognized cannot be combined with @ProtoEnumValue");
                    continue;
                }
                if (unrecognized != null) {
                    diag.error(constant, "at most one @ProtoUnrecognized per enum");
                    continue;
                }
                unrecognized = constant.getSimpleName().toString();
                continue;
            }
            if (value == null) {
                diag.error(constant, "enum constant " + constant.getSimpleName() + " must have @ProtoEnumValue");
                continue;
            }
            int number = value.value();
            if (!numbers.add(number)) {
                diag.error(constant, "duplicate enum number " + number);
            }
            if (number == 0) {
                hasZero = true;
            }
            String constantName = constant.getSimpleName().toString();
            String protoConstantName = ProtoIdent.enumConstantName(protoEnumName, constantName);
            if (reservedNumbers.containsNumber(number)) {
                diag.error(constant, "enum number " + number + " is reserved");
            }
            if (reservedNumbers.containsName(protoConstantName)) {
                diag.error(constant, "proto name '" + protoConstantName + "' is reserved");
            }
            constants.add(new EnumModel.Constant(constantName, number));
        }
        if (!hasZero) {
            diag.error(type, "proto3 enum " + type.getSimpleName() + " must have a constant with number 0");
        }
        ExportNames.requirePackage(diag, type, protoPackage);
        ExportNames.require(diag, type, protoEnumName);
        if (diag.failed()) {
            return null;
        }
        String pkg = Names.packageName(type);
        return EnumModel.builder()
            .type(type)
            .typeName(Names.typeName(type, pkg))
            .protoPackage(protoPackage)
            .protoEnumName(protoEnumName)
            .constants(constants)
            .unrecognized(unrecognized)
            .reserved(reservedNumbers)
            .build();
    }
}
