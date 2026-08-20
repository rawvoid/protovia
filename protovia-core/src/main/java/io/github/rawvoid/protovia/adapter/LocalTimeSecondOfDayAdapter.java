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

import java.time.LocalTime;

/**
 * Opt-in {@link LocalTime} as proto3 {@code int32} second of day (0 to 86,399).
 * Unused unless named in {@code @ProtoField(adapter)} / {@code @ProtoAdapters}.
 *
 * @implNote Lossy conversion: drops sub-second precision.
 *
 * @author Rawvoid
 */
@ProtoScalar(ProtoType.INT32)
public final class LocalTimeSecondOfDayAdapter implements ProtoAdapter<LocalTime, Integer> {

    public static final LocalTimeSecondOfDayAdapter INSTANCE = new LocalTimeSecondOfDayAdapter();

    private LocalTimeSecondOfDayAdapter() {
    }

    @Override
    public Integer toWire(LocalTime value) {
        return value.toSecondOfDay();
    }

    @Override
    public LocalTime fromWire(Integer wire) {
        if (wire < 0 || wire > 86399) {
            throw new ProtoException("LocalTime second-of-day out of range [0, 86399]: " + wire);
        }
        return LocalTime.ofSecondOfDay(wire);
    }
}
