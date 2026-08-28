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

package io.github.rawvoid.protovia.processor.proto;

import io.github.rawvoid.protovia.annotation.ProtoMessage;
import io.github.rawvoid.protovia.processor.model.FieldModel;
import io.github.rawvoid.protovia.processor.model.Names;
import io.github.rawvoid.protovia.processor.model.ProtoIdent;

import javax.lang.model.element.TypeElement;

/**
 * Proto full names, import paths, and type references for {@code .proto} export.
 *
 * @author Rawvoid
 */
final class ProtoNames {

    record Named(String fullName, String importPath) {
    }

    private ProtoNames() {
    }

    static String filePath(String fullName) {
        String simple = simpleName(fullName);
        String pkg = packageName(fullName);
        String file = ProtoIdent.toSnakeCase(simple) + ".proto";
        return pkg.isEmpty() ? file : pkg.replace('.', '/') + "/" + file;
    }

    static String packageName(String fullName) {
        int dot = fullName.lastIndexOf('.');
        return dot < 0 ? "" : fullName.substring(0, dot);
    }

    static String simpleName(String fullName) {
        int dot = fullName.lastIndexOf('.');
        return dot < 0 ? fullName : fullName.substring(dot + 1);
    }

    static String qualify(String fullName, String currentPackage) {
        if (WellKnownProtos.isWellKnownFullName(fullName)) {
            return fullName;
        }
        String pkg = packageName(fullName);
        if (pkg.equals(currentPackage)) {
            return simpleName(fullName);
        }
        return fullName;
    }

    static String messageFullName(TypeElement type) {
        ProtoMessage meta = type.getAnnotation(ProtoMessage.class);
        if (meta == null) {
            return type.getSimpleName().toString();
        }
        String name = meta.name().isBlank() ? type.getSimpleName().toString() : meta.name().trim();
        String pkg = Names.protoPackage(type, meta.packageName());
        return pkg.isEmpty() ? name : pkg + "." + name;
    }

    /**
     * Enum, user message, or well-known codec. {@code null} for scalars.
     */
    static Named named(FieldModel field) {
        if (field.enumModel != null) {
            String full = field.enumModel.protoFullName();
            return new Named(full, filePath(full));
        }
        if (field.messageType != null) {
            String full = messageFullName(field.messageType);
            return new Named(full, filePath(full));
        }
        WellKnownProtos.Type wkt = WellKnownProtos.ofCodec(field.codecName);
        if (wkt != null) {
            return new Named(wkt.fullName(), wkt.importPath());
        }
        return null;
    }
}
