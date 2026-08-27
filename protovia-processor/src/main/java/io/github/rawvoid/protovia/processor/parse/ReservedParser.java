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

import io.github.rawvoid.protovia.annotation.ProtoReserved;
import io.github.rawvoid.protovia.processor.model.ProtoIdent;
import io.github.rawvoid.protovia.processor.model.Reserved;
import io.github.rawvoid.protovia.wire.WireType;

import javax.lang.model.element.TypeElement;

/**
 * Reads {@link ProtoReserved} on a message, enum, or mixin superclass.
 *
 * @author Rawvoid
 */
final class ReservedParser {

    enum Scope {
        MESSAGE,
        ENUM
    }

    private final Diagnostics diag;

    ReservedParser(Diagnostics diag) {
        this.diag = diag;
    }

    Reserved parse(TypeElement type, Scope scope) {
        ProtoReserved[] annotations = type.getAnnotationsByType(ProtoReserved.class);
        if (annotations.length == 0) {
            return Reserved.EMPTY;
        }
        Reserved.Builder builder = Reserved.builder();
        for (ProtoReserved annotation : annotations) {
            absorb(type, annotation, scope, builder);
        }
        return builder.build();
    }

    private void absorb(TypeElement type, ProtoReserved annotation, Scope scope, Reserved.Builder builder) {
        for (int number : annotation.numbers()) {
            if (scope == Scope.MESSAGE && !WireType.isValidFieldNumber(number)) {
                diag.error(type, "invalid reserved field number " + number);
                continue;
            }
            builder.addNumber(number);
        }
        for (ProtoReserved.Range range : annotation.ranges()) {
            int from = range.from();
            int to = range.to();
            if (from > to) {
                diag.error(type, "invalid reserved range " + from + " to " + to);
                continue;
            }
            if (scope == Scope.MESSAGE && !WireType.isValidFieldNumberRange(from, to)) {
                diag.error(type, "invalid reserved range " + from + " to " + to);
                continue;
            }
            builder.addRange(from, to);
        }
        for (String name : annotation.names()) {
            if (!ProtoIdent.isIdentifier(name)) {
                diag.error(type, "reserved name '" + name + "' is not a proto identifier");
                continue;
            }
            builder.addName(name);
        }
    }
}
