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

import java.time.LocalDate;

/**
 * Opt-in {@link LocalDate} as proto3 {@code int32} epoch day. Unused unless named
 * in {@code @ProtoField(adapter)} / {@code @ProtoAdapters}. Not a well-known type.
 *
 * @author Rawvoid
 */
@ProtoScalar(ProtoType.INT32)
public final class LocalDateEpochDayAdapter implements ProtoAdapter<LocalDate, Integer> {

    public static final LocalDateEpochDayAdapter INSTANCE = new LocalDateEpochDayAdapter();

    private LocalDateEpochDayAdapter() {
    }

    @Override
    public Integer toWire(LocalDate value) {
        long day = value.toEpochDay();
        if (day < Integer.MIN_VALUE || day > Integer.MAX_VALUE) {
            throw new ProtoException("LocalDate out of int32 epoch-day range: " + value);
        }
        return (int) day;
    }

    @Override
    public LocalDate fromWire(Integer wire) {
        return LocalDate.ofEpochDay(wire);
    }
}
