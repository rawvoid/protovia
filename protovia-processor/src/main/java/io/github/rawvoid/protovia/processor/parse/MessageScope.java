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

import io.github.rawvoid.protovia.processor.model.FieldModel;
import io.github.rawvoid.protovia.processor.model.MessageModel;
import io.github.rawvoid.protovia.processor.model.Names;
import io.github.rawvoid.protovia.wire.WireType;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.*;

/**
 * Accumulator for one {@code parseMessage} call.
 *
 * @author Rawvoid
 */
final class MessageScope {

    final TypeElement type;
    final String pkg;
    final Map<Integer, FieldModel> byNumber = new LinkedHashMap<>();
    final Set<Integer> taken = new HashSet<>();
    final Set<String> claimed = new HashSet<>();
    final List<FieldModel> oneofs = new ArrayList<>();
    final List<MessageModel.RecordComponentModel> recordComponents = new ArrayList<>();
    final Set<String> annotatedViaField = new HashSet<>();
    MessageModel.UnknownField unknown;

    MessageScope(TypeElement type, String pkg) {
        this.type = type;
        this.pkg = pkg;
    }

    boolean addField(FieldModel field, Diagnostics diag) {
        if (!WireType.isValidFieldNumber(field.number)) {
            diag.error(field.origin, "invalid field number " + field.number
                + " (must be in [1, 536870911] and not in [19000, 19999])");
            return false;
        }
        if (!taken.add(field.number)) {
            diag.error(field.origin, "duplicate field number " + field.number);
            return false;
        }
        if (!claimed.add(field.name)) {
            diag.error(field.origin, "duplicate proto field name '" + field.name + "'");
            return false;
        }
        byNumber.put(field.number, field);
        return true;
    }

    boolean checkUnknownType(Element origin, TypeMirror type, TypeEnv env, Diagnostics diag) {
        TypeElement expected = env.elements.getTypeElement("io.github.rawvoid.protovia.UnknownFields");
        if (expected == null || !env.types.isSameType(env.types.erasure(type), expected.asType())) {
            diag.error(origin, "@ProtoUnknown must be of type UnknownFields");
            return false;
        }
        return true;
    }

    boolean bindUnknown(
        Element origin,
        TypeMirror type,
        Access access,
        String name,
        TypeEnv env,
        Diagnostics diag) {
        if (!checkUnknownType(origin, type, env, diag)) {
            return false;
        }
        if (unknown != null) {
            diag.error(origin, "at most one @ProtoUnknown per message");
            return false;
        }
        unknown = new MessageModel.UnknownField(
            access.kind(),
            name,
            Names.safeLocal(name),
            access.readExpr(),
            access.setter(),
            name);
        return true;
    }

    void addComponent(String name, TypeMirror type, FieldModel field) {
        recordComponents.add(new MessageModel.RecordComponentModel(name, type, field));
    }

    MessageModel toModel(String codecPackageName, String protoPackage, String protoMessageName, String typeName, String codecSimpleName, boolean record) {
        List<FieldModel> fields = new ArrayList<>(byNumber.values());
        fields.sort(Comparator.comparingInt(f -> f.number));
        fields.addAll(oneofs);
        return new MessageModel(
            type,
            pkg,
            codecPackageName,
            protoPackage,
            protoMessageName,
            typeName,
            codecSimpleName,
            record,
            fields,
            recordComponents,
            unknown);
    }
}
