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

package io.github.rawvoid.protovia.adapter;

import io.github.rawvoid.protovia.ProtoException;
import io.github.rawvoid.protovia.ProtoType;
import io.github.rawvoid.protovia.annotation.ProtoScalar;
import io.github.rawvoid.protovia.codec.ProtoAdapter;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * Opt-in {@link OffsetDateTime} as proto3 {@code string} formatted in standard ISO-8601 / RFC 3339
 * (e.g. {@code "2026-08-18T15:47:05.123+08:00"}).
 * Unused unless named in {@code @ProtoField(adapter)} / {@code @ProtoAdapters}.
 *
 * @author Rawvoid
 */
@ProtoScalar(ProtoType.STRING)
public final class OffsetDateTimeIsoStringAdapter implements ProtoAdapter<OffsetDateTime, String> {

    public static final OffsetDateTimeIsoStringAdapter INSTANCE = new OffsetDateTimeIsoStringAdapter();

    private OffsetDateTimeIsoStringAdapter() {
    }

    @Override
    public String toWire(OffsetDateTime value) {
        return value.toString();
    }

    @Override
    public OffsetDateTime fromWire(String wire) {
        try {
            return OffsetDateTime.parse(wire);
        } catch (DateTimeParseException e) {
            throw new ProtoException("invalid OffsetDateTime string: " + wire, e);
        }
    }
}
