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

import java.time.Duration;

/**
 * Opt-in {@link Duration} as proto3 {@code int64} millis. Unused unless named
 * in {@code @ProtoField(adapter)} / {@code @ProtoAdapters}. The default mapping
 * remains {@code google.protobuf.Duration}.
 *
 * @author Rawvoid
 */
@ProtoScalar(ProtoType.INT64)
public final class DurationMilliAdapter implements ProtoAdapter<Duration, Long> {

    public static final DurationMilliAdapter INSTANCE = new DurationMilliAdapter();

    private DurationMilliAdapter() {
    }

    @Override
    public Long toWire(Duration value) {
        return value.toMillis();
    }

    @Override
    public Duration fromWire(Long wire) {
        return Duration.ofMillis(wire);
    }
}
