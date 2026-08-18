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

import java.time.Instant;

/**
 * Opt-in {@link Instant} as proto3 {@code int64} epoch nanoseconds. Unused unless named
 * in {@code @ProtoField(adapter)} / {@code @ProtoAdapters}.
 *
 * @implNote Supports timestamps between years ~1678 and ~2262. Throws {@link ProtoException}
 * if the instant exceeds the 64-bit nanosecond range.
 *
 * @author Rawvoid
 */
@ProtoScalar(ProtoType.INT64)
public final class InstantEpochNano implements ProtoAdapter<Instant, Long> {

    public static final InstantEpochNano INSTANCE = new InstantEpochNano();
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private InstantEpochNano() {
    }

    @Override
    public Long toWire(Instant value) {
        try {
            return Math.addExact(Math.multiplyExact(value.getEpochSecond(), NANOS_PER_SECOND), value.getNano());
        } catch (ArithmeticException e) {
            throw new ProtoException("Instant out of int64 epoch nanoseconds range: " + value, e);
        }
    }

    @Override
    public Instant fromWire(Long wire) {
        long epochSecond = Math.floorDiv(wire, NANOS_PER_SECOND);
        int nanoAdjustment = (int) Math.floorMod(wire, NANOS_PER_SECOND);
        return Instant.ofEpochSecond(epochSecond, nanoAdjustment);
    }
}
