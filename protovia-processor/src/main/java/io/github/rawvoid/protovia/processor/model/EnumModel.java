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
import java.util.List;

/**
 * Parsed {@code @ProtoEnum}.
 *
 * @author Rawvoid
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public final class EnumModel {

    public final TypeElement type;
    public final String typeName;
    @Builder.Default
    public final String protoPackage = "";
    @Builder.Default
    public final String protoEnumName = "";
    @Singular
    public final List<Constant> constants;
    /**
     * Java-only sentinel name, or {@code null}.
     */
    public final String unrecognized;
    @Builder.Default
    public final Reserved reserved = Reserved.EMPTY;

    /**
     * @return {@code package.name} or just {@code name} when the proto package is empty
     */
    public String protoFullName() {
        String name = protoEnumName == null || protoEnumName.isEmpty()
            ? type.getSimpleName().toString()
            : protoEnumName;
        return protoPackage == null || protoPackage.isEmpty() ? name : protoPackage + "." + name;
    }

    public record Constant(String name, int number) {
    }
}
