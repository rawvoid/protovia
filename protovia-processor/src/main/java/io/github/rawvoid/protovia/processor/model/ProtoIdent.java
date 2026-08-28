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

import java.util.Locale;
import java.util.Set;

/**
 * Proto3 identifiers, export names, and dotted package names.
 *
 * @author Rawvoid
 */
public final class ProtoIdent {

    /**
     * proto3 keywords and scalar type names. Identifiers, but illegal as
     * export names. Reserved names may still use them.
     */
    private static final Set<String> KEYWORDS = Set.of(
        "syntax", "import", "option", "package", "message", "enum", "service",
        "rpc", "returns", "stream", "reserved", "to", "max", "optional",
        "repeated", "required", "oneof", "map", "group", "extensions", "extend",
        "double", "float", "int32", "int64", "uint32", "uint64", "sint32", "sint64",
        "fixed32", "fixed64", "sfixed32", "sfixed64", "bool", "string", "bytes");

    private ProtoIdent() {
    }

    /**
     * @param name candidate proto name
     * @return {@code true} if {@code name} is {@code [_A-Za-z][_A-Za-z0-9]*}
     */
    public static boolean isIdentifier(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        char first = name.charAt(0);
        if (!isIdentStart(first)) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            if (!isIdentPart(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param name candidate proto name
     * @return {@code true} if {@code name} is a proto keyword or scalar type name
     */
    public static boolean isKeyword(String name) {
        return name != null && KEYWORDS.contains(name);
    }

    /**
     * Field, oneof, message, and enum names that may appear in a {@code .proto}.
     */
    public static boolean isExportName(String name) {
        return isIdentifier(name) && !isKeyword(name);
    }

    /**
     * Dotted proto package. Empty is not a package name; callers treat blank
     * as “no package”.
     */
    public static boolean isPackageName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        int start = 0;
        int n = name.length();
        for (int i = 0; i <= n; i++) {
            if (i < n && name.charAt(i) != '.') {
                continue;
            }
            if (i == start || !isIdentifier(name.substring(start, i))) {
                return false;
            }
            start = i + 1;
        }
        return true;
    }

    /**
     * CamelCase / PascalCase / acronyms to {@code lower_snake_case}.
     * {@code AncillaryBookingRQ} → {@code ancillary_booking_rq}.
     */
    public static String toSnakeCase(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        String split = name
            .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
            .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2");
        return split.toLowerCase(Locale.ROOT);
    }

    /**
     * Protobuf enum constant: {@code CabinClass} + {@code BUSINESS} → {@code CABIN_CLASS_BUSINESS}.
     * The Java constant is itself snake-cased ({@code Unknown} / {@code ActiveUser} →
     * {@code UNKNOWN} / {@code ACTIVE_USER}).
     */
    public static String enumConstantName(String enumTypeName, String constantName) {
        return toSnakeCase(enumTypeName).toUpperCase(Locale.ROOT)
            + "_" + toSnakeCase(constantName).toUpperCase(Locale.ROOT);
    }

    private static boolean isIdentStart(char c) {
        return c == '_' || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private static boolean isIdentPart(char c) {
        return isIdentStart(c) || (c >= '0' && c <= '9');
    }
}
