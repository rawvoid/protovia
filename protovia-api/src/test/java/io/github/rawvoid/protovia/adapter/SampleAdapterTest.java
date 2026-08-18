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
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Rawvoid
 */
class SampleAdapterTest {

    @Test
    void localDateEpochDayRoundTrip() {
        LocalDate epoch = LocalDate.of(1970, 1, 1);
        assertEquals(0, LocalDateEpochDay.INSTANCE.toWire(epoch));
        assertEquals(epoch, LocalDateEpochDay.INSTANCE.fromWire(0));

        LocalDate day = LocalDate.of(2026, 8, 13);
        assertEquals(20678, LocalDateEpochDay.INSTANCE.toWire(day));
        assertEquals(day, LocalDateEpochDay.INSTANCE.fromWire(20678));
    }

    @Test
    void localDateEpochDayRejectsOutOfInt32Range() {
        LocalDate tooBig = LocalDate.ofEpochDay((long) Integer.MAX_VALUE + 1);
        ProtoException high = assertThrows(ProtoException.class,
            () -> LocalDateEpochDay.INSTANCE.toWire(tooBig));
        assertTrue(high.getMessage().contains("int32 epoch-day range"));

        LocalDate tooSmall = LocalDate.ofEpochDay((long) Integer.MIN_VALUE - 1);
        ProtoException low = assertThrows(ProtoException.class,
            () -> LocalDateEpochDay.INSTANCE.toWire(tooSmall));
        assertTrue(low.getMessage().contains("int32 epoch-day range"));

        assertThrows(ProtoException.class, () -> LocalDateEpochDay.INSTANCE.toWire(LocalDate.MAX));
        assertThrows(ProtoException.class, () -> LocalDateEpochDay.INSTANCE.toWire(LocalDate.MIN));

        assertEquals(Integer.MAX_VALUE,
            LocalDateEpochDay.INSTANCE.toWire(LocalDate.ofEpochDay(Integer.MAX_VALUE)));
        assertEquals(Integer.MIN_VALUE,
            LocalDateEpochDay.INSTANCE.toWire(LocalDate.ofEpochDay(Integer.MIN_VALUE)));
    }

    @Test
    void uuidStringRoundTrip() {
        UUID id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        assertEquals(id.toString(), UuidString.INSTANCE.toWire(id));
        assertEquals(id, UuidString.INSTANCE.fromWire(id.toString()));
    }

    @Test
    void uuidStringWrapsParseFailure() {
        ProtoException ex = assertThrows(ProtoException.class,
            () -> UuidString.INSTANCE.fromWire("not-a-uuid"));
        assertTrue(ex.getMessage().contains("not-a-uuid"));
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
    }

    @Test
    void instantEpochMilliRoundTrip() {
        Instant at = Instant.parse("2020-01-02T03:04:05.006Z");
        assertEquals(at.toEpochMilli(), InstantEpochMilli.INSTANCE.toWire(at));
        assertEquals(at, InstantEpochMilli.INSTANCE.fromWire(at.toEpochMilli()));
    }

    @Test
    void durationMilliRoundTrip() {
        Duration wait = Duration.ofMillis(-1500);
        assertEquals(-1500L, DurationMilli.INSTANCE.toWire(wait));
        assertEquals(wait, DurationMilli.INSTANCE.fromWire(-1500L));
    }
}
