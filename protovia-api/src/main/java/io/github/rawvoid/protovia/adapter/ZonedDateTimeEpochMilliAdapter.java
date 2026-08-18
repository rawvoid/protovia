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

import io.github.rawvoid.protovia.ProtoType;
import io.github.rawvoid.protovia.annotation.ProtoScalar;
import io.github.rawvoid.protovia.codec.ProtoAdapter;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Opt-in {@link ZonedDateTime} as proto3 {@code int64} epoch millisecond.
 * Unused unless named in {@code @ProtoField(adapter)} / {@code @ProtoAdapters}.
 *
 * @implNote Lossy conversion: the original {@link java.time.ZoneId} is not preserved on the wire.
 * {@link #fromWire(Long)} restores the timestamp in {@link ZoneOffset#UTC}. Drops sub-millisecond precision.
 * For lossless representation, use {@link ZonedDateTimeIsoStringAdapter}.
 *
 * @author Rawvoid
 */
@ProtoScalar(ProtoType.INT64)
public final class ZonedDateTimeEpochMilliAdapter implements ProtoAdapter<ZonedDateTime, Long> {

    public static final ZonedDateTimeEpochMilliAdapter INSTANCE = new ZonedDateTimeEpochMilliAdapter();

    private ZonedDateTimeEpochMilliAdapter() {
    }

    @Override
    public Long toWire(ZonedDateTime value) {
        return value.toInstant().toEpochMilli();
    }

    @Override
    public ZonedDateTime fromWire(Long wire) {
        return Instant.ofEpochMilli(wire).atZone(ZoneOffset.UTC);
    }
}
