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
 * Opt-in {@link LocalTime} as proto3 {@code int64} nanosecond of day (0 to 86,399,999,999,999).
 * Unused unless named in {@code @ProtoField(adapter)} / {@code @ProtoAdapters}.
 *
 * @author Rawvoid
 */
@ProtoScalar(ProtoType.INT64)
public final class LocalTimeNanoOfDay implements ProtoAdapter<LocalTime, Long> {

    public static final LocalTimeNanoOfDay INSTANCE = new LocalTimeNanoOfDay();
    private static final long MAX_NANO_OF_DAY = 86_399_999_999_999L;

    private LocalTimeNanoOfDay() {
    }

    @Override
    public Long toWire(LocalTime value) {
        return value.toNanoOfDay();
    }

    @Override
    public LocalTime fromWire(Long wire) {
        if (wire < 0 || wire > MAX_NANO_OF_DAY) {
            throw new ProtoException("LocalTime nano-of-day out of range [0, 86399999999999]: " + wire);
        }
        return LocalTime.ofNanoOfDay(wire);
    }
}
