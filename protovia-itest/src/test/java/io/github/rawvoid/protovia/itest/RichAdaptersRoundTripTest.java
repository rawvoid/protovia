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

package io.github.rawvoid.protovia.itest;

import io.github.rawvoid.protovia.Protovia;
import io.github.rawvoid.protovia.itest.model.RichAdaptersModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.time.*;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Rawvoid
 */
class RichAdaptersRoundTripTest {

    @Test
    void richAdaptersFullRoundTrip() throws Exception {
        RichAdaptersModel model = RichAdaptersModel.builder()
            .uuidBytes(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
            .amount(new BigDecimal("123456789.987654321"))
            .bigInt(new BigInteger("9876543210123456789"))
            .ip(InetAddress.getByName("192.168.1.100"))
            .uri(URI.create("https://example.com/api?user=42"))
            .nanoInstant(Instant.parse("2026-08-18T15:47:05.123456789Z"))
            .timeOfDay(LocalTime.of(12, 34, 56, 789_000_000))
            .yearMonth(YearMonth.of(2026, 8))
            .year(Year.of(2026))
            .period(Period.of(2, 5, 12))
            .zoneId(ZoneId.of("Asia/Shanghai"))
            .zoneOffset(ZoneOffset.ofHours(8))
            .date(new Date(1700000000000L))
            .zonedDateTime(ZonedDateTime.of(2026, 8, 18, 15, 47, 5, 123_000_000, ZoneId.of("Asia/Shanghai")))
            .offsetDateTime(OffsetDateTime.of(2026, 8, 18, 15, 47, 5, 123_000_000, ZoneOffset.ofHours(8)))
            .build();

        byte[] bytes = Protovia.toBytes(model);
        RichAdaptersModel back = Protovia.fromBytes(bytes, RichAdaptersModel.class);

        assertEquals(model.uuidBytes, back.uuidBytes);
        assertEquals(model.amount, back.amount);
        assertEquals(model.bigInt, back.bigInt);
        assertEquals(model.ip, back.ip);
        assertEquals(model.uri, back.uri);
        assertEquals(model.nanoInstant, back.nanoInstant);
        assertEquals(model.timeOfDay, back.timeOfDay);
        assertEquals(model.yearMonth, back.yearMonth);
        assertEquals(model.year, back.year);
        assertEquals(model.period, back.period);
        assertEquals(model.zoneId, back.zoneId);
        assertEquals(model.zoneOffset, back.zoneOffset);
        assertEquals(model.date, back.date);
        assertEquals(model.zonedDateTime, back.zonedDateTime);
        assertEquals(model.offsetDateTime, back.offsetDateTime);
    }

    @Test
    void nullFieldsHandledGracefully() {
        RichAdaptersModel model = new RichAdaptersModel();
        byte[] bytes = Protovia.toBytes(model);
        assertEquals(0, bytes.length);

        RichAdaptersModel back = Protovia.fromBytes(bytes, RichAdaptersModel.class);
        assertNull(back.uuidBytes);
        assertNull(back.amount);
        assertNull(back.bigInt);
        assertNull(back.ip);
        assertNull(back.uri);
        assertNull(back.nanoInstant);
        assertNull(back.timeOfDay);
        assertNull(back.yearMonth);
        assertNull(back.year);
        assertNull(back.period);
        assertNull(back.zoneId);
        assertNull(back.zoneOffset);
        assertNull(back.date);
        assertNull(back.zonedDateTime);
        assertNull(back.offsetDateTime);
    }
}
