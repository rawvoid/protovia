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

package io.github.rawvoid.protovia.processor.gen;

import io.github.rawvoid.protovia.processor.model.EnumModel;
import io.github.rawvoid.protovia.processor.model.FieldModel;
import io.github.rawvoid.protovia.processor.model.Names;

/**
 * Generated helper method names (enum converters, packed-size helpers, map helpers).
 *
 * @author Rawvoid
 */
final class GenNames {

    private GenNames() {
    }

    static String enumNumberOf(EnumModel model) {
        return "numberOf" + sanitize(model.typeName);
    }

    static String enumFrom(EnumModel model) {
        return "from" + sanitize(model.typeName);
    }

    static String packedSizeOf(FieldModel field) {
        return "packedSizeOf" + Names.capitalize(field.name);
    }

    static String mapEntrySizeOf(FieldModel field) {
        return "sizeOf" + Names.capitalize(field.name) + "Entry";
    }

    static String mapEntryWrite(FieldModel field) {
        return "write" + Names.capitalize(field.name) + "Entry";
    }

    static String mapEntryRead(FieldModel field) {
        return "read" + Names.capitalize(field.name) + "Entry";
    }

    private static String sanitize(String typeName) {
        return typeName.replace(".", "_");
    }
}
