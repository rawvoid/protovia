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

import java.util.Date;

/**
 * Opt-in {@link Date} as proto3 {@code int64} epoch millisecond.
 * Useful for interoperability with legacy Java codebases.
 * Unused unless named in {@code @ProtoField(adapter)} / {@code @ProtoAdapters}.
 *
 * @author Rawvoid
 */
@ProtoScalar(ProtoType.INT64)
public final class DateEpochMilli implements ProtoAdapter<Date, Long> {

    public static final DateEpochMilli INSTANCE = new DateEpochMilli();

    private DateEpochMilli() {
    }

    @Override
    public Long toWire(Date value) {
        return value.getTime();
    }

    @Override
    public Date fromWire(Long wire) {
        return new Date(wire);
    }
}
