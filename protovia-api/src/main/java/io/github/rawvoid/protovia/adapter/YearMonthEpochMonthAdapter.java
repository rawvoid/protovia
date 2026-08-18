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

import java.time.YearMonth;

/**
 * Opt-in {@link YearMonth} as proto3 {@code int32} 0-based epoch month (1970-01 is 23640).
 * Calculates month count from year 0: {@code year * 12 + (month - 1)}.
 * Unused unless named in {@code @ProtoField(adapter)} / {@code @ProtoAdapters}.
 *
 * @author Rawvoid
 */
@ProtoScalar(ProtoType.INT32)
public final class YearMonthEpochMonthAdapter implements ProtoAdapter<YearMonth, Integer> {

    public static final YearMonthEpochMonthAdapter INSTANCE = new YearMonthEpochMonthAdapter();

    private YearMonthEpochMonthAdapter() {
    }

    @Override
    public Integer toWire(YearMonth value) {
        long epochMonth = value.getYear() * 12L + (value.getMonthValue() - 1);
        if (epochMonth < Integer.MIN_VALUE || epochMonth > Integer.MAX_VALUE) {
            throw new ProtoException("YearMonth out of int32 epoch-month range: " + value);
        }
        return (int) epochMonth;
    }

    @Override
    public YearMonth fromWire(Integer wire) {
        int year = Math.floorDiv(wire, 12);
        int month = Math.floorMod(wire, 12) + 1;
        return YearMonth.of(year, month);
    }
}
