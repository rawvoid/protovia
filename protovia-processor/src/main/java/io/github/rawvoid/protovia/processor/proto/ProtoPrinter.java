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

import io.github.rawvoid.protovia.ProtoType;
import io.github.rawvoid.protovia.processor.model.EnumModel;
import io.github.rawvoid.protovia.processor.model.FieldKind;
import io.github.rawvoid.protovia.processor.model.FieldModel;
import io.github.rawvoid.protovia.processor.model.MessageModel;
import io.github.rawvoid.protovia.processor.model.OneofCaseModel;
import io.github.rawvoid.protovia.processor.model.Reserved;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Renders {@link MessageModel} / {@link EnumModel} as proto3 text.
 *
 * @author Rawvoid
 */
public final class ProtoPrinter {

    private ProtoPrinter() {
    }

    public static String print(MessageModel model) {
        String pkg = model.protoPackage;
        StringBuilder out = new StringBuilder();
        header(out, pkg, importsOf(model));
        out.append("message ").append(model.protoMessageName).append(" {\n");
        for (String nested : nestedEmptyNames(model)) {
            out.append("  message ").append(nested).append(" {\n  }\n");
        }
        printReserved(out, "  ", model.reserved);
        for (FieldModel field : model.fields) {
            if (field.kind == FieldKind.ONEOF) {
                printOneof(out, field, pkg);
            } else {
                printField(out, "  ", field, pkg);
            }
        }
        out.append("}\n");
        return out.toString();
    }

    public static String print(EnumModel model) {
        StringBuilder out = new StringBuilder();
        header(out, model.protoPackage, Set.of());
        out.append("enum ").append(model.protoEnumName).append(" {\n");
        printReserved(out, "  ", model.reserved);
        for (EnumModel.Constant constant : model.constants) {
            out.append("  ").append(constant.name()).append(" = ").append(constant.number()).append(";\n");
        }
        out.append("}\n");
        return out.toString();
    }

    private static void header(StringBuilder out, String pkg, Set<String> imports) {
        out.append("syntax = \"proto3\";\n");
        if (pkg != null && !pkg.isEmpty()) {
            out.append('\n');
            out.append("package ").append(pkg).append(";\n");
        }
        if (!imports.isEmpty()) {
            out.append('\n');
            for (String path : imports) {
                out.append("import \"").append(path).append("\";\n");
            }
        }
        out.append('\n');
    }

    private static Set<String> importsOf(MessageModel model) {
        TreeSet<String> imports = new TreeSet<>();
        String self = ProtoNames.filePath(model.protoFullName());
        for (FieldModel field : model.fields) {
            if (field.kind == FieldKind.ONEOF) {
                for (OneofCaseModel c : field.oneofCases) {
                    if (c.empty()) {
                        continue;
                    }
                    addImport(imports, self, c.payload);
                }
            } else {
                addImport(imports, self, field);
            }
        }
        return imports;
    }

    private static void addImport(Set<String> imports, String selfPath, FieldModel field) {
        if (field == null) {
            return;
        }
        switch (field.kind) {
            case REPEATED -> addImport(imports, selfPath, field.element);
            case MAP -> {
                addImport(imports, selfPath, field.mapKey);
                addImport(imports, selfPath, field.mapValue);
            }
            case SCALAR -> {
            }
            default -> {
                ProtoNames.Named named = ProtoNames.named(field);
                if (named != null && !named.importPath().equals(selfPath)) {
                    imports.add(named.importPath());
                }
            }
        }
    }

    private static List<String> nestedEmptyNames(MessageModel model) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (FieldModel field : model.fields) {
            if (field.kind != FieldKind.ONEOF) {
                continue;
            }
            for (OneofCaseModel c : field.oneofCases) {
                if (c.empty() && c.type != null) {
                    names.add(c.type.getSimpleName().toString());
                }
            }
        }
        return List.copyOf(names);
    }

    private static void printOneof(StringBuilder out, FieldModel field, String currentPackage) {
        out.append("  oneof ").append(field.exportName()).append(" {\n");
        for (OneofCaseModel c : field.oneofCases) {
            String typeName;
            if (c.empty()) {
                typeName = c.type != null ? c.type.getSimpleName().toString() : c.typeName;
            } else {
                typeName = typeRef(c.payload, currentPackage);
            }
            out.append("    ").append(typeName).append(' ').append(c.exportName())
                .append(" = ").append(c.number).append(";\n");
        }
        out.append("  }\n");
    }

    private static void printField(StringBuilder out, String indent, FieldModel field, String currentPackage) {
        out.append(indent);
        switch (field.kind) {
            case REPEATED -> {
                out.append("repeated ").append(typeRef(field.element, currentPackage)).append(' ')
                    .append(field.exportName()).append(" = ").append(field.number);
                if (field.packable() && !field.packed) {
                    out.append(" [packed = false]");
                }
            }
            case MAP -> out.append("map<").append(typeRef(field.mapKey, currentPackage)).append(", ")
                .append(typeRef(field.mapValue, currentPackage)).append("> ")
                .append(field.exportName()).append(" = ").append(field.number);
            default -> {
                if (field.optional || field.javaOptional) {
                    out.append("optional ");
                }
                out.append(typeRef(field, currentPackage)).append(' ')
                    .append(field.exportName()).append(" = ").append(field.number);
            }
        }
        out.append(";\n");
    }

    private static String typeRef(FieldModel field, String currentPackage) {
        if (field.kind == FieldKind.SCALAR) {
            return scalarName(field.protoType);
        }
        ProtoNames.Named named = ProtoNames.named(field);
        if (named == null) {
            throw new IllegalStateException("missing proto type for " + field.kind + " field " + field.exportName());
        }
        return ProtoNames.qualify(named.fullName(), currentPackage);
    }

    private static String scalarName(ProtoType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }

    private static void printReserved(StringBuilder out, String indent, Reserved reserved) {
        if (reserved == null || reserved.isEmpty()) {
            return;
        }
        List<String> numbers = new ArrayList<>();
        for (int n : reserved.numbers()) {
            numbers.add(Integer.toString(n));
        }
        for (Reserved.Range range : reserved.ranges()) {
            numbers.add(range.from() + " to " + range.to());
        }
        if (!numbers.isEmpty()) {
            out.append(indent).append("reserved ").append(String.join(", ", numbers)).append(";\n");
        }
        if (!reserved.names().isEmpty()) {
            List<String> quoted = new ArrayList<>();
            for (String name : reserved.names()) {
                quoted.add('"' + name + '"');
            }
            out.append(indent).append("reserved ").append(String.join(", ", quoted)).append(";\n");
        }
    }
}
