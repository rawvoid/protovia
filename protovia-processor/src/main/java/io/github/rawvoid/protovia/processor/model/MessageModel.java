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

package io.github.rawvoid.protovia.processor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;

/**
 * Parsed {@code @ProtoMessage} used by the codec generator.
 *
 * @author Rawvoid
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public final class MessageModel {

    public final TypeElement type;
    public final String packageName;
    public final String codecPackageName;
    public final String protoPackage;
    public final String protoMessageName;
    public final String typeName;
    public final String codecSimpleName;
    public final boolean record;
    @Singular
    public final List<FieldModel> fields;
    @Singular
    public final List<RecordComponentModel> recordComponents;
    public final UnknownField unknown;
    @Builder.Default
    public final Reserved reserved = Reserved.EMPTY;
    @Builder.Default
    public final Instantiation instantiation = Instantiation.MUTABLE;

    /**
     * @return {@code package.name} or just {@code name} when the proto package is empty
     */
    public String protoFullName() {
        return protoPackage.isEmpty() ? protoMessageName : protoPackage + "." + protoMessageName;
    }

    public record RecordComponentModel(String name, TypeMirror type, FieldModel field) {
    }

    public record UnknownField(
        AccessKind accessKind,
        String name,
        String localName,
        String readExpr,
        String setterName,
        String fieldName) {

        /**
         * @param instance generated local holding the message ({@code value} or {@code msg})
         * @return field or getter access on that instance
         */
        public String accessOn(String instance) {
            return Names.rewriteReceiver(readExpr, fieldName, instance);
        }
    }
}
