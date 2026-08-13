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

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;

/**
 * Parsed {@code @ProtoMessage} used by the codec generator.
 *
 * @author Rawvoid
 */
public final class MessageModel {

    public final TypeElement type;
    public final String packageName;
    public final String protoPackage;
    public final String protoMessageName;
    public final String typeName;
    public final String codecSimpleName;
    public final boolean record;
    public final List<FieldModel> fields;
    public final List<RecordComponentModel> recordComponents;
    public final UnknownField unknown;

    public MessageModel(
        TypeElement type,
        String packageName,
        String protoPackage,
        String protoMessageName,
        String typeName,
        String codecSimpleName,
        boolean record,
        List<FieldModel> fields,
        List<RecordComponentModel> recordComponents,
        UnknownField unknown) {
        this.type = type;
        this.packageName = packageName;
        this.protoPackage = protoPackage;
        this.protoMessageName = protoMessageName;
        this.typeName = typeName;
        this.codecSimpleName = codecSimpleName;
        this.record = record;
        this.fields = List.copyOf(fields);
        this.recordComponents = List.copyOf(recordComponents);
        this.unknown = unknown;
    }

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
    }
}
