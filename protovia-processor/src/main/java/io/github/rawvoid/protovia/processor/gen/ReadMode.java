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

import io.github.rawvoid.protovia.processor.model.AccessKind;
import io.github.rawvoid.protovia.processor.model.FieldModel;

/**
 * Addressing mode for field-read code generation across POJO / immutable and split / unsplit paths.
 *
 * @author Rawvoid
 */
enum ReadMode {
    POJO,
    POJO_SPLIT,
    LOCALS,
    LOCALS_SPLIT;

    boolean usesLocals() {
        return this == LOCALS || this == LOCALS_SPLIT;
    }

    boolean usesColCache() {
        return this == POJO;
    }

    String localRef(FieldModel field) {
        return this == LOCALS_SPLIT ? "_s." + field.localName : field.localName;
    }

    String arrayBuilderRef(FieldModel field) {
        String var = arrayBuilderVar(field);
        return switch (this) {
            case POJO, LOCALS -> var;
            case POJO_SPLIT -> "_arr." + var;
            case LOCALS_SPLIT -> "_s." + var;
        };
    }

    String unknownBuilderRef() {
        return this == LOCALS_SPLIT ? "_s._ub" : "_ub";
    }

    String repeatedVar(FieldModel field) {
        if (field.array) {
            return arrayBuilderRef(field);
        }
        return switch (this) {
            case POJO -> collectionCacheVar(field);
            case POJO_SPLIT -> field.accessKind == AccessKind.FIELD ? field.accessOn("msg") : field.localName;
            case LOCALS -> field.localName;
            case LOCALS_SPLIT -> "_s." + field.localName;
        };
    }

    String mapVar(FieldModel field) {
        return switch (this) {
            case POJO -> collectionCacheVar(field);
            case POJO_SPLIT -> field.accessKind == AccessKind.FIELD ? field.accessOn("msg") : field.localName;
            case LOCALS -> field.localName;
            case LOCALS_SPLIT -> "_s." + field.localName;
        };
    }

    static String collectionCacheVar(FieldModel field) {
        return "_col_" + field.number;
    }

    static String arrayBuilderVar(FieldModel field) {
        return "_builder_" + field.number;
    }
}
