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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.time.*;
import java.util.Date;
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
    void uuidBytesRoundTrip() {
        UUID id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        byte[] wire = UuidBytes.INSTANCE.toWire(id);
        assertEquals(16, wire.length);
        assertEquals(id, UuidBytes.INSTANCE.fromWire(wire));

        UUID random = UUID.randomUUID();
        assertEquals(random, UuidBytes.INSTANCE.fromWire(UuidBytes.INSTANCE.toWire(random)));
    }

    @Test
    void uuidBytesRejectsInvalidLength() {
        assertThrows(ProtoException.class, () -> UuidBytes.INSTANCE.fromWire(null));
        assertThrows(ProtoException.class, () -> UuidBytes.INSTANCE.fromWire(new byte[15]));
        assertThrows(ProtoException.class, () -> UuidBytes.INSTANCE.fromWire(new byte[17]));
    }

    @Test
    void instantEpochMilliRoundTrip() {
        Instant at = Instant.parse("2020-01-02T03:04:05.006Z");
        assertEquals(at.toEpochMilli(), InstantEpochMilli.INSTANCE.toWire(at));
        assertEquals(at, InstantEpochMilli.INSTANCE.fromWire(at.toEpochMilli()));
    }

    @Test
    void instantEpochSecondRoundTrip() {
        Instant at = Instant.ofEpochSecond(1700000000L);
        assertEquals(1700000000L, InstantEpochSecond.INSTANCE.toWire(at));
        assertEquals(at, InstantEpochSecond.INSTANCE.fromWire(1700000000L));
    }

    @Test
    void instantEpochNanoRoundTrip() {
        Instant at = Instant.parse("2026-08-18T15:47:05.123456789Z");
        long wire = InstantEpochNano.INSTANCE.toWire(at);
        assertEquals(at, InstantEpochNano.INSTANCE.fromWire(wire));

        Instant negative = Instant.ofEpochSecond(-10, 500_000_000);
        long negWire = InstantEpochNano.INSTANCE.toWire(negative);
        assertEquals(negative, InstantEpochNano.INSTANCE.fromWire(negWire));
    }

    @Test
    void durationMilliRoundTrip() {
        Duration wait = Duration.ofMillis(-1500);
        assertEquals(-1500L, DurationMilli.INSTANCE.toWire(wait));
        assertEquals(wait, DurationMilli.INSTANCE.fromWire(-1500L));
    }

    @Test
    void durationSecondRoundTrip() {
        Duration d = Duration.ofSeconds(120);
        assertEquals(120L, DurationSecond.INSTANCE.toWire(d));
        assertEquals(d, DurationSecond.INSTANCE.fromWire(120L));
    }

    @Test
    void durationNanoRoundTrip() {
        Duration d = Duration.ofNanos(9876543210L);
        assertEquals(9876543210L, DurationNano.INSTANCE.toWire(d));
        assertEquals(d, DurationNano.INSTANCE.fromWire(9876543210L));
    }

    @Test
    void localTimeSecondOfDayRoundTrip() {
        LocalTime time = LocalTime.of(14, 30, 45);
        int wire = LocalTimeSecondOfDay.INSTANCE.toWire(time);
        assertEquals(time.toSecondOfDay(), wire);
        assertEquals(time, LocalTimeSecondOfDay.INSTANCE.fromWire(wire));

        assertThrows(ProtoException.class, () -> LocalTimeSecondOfDay.INSTANCE.fromWire(-1));
        assertThrows(ProtoException.class, () -> LocalTimeSecondOfDay.INSTANCE.fromWire(86400));
    }

    @Test
    void localTimeMilliOfDayRoundTrip() {
        LocalTime time = LocalTime.of(14, 30, 45, 123_000_000);
        int wire = LocalTimeMilliOfDay.INSTANCE.toWire(time);
        assertEquals(time, LocalTimeMilliOfDay.INSTANCE.fromWire(wire));

        assertThrows(ProtoException.class, () -> LocalTimeMilliOfDay.INSTANCE.fromWire(-1));
        assertThrows(ProtoException.class, () -> LocalTimeMilliOfDay.INSTANCE.fromWire(86_400_000));
    }

    @Test
    void localTimeNanoOfDayRoundTrip() {
        LocalTime time = LocalTime.of(23, 59, 59, 999_999_999);
        long wire = LocalTimeNanoOfDay.INSTANCE.toWire(time);
        assertEquals(time, LocalTimeNanoOfDay.INSTANCE.fromWire(wire));

        assertThrows(ProtoException.class, () -> LocalTimeNanoOfDay.INSTANCE.fromWire(-1L));
        assertThrows(ProtoException.class, () -> LocalTimeNanoOfDay.INSTANCE.fromWire(86_400_000_000_000L));
    }

    @Test
    void localDateTimeEpochMilliRoundTrip() {
        LocalDateTime ldt = LocalDateTime.of(2026, 8, 18, 15, 30, 0, 123_000_000);
        long wire = LocalDateTimeEpochMilli.INSTANCE.toWire(ldt);
        assertEquals(ldt, LocalDateTimeEpochMilli.INSTANCE.fromWire(wire));
    }

    @Test
    void zonedDateTimeEpochMilliRoundTrip() {
        ZonedDateTime zdt = ZonedDateTime.of(2026, 8, 18, 15, 30, 0, 0, ZoneOffset.UTC);
        long wire = ZonedDateTimeEpochMilli.INSTANCE.toWire(zdt);
        assertEquals(zdt, ZonedDateTimeEpochMilli.INSTANCE.fromWire(wire));

        ZonedDateTime shanghai = ZonedDateTime.of(2026, 8, 18, 15, 30, 0, 0, ZoneId.of("Asia/Shanghai"));
        long wireSh = ZonedDateTimeEpochMilli.INSTANCE.toWire(shanghai);
        ZonedDateTime restoredUtc = ZonedDateTimeEpochMilli.INSTANCE.fromWire(wireSh);
        assertEquals(shanghai.toInstant(), restoredUtc.toInstant());
        assertEquals(ZoneOffset.UTC, restoredUtc.getZone());
    }

    @Test
    void offsetDateTimeEpochMilliRoundTrip() {
        OffsetDateTime odt = OffsetDateTime.of(2026, 8, 18, 15, 30, 0, 0, ZoneOffset.UTC);
        long wire = OffsetDateTimeEpochMilli.INSTANCE.toWire(odt);
        assertEquals(odt, OffsetDateTimeEpochMilli.INSTANCE.fromWire(wire));
    }

    @Test
    void zonedDateTimeStringRoundTrip() {
        ZonedDateTime zdt = ZonedDateTime.of(2026, 8, 18, 15, 30, 0, 123_000_000, ZoneId.of("Asia/Shanghai"));
        String wire = ZonedDateTimeString.INSTANCE.toWire(zdt);
        assertEquals(zdt, ZonedDateTimeString.INSTANCE.fromWire(wire));

        assertThrows(ProtoException.class, () -> ZonedDateTimeString.INSTANCE.fromWire("invalid-zdt"));
    }

    @Test
    void offsetDateTimeStringRoundTrip() {
        OffsetDateTime odt = OffsetDateTime.of(2026, 8, 18, 15, 30, 0, 123_000_000, ZoneOffset.ofHours(8));
        String wire = OffsetDateTimeString.INSTANCE.toWire(odt);
        assertEquals(odt, OffsetDateTimeString.INSTANCE.fromWire(wire));

        assertThrows(ProtoException.class, () -> OffsetDateTimeString.INSTANCE.fromWire("invalid-odt"));
    }

    @Test
    void yearMonthEpochMonthRoundTrip() {
        YearMonth ym = YearMonth.of(2026, 8);
        int wire = YearMonthEpochMonth.INSTANCE.toWire(ym);
        assertEquals(ym, YearMonthEpochMonth.INSTANCE.fromWire(wire));

        YearMonth epoch = YearMonth.of(1970, 1);
        assertEquals(23640, YearMonthEpochMonth.INSTANCE.toWire(epoch));
        assertEquals(epoch, YearMonthEpochMonth.INSTANCE.fromWire(23640));

        YearMonth bc = YearMonth.of(-1, 12);
        int bcWire = YearMonthEpochMonth.INSTANCE.toWire(bc);
        assertEquals(bc, YearMonthEpochMonth.INSTANCE.fromWire(bcWire));
    }

    @Test
    void yearValueRoundTrip() {
        Year y = Year.of(2026);
        assertEquals(2026, YearValue.INSTANCE.toWire(y));
        assertEquals(y, YearValue.INSTANCE.fromWire(2026));

        assertThrows(ProtoException.class, () -> YearValue.INSTANCE.fromWire((int) 1e10));
    }

    @Test
    void periodStringRoundTrip() {
        Period period = Period.of(1, 2, 3);
        String wire = PeriodString.INSTANCE.toWire(period);
        assertEquals("P1Y2M3D", wire);
        assertEquals(period, PeriodString.INSTANCE.fromWire(wire));

        assertThrows(ProtoException.class, () -> PeriodString.INSTANCE.fromWire("invalid-period"));
    }

    @Test
    void zoneIdStringRoundTrip() {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        String wire = ZoneIdString.INSTANCE.toWire(zone);
        assertEquals("Asia/Shanghai", wire);
        assertEquals(zone, ZoneIdString.INSTANCE.fromWire(wire));

        assertThrows(ProtoException.class, () -> ZoneIdString.INSTANCE.fromWire("Invalid/Zone_ID"));
    }

    @Test
    void zoneOffsetSecondsRoundTrip() {
        ZoneOffset offset = ZoneOffset.ofHours(8);
        int wire = ZoneOffsetSeconds.INSTANCE.toWire(offset);
        assertEquals(28800, wire);
        assertEquals(offset, ZoneOffsetSeconds.INSTANCE.fromWire(wire));

        assertThrows(ProtoException.class, () -> ZoneOffsetSeconds.INSTANCE.fromWire(1000000));
    }

    @Test
    void dateEpochMilliRoundTrip() {
        Date date = new Date(1700000000000L);
        long wire = DateEpochMilli.INSTANCE.toWire(date);
        assertEquals(1700000000000L, wire);
        assertEquals(date, DateEpochMilli.INSTANCE.fromWire(wire));
    }

    @Test
    void bigDecimalStringRoundTrip() {
        BigDecimal dec = new BigDecimal("12345678901234567890.123456789");
        String wire = BigDecimalString.INSTANCE.toWire(dec);
        assertEquals("12345678901234567890.123456789", wire);
        assertEquals(dec, BigDecimalString.INSTANCE.fromWire(wire));

        assertThrows(ProtoException.class, () -> BigDecimalString.INSTANCE.fromWire("abc"));
    }

    @Test
    void bigIntegerStringRoundTrip() {
        BigInteger bi = new BigInteger("987654321098765432109876543210");
        String wire = BigIntegerString.INSTANCE.toWire(bi);
        assertEquals("987654321098765432109876543210", wire);
        assertEquals(bi, BigIntegerString.INSTANCE.fromWire(wire));

        assertThrows(ProtoException.class, () -> BigIntegerString.INSTANCE.fromWire("xyz"));
    }

    @Test
    void inetAddressBytesRoundTrip() throws Exception {
        InetAddress v4 = InetAddress.getByName("192.168.1.1");
        byte[] wireV4 = InetAddressBytes.INSTANCE.toWire(v4);
        assertEquals(4, wireV4.length);
        assertEquals(v4, InetAddressBytes.INSTANCE.fromWire(wireV4));

        InetAddress v6 = InetAddress.getByName("2001:db8::1");
        byte[] wireV6 = InetAddressBytes.INSTANCE.toWire(v6);
        assertEquals(16, wireV6.length);
        assertEquals(v6, InetAddressBytes.INSTANCE.fromWire(wireV6));

        assertThrows(ProtoException.class, () -> InetAddressBytes.INSTANCE.fromWire(new byte[5]));
    }

    @Test
    void inetAddressStringRoundTrip() throws Exception {
        InetAddress v4 = InetAddress.getByName("192.168.1.1");
        String wireV4 = InetAddressString.INSTANCE.toWire(v4);
        assertEquals("192.168.1.1", wireV4);
        assertEquals(v4, InetAddressString.INSTANCE.fromWire(wireV4));

        assertThrows(ProtoException.class, () -> InetAddressString.INSTANCE.fromWire("999.999.999.999"));
    }

    @Test
    void uriStringRoundTrip() {
        URI uri = URI.create("https://example.com/api/v1?query=protovia#top");
        String wire = UriString.INSTANCE.toWire(uri);
        assertEquals(uri.toString(), wire);
        assertEquals(uri, UriString.INSTANCE.fromWire(wire));

        assertThrows(ProtoException.class, () -> UriString.INSTANCE.fromWire("http:// invalid url with spaces"));
    }
}
