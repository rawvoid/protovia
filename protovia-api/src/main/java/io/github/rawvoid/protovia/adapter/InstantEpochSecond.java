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

/**
 * Opt-in {@link Instant} as proto3 {@code int64} epoch second. Unused unless named
 * in {@code @ProtoField(adapter)} / {@code @ProtoAdapters}.
 *
 * @implNote Lossy conversion: drops sub-second (millisecond and nanosecond) precision.
 *
 * @author Rawvoid
 */
@ProtoScalar(ProtoType.INT64)
public final class InstantEpochSecond implements ProtoAdapter<Instant, Long> {

    public static final InstantEpochSecond INSTANCE = new InstantEpochSecond();

    private InstantEpochSecond() {
    }

    @Override
    public Long toWire(Instant value) {
        return value.getEpochSecond();
    }

    @Override
    public Instant fromWire(Long wire) {
        return Instant.ofEpochSecond(wire);
    }
}
