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

import io.github.rawvoid.protovia.annotation.ProtoField;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.type.TypeMirror;

/**
 * One record component, POJO field, or JavaBean getter that may carry proto annotations.
 *
 * @param access           {@code null} when access could not be resolved (error already reported)
 * @param fieldOnSameElement {@code @ProtoField} on the same element as unknown/oneof (for combine checks)
 * @param protoField       effective {@code @ProtoField}, including a record accessor
 */
record Member(
    Element origin,
    String name,
    TypeMirror type,
    Access access,
    boolean recordComponent,
    boolean protoUnknown,
    boolean protoOneof,
    boolean fieldOnSameElement,
    ProtoField protoField) {

    boolean fromField() {
        return origin.getKind() == ElementKind.FIELD;
    }

    boolean fromMethod() {
        return origin.getKind() == ElementKind.METHOD;
    }
}
