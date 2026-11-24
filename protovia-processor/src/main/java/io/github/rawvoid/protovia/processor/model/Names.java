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

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/**
 * Identifier helpers for generated Java (packages, codec names, tags, helpers).
 *
 * @author Rawvoid
 */
public final class Names {

    private Names() {
    }

    public static String packageName(TypeElement type) {
        Element e = type;
        while (e != null && e.getKind() != ElementKind.PACKAGE) {
            e = e.getEnclosingElement();
        }
        return e instanceof PackageElement pkg ? pkg.getQualifiedName().toString() : "";
    }

    /**
     * Protobuf package for export and Any {@code type_url}. Blank {@code declared}
     * falls back to the Java package; the unnamed Java package yields {@code ""}.
     */
    public static String protoPackage(TypeElement type, String declared) {
        if (declared != null && !declared.isBlank()) {
            return declared.trim();
        }
        return packageName(type);
    }

    public static String codecPackageName(TypeElement type) {
        return codecPackageName(packageName(type));
    }

    public static String codecPackageName(String entityPackage) {
        return entityPackage.isEmpty() ? "internal" : entityPackage + ".internal";
    }

    public static String binaryName(Elements elements, TypeElement type) {
        return elements.getBinaryName(type).toString();
    }

    public static String codecFqcn(Elements elements, TypeElement type) {
        String binaryName = binaryName(elements, type);
        String pkg = packageName(type);
        String typePart = pkg.isEmpty() ? binaryName : binaryName.substring(pkg.length() + 1);
        return codecPackageName(pkg) + "." + typePart + "ProtoCodec";
    }

    public static String codecSimpleName(Elements elements, TypeElement type) {
        String binaryName = binaryName(elements, type);
        String pkg = packageName(type);
        String typePart = pkg.isEmpty() ? binaryName : binaryName.substring(pkg.length() + 1);
        return typePart + "ProtoCodec";
    }

    public static String typeName(TypeElement type, String currentPackage) {
        return render(type, currentPackage);
    }

    private static String render(TypeElement type, String currentPackage) {
        Element enclosing = type.getEnclosingElement();
        if (enclosing instanceof TypeElement parent) {
            return render(parent, currentPackage) + "." + type.getSimpleName();
        }
        String pkg = packageName(type);
        if (pkg.equals(currentPackage) || pkg.isEmpty()) {
            return type.getSimpleName().toString();
        }
        return type.getQualifiedName().toString();
    }

    public static String safeLocal(String name) {
        return "f_" + name;
    }

    public static String capitalize(String name) {
        if (name.isEmpty()) {
            return name;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    public static String decapitalize(String name) {
        if (name.isEmpty()) {
            return name;
        }
        if (name.length() > 1 && Character.isUpperCase(name.charAt(0)) && Character.isUpperCase(name.charAt(1))) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    public static String propertyFromGetter(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return decapitalize(methodName.substring(3));
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            return decapitalize(methodName.substring(2));
        }
        return null;
    }

    public static String getterName(String property, boolean primitiveBoolean) {
        String cap = capitalize(property);
        return primitiveBoolean ? "is" + cap : "get" + cap;
    }

    /**
     * Rewrites a generated read expression so it targets {@code instance}
     * instead of {@code value}. Inherited field access uses
     * {@code ((Owner) value).name}.
     */
    public static String rewriteReceiver(String readExpr, String fieldName, String instance) {
        if (readExpr == null) {
            return instance + "." + fieldName;
        }
        if (readExpr.startsWith("((")) {
            int idx = readExpr.indexOf(") value).");
            if (idx >= 0) {
                return readExpr.substring(0, idx) + ") " + instance + ")."
                    + readExpr.substring(idx + ") value).".length());
            }
        }
        if (readExpr.startsWith("value.")) {
            return instance + readExpr.substring("value".length());
        }
        return instance + "." + fieldName;
    }

    public static String setterName(String property) {
        return "set" + capitalize(property);
    }

    public static String witherName(String property) {
        return "with" + capitalize(property);
    }

    /**
     * @param fieldNumber protobuf field number
     * @return {@code TAG_<number>} used in generated codecs
     */
    public static String tagConstant(int fieldNumber) {
        return "TAG_" + fieldNumber;
    }

    /**
     * Default proto field name of a oneof case when {@code Case.name} is blank.
     */
    public static String oneofCaseProtoName(OneofCaseModel c) {
        if (c.selfMessage || c.empty()) {
            return decapitalize(caseTypeSimpleName(c));
        }
        if (c.payload != null && c.payload.kind == FieldKind.MESSAGE && c.accessor != null) {
            return stripAccessor(c.accessor);
        }
        if (c.type != null) {
            return decapitalize(c.type.getSimpleName().toString());
        }
        return "bytes";
    }

    private static String caseTypeSimpleName(OneofCaseModel c) {
        if (c.type != null) {
            return c.type.getSimpleName().toString();
        }
        return c.typeName;
    }

    private static String stripAccessor(String accessor) {
        return accessor.endsWith("()") ? accessor.substring(0, accessor.length() - 2) : accessor;
    }

    public static String enumNumberOf(EnumModel model) {
        return "numberOf_" + sanitizeFqn(enumFqn(model));
    }

    public static String enumFrom(EnumModel model) {
        return "from_" + sanitizeFqn(enumFqn(model));
    }

    public static String packedSizeOf(FieldModel field) {
        return "packedSizeOf_" + field.number;
    }

    public static String mapEntrySizeOf(FieldModel field) {
        return "sizeOfEntry_" + field.number;
    }

    public static String mapEntryWrite(FieldModel field) {
        return "writeEntry_" + field.number;
    }

    public static String mapEntryRead(FieldModel field) {
        return "readEntry_" + field.number;
    }

    private static String enumFqn(EnumModel model) {
        return model.type != null ? model.type.getQualifiedName().toString() : model.typeName;
    }

    /**
     * Injectively encodes a Java qualified name into a valid Java identifier
     * fragment: {@code _} becomes {@code _0} and {@code .} becomes {@code _}.
     * Because a Java identifier segment after {@code .} can never start with
     * {@code '0'}, {@code _0} uniquely denotes a literal underscore.
     */
    static String sanitizeFqn(String fqn) {
        StringBuilder out = new StringBuilder(fqn.length() + 8);
        for (int i = 0; i < fqn.length(); i++) {
            char c = fqn.charAt(i);
            if (c == '_') {
                out.append("_0");
            } else if (c == '.') {
                out.append('_');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
