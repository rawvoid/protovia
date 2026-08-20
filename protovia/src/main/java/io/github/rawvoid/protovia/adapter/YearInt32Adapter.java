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

import java.time.DateTimeException;
import java.time.Year;

/**
 * Opt-in {@link Year} as proto3 {@code int32} integer value (e.g. 2026).
 * Unused unless named in {@code @ProtoField(adapter)} / {@code @ProtoAdapters}.
 *
 * @author Rawvoid
 */
@ProtoScalar(ProtoType.INT32)
public final class YearInt32Adapter implements ProtoAdapter<Year, Integer> {

    public static final YearInt32Adapter INSTANCE = new YearInt32Adapter();

    private YearInt32Adapter() {
    }

    @Override
    public Integer toWire(Year value) {
        return value.getValue();
    }

    @Override
    public Year fromWire(Integer wire) {
        try {
            return Year.of(wire);
        } catch (DateTimeException e) {
            throw new ProtoException("invalid Year value: " + wire, e);
        }
    }
}
