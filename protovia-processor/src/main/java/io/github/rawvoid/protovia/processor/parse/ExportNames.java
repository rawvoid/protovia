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

import io.github.rawvoid.protovia.processor.model.ProtoIdent;

import javax.lang.model.element.Element;

/**
 * Resolves and validates proto export names and packages.
 *
 * @author Rawvoid
 */
final class ExportNames {

    private ExportNames() {
    }

    /**
     * @param declared annotation value; blank means {@code fallback}
     * @param fallback Java member name or default case name
     */
    static String orDefault(String declared, String fallback) {
        if (declared == null) {
            return fallback;
        }
        String trimmed = declared.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    /**
     * @return {@code true} if {@code protoName} may appear in a {@code .proto}
     */
    static boolean require(Diagnostics diag, Element origin, String protoName) {
        if (ProtoIdent.isExportName(protoName)) {
            return true;
        }
        if (ProtoIdent.isKeyword(protoName)) {
            diag.error(origin, "proto name '" + protoName + "' is a proto keyword; set name to override");
        } else {
            diag.error(origin, "proto name '" + protoName + "' is not a proto identifier");
        }
        return false;
    }

    /**
     * Empty package is allowed. Non-empty must be dotted identifiers.
     */
    static boolean requirePackage(Diagnostics diag, Element origin, String packageName) {
        if (packageName.isEmpty() || ProtoIdent.isPackageName(packageName)) {
            return true;
        }
        diag.error(origin, "invalid proto package name '" + packageName + "'");
        return false;
    }
}
