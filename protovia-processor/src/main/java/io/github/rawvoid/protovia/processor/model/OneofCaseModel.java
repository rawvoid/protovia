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

import javax.lang.model.element.TypeElement;

/**
 * One {@code @ProtoOneof.Case} of a parent message.
 *
 * @author Rawvoid
 */
@Getter
@Builder(builderClassName = "Builder", toBuilder = true)
@AllArgsConstructor
public final class OneofCaseModel {

    public final int number;
    /**
     * Case Java type, or {@code null} for a naked {@code byte[]} case.
     */
    public final TypeElement type;
    public final String typeName;
    public final String tagConstant;
    /**
     * {@code null} when the case is an empty record.
     */
    public final FieldModel payload;
    /**
     * Record component accessor, e.g. {@code value()}.
     * {@code null} for an empty record, a self-message, or a naked payload.
     */
    public final String accessor;
    public final boolean selfMessage;

    public boolean empty() {
        return !selfMessage && payload == null;
    }
}
