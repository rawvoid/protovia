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
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Opt-in {@link LocalDateTime} as proto3 {@code int64} epoch millisecond.
 * Unused unless named in {@code @ProtoField(adapter)} / {@code @ProtoAdapters}.
 *
 * @implNote {@link LocalDateTime} has no timezone component. This adapter implicitly assumes
 * and binds to {@link ZoneOffset#UTC} during serialization and deserialization.
 * Drops sub-millisecond precision.
 *
 * @author Rawvoid
 */
@ProtoScalar(ProtoType.INT64)
public final class LocalDateTimeEpochMilli implements ProtoAdapter<LocalDateTime, Long> {

    public static final LocalDateTimeEpochMilli INSTANCE = new LocalDateTimeEpochMilli();

    private LocalDateTimeEpochMilli() {
    }

    @Override
    public Long toWire(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    @Override
    public LocalDateTime fromWire(Long wire) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(wire), ZoneOffset.UTC);
    }
}
