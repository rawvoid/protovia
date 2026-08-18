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

import io.github.rawvoid.protovia.ProtoType;
import io.github.rawvoid.protovia.processor.model.AccessKind;
import io.github.rawvoid.protovia.processor.model.FieldModel;
import io.github.rawvoid.protovia.processor.model.Names;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/**
 * Inputs shared by singular / repeated / map / adapter field resolution.
 *
 * @author Rawvoid
 */
final class FieldRequest {

    final Element origin;
    final String name;
    final TypeMirror type;
    final ProtoType declared;
    final boolean optional;
    final boolean packed;
    final AccessKind accessKind;
    final String readExpr;
    final String setter;
    final String fieldName;
    final String pkg;
    final boolean javaOptional;
    final TypeMirror declaredJavaType;
    final TypeElement fieldAdapter;
    final int number;
    final AdapterSite site;

    FieldRequest(
        Element origin,
        String name,
        TypeMirror type,
        ProtoType declared,
        boolean optional,
        boolean packed,
        AccessKind accessKind,
        String readExpr,
        String setter,
        String fieldName,
        String pkg,
        boolean javaOptional,
        TypeMirror declaredJavaType,
        TypeElement fieldAdapter,
        int number,
        AdapterSite site) {
        this.origin = origin;
        this.name = name;
        this.type = type;
        this.declared = declared;
        this.optional = optional;
        this.packed = packed;
        this.accessKind = accessKind;
        this.readExpr = readExpr;
        this.setter = setter;
        this.fieldName = fieldName;
        this.pkg = pkg;
        this.javaOptional = javaOptional;
        this.declaredJavaType = declaredJavaType;
        this.fieldAdapter = fieldAdapter;
        this.number = number;
        this.site = site;
    }

    FieldModel.Builder baseBuilder() {
        return FieldModel.builder()
            .number(number)
            .name(name)
            .localName(Names.safeLocal(name))
            .optional(optional)
            .packed(packed)
            .javaOptional(javaOptional)
            .accessKind(accessKind)
            .readExpr(readExpr)
            .setterName(setter)
            .fieldName(fieldName)
            .origin(origin);
    }
}
