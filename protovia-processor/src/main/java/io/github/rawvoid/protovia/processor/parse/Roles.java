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
import io.github.rawvoid.protovia.annotation.ProtoOneof;
import io.github.rawvoid.protovia.annotation.ProtoUnknown;

import javax.lang.model.element.Element;

/**
 * Proto role annotations on one element, or the union across a record
 * component and its accessor (they are one site).
 *
 * @author Rawvoid
 */
record Roles(boolean unknown, boolean oneof, boolean field) {

    static final Roles NONE = new Roles(false, false, false);

    Roles union(Roles other) {
        return new Roles(
            unknown || other.unknown,
            oneof || other.oneof,
            field || other.field);
    }

    int count() {
        return (unknown ? 1 : 0) + (oneof ? 1 : 0) + (field ? 1 : 0);
    }

    boolean any() {
        return unknown || oneof || field;
    }

    static Roles of(Element element) {
        if (element == null) {
            return NONE;
        }
        return new Roles(
            element.getAnnotation(ProtoUnknown.class) != null,
            element.getAnnotation(ProtoOneof.class) != null,
            element.getAnnotation(ProtoField.class) != null);
    }
}
