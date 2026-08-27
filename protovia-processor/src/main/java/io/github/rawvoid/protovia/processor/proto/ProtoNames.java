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

import javax.lang.model.element.TypeElement;
import java.util.Locale;

/**
 * Proto full names, import paths, and type references for {@code .proto} export.
 *
 * @author Rawvoid
 */
final class ProtoNames {

    private ProtoNames() {
    }

    static String filePath(String fullName) {
        String simple = simpleName(fullName);
        String pkg = packageName(fullName);
        String file = simple.toLowerCase(Locale.ROOT) + ".proto";
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
            WellKnownProtos.Type wkt = WellKnownProtos.ofClass(type.getQualifiedName().toString());
            return wkt != null ? wkt.fullName() : type.getSimpleName().toString();
        }
        String name = meta.name().isBlank() ? type.getSimpleName().toString() : meta.name().trim();
        String pkg = meta.packageName().isBlank() ? "" : meta.packageName().trim();
        return pkg.isEmpty() ? name : pkg + "." + name;
    }

    static String typeFullName(FieldModel field) {
        if (field.enumModel != null) {
            return field.enumModel.protoFullName();
        }
        if (field.messageType != null) {
            return messageFullName(field.messageType);
        }
        WellKnownProtos.Type wkt = WellKnownProtos.ofClass(field.codecName);
        if (wkt == null && field.javaType != null) {
            wkt = WellKnownProtos.ofClass(field.javaType.toString());
        }
        return wkt != null ? wkt.fullName() : null;
    }

    static String importPath(FieldModel field) {
        WellKnownProtos.Type wkt = WellKnownProtos.ofClass(field.codecName);
        if (wkt == null && field.messageType != null) {
            wkt = WellKnownProtos.ofClass(field.messageType.getQualifiedName().toString());
        }
        if (wkt != null) {
            return wkt.importPath();
        }
        String full = typeFullName(field);
        return full == null ? null : filePath(full);
    }

}
