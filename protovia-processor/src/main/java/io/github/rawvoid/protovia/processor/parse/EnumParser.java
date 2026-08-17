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

import io.github.rawvoid.protovia.annotation.ProtoEnumValue;
import io.github.rawvoid.protovia.annotation.ProtoUnrecognized;
import io.github.rawvoid.protovia.processor.model.EnumModel;
import io.github.rawvoid.protovia.processor.model.Names;

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
 */
final class EnumParser {

    private final Diagnostics diag;

    EnumParser(Diagnostics diag) {
        this.diag = diag;
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
            constants.add(new EnumModel.Constant(constant.getSimpleName().toString(), number));
        }
        if (!hasZero) {
            diag.error(type, "proto3 enum " + type.getSimpleName() + " must have a constant with number 0");
        }
        if (diag.failed()) {
            return null;
        }
        String pkg = Names.packageName(type);
        return new EnumModel(type, Names.typeName(type, pkg), constants, unrecognized);
    }
}
