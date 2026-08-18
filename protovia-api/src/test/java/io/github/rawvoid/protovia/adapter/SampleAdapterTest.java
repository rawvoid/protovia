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
        assertEquals(0, LocalDateEpochDayAdapter.INSTANCE.toWire(epoch));
        assertEquals(epoch, LocalDateEpochDayAdapter.INSTANCE.fromWire(0));

        LocalDate day = LocalDate.of(2026, 8, 13);
        assertEquals(20678, LocalDateEpochDayAdapter.INSTANCE.toWire(day));
        assertEquals(day, LocalDateEpochDayAdapter.INSTANCE.fromWire(20678));
    }

    @Test
    void localDateEpochDayRejectsOutOfInt32Range() {
        LocalDate tooBig = LocalDate.ofEpochDay((long) Integer.MAX_VALUE + 1);
        ProtoException high = assertThrows(ProtoException.class,
            () -> LocalDateEpochDayAdapter.INSTANCE.toWire(tooBig));
        assertTrue(high.getMessage().contains("int32 epoch-day range"));

        LocalDate tooSmall = LocalDate.ofEpochDay((long) Integer.MIN_VALUE - 1);
        ProtoException low = assertThrows(ProtoException.class,
            () -> LocalDateEpochDayAdapter.INSTANCE.toWire(tooSmall));
        assertTrue(low.getMessage().contains("int32 epoch-day range"));

        assertThrows(ProtoException.class, () -> LocalDateEpochDayAdapter.INSTANCE.toWire(LocalDate.MAX));
        assertThrows(ProtoException.class, () -> LocalDateEpochDayAdapter.INSTANCE.toWire(LocalDate.MIN));

        assertEquals(Integer.MAX_VALUE,
            LocalDateEpochDayAdapter.INSTANCE.toWire(LocalDate.ofEpochDay(Integer.MAX_VALUE)));
        assertEquals(Integer.MIN_VALUE,
            LocalDateEpochDayAdapter.INSTANCE.toWire(LocalDate.ofEpochDay(Integer.MIN_VALUE)));
    }

    @Test
    void uuidStringRoundTrip() {
        UUID id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        assertEquals(id.toString(), UuidStringAdapter.INSTANCE.toWire(id));
        assertEquals(id, UuidStringAdapter.INSTANCE.fromWire(id.toString()));
    }

    @Test
    void uuidStringWrapsParseFailure() {
        ProtoException ex = assertThrows(ProtoException.class,
            () -> UuidStringAdapter.INSTANCE.fromWire("not-a-uuid"));
        assertTrue(ex.getMessage().contains("not-a-uuid"));
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
    }

    @Test
    void uuidBytesRoundTrip() {
        UUID id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        byte[] wire = UuidBytesAdapter.INSTANCE.toWire(id);
        assertEquals(16, wire.length);
        assertEquals(id, UuidBytesAdapter.INSTANCE.fromWire(wire));

        UUID random = UUID.randomUUID();
        assertEquals(random, UuidBytesAdapter.INSTANCE.fromWire(UuidBytesAdapter.INSTANCE.toWire(random)));
    }

    @Test
    void uuidBytesRejectsInvalidLength() {
        assertThrows(ProtoException.class, () -> UuidBytesAdapter.INSTANCE.fromWire(null));
        assertThrows(ProtoException.class, () -> UuidBytesAdapter.INSTANCE.fromWire(new byte[15]));
        assertThrows(ProtoException.class, () -> UuidBytesAdapter.INSTANCE.fromWire(new byte[17]));
    }

    @Test
    void instantEpochMilliRoundTrip() {
        Instant at = Instant.parse("2020-01-02T03:04:05.006Z");
        assertEquals(at.toEpochMilli(), InstantEpochMilliAdapter.INSTANCE.toWire(at));
        assertEquals(at, InstantEpochMilliAdapter.INSTANCE.fromWire(at.toEpochMilli()));
    }

    @Test
    void instantEpochSecondRoundTrip() {
        Instant at = Instant.ofEpochSecond(1700000000L);
        assertEquals(1700000000L, InstantEpochSecondAdapter.INSTANCE.toWire(at));
        assertEquals(at, InstantEpochSecondAdapter.INSTANCE.fromWire(1700000000L));
    }

    @Test
    void instantEpochNanoRoundTrip() {
        Instant at = Instant.parse("2026-08-18T15:47:05.123456789Z");
        long wire = InstantEpochNanoAdapter.INSTANCE.toWire(at);
        assertEquals(at, InstantEpochNanoAdapter.INSTANCE.fromWire(wire));

        Instant negative = Instant.ofEpochSecond(-10, 500_000_000);
        long negWire = InstantEpochNanoAdapter.INSTANCE.toWire(negative);
        assertEquals(negative, InstantEpochNanoAdapter.INSTANCE.fromWire(negWire));
    }

    @Test
    void durationMilliRoundTrip() {
        Duration wait = Duration.ofMillis(-1500);
        assertEquals(-1500L, DurationMilliAdapter.INSTANCE.toWire(wait));
        assertEquals(wait, DurationMilliAdapter.INSTANCE.fromWire(-1500L));
    }

    @Test
    void durationSecondRoundTrip() {
        Duration d = Duration.ofSeconds(120);
        assertEquals(120L, DurationSecondAdapter.INSTANCE.toWire(d));
        assertEquals(d, DurationSecondAdapter.INSTANCE.fromWire(120L));
    }

    @Test
    void durationNanoRoundTrip() {
        Duration d = Duration.ofNanos(9876543210L);
        assertEquals(9876543210L, DurationNanoAdapter.INSTANCE.toWire(d));
        assertEquals(d, DurationNanoAdapter.INSTANCE.fromWire(9876543210L));
    }

    @Test
    void localTimeSecondOfDayRoundTrip() {
        LocalTime time = LocalTime.of(14, 30, 45);
        int wire = LocalTimeSecondOfDayAdapter.INSTANCE.toWire(time);
        assertEquals(time.toSecondOfDay(), wire);
        assertEquals(time, LocalTimeSecondOfDayAdapter.INSTANCE.fromWire(wire));

        assertThrows(ProtoException.class, () -> LocalTimeSecondOfDayAdapter.INSTANCE.fromWire(-1));
        assertThrows(ProtoException.class, () -> LocalTimeSecondOfDayAdapter.INSTANCE.fromWire(86400));
    }

    @Test
    void localTimeMilliOfDayRoundTrip() {
        LocalTime time = LocalTime.of(14, 30, 45, 123_000_000);
        int wire = LocalTimeMilliOfDayAdapter.INSTANCE.toWire(time);
        assertEquals(time, LocalTimeMilliOfDayAdapter.INSTANCE.fromWire(wire));

        assertThrows(ProtoException.class, () -> LocalTimeMilliOfDayAdapter.INSTANCE.fromWire(-1));
        assertThrows(ProtoException.class, () -> LocalTimeMilliOfDayAdapter.INSTANCE.fromWire(86_400_000));
    }

    @Test
    void localTimeNanoOfDayRoundTrip() {
        LocalTime time = LocalTime.of(23, 59, 59, 999_999_999);
        long wire = LocalTimeNanoOfDayAdapter.INSTANCE.toWire(time);
        assertEquals(time, LocalTimeNanoOfDayAdapter.INSTANCE.fromWire(wire));

        assertThrows(ProtoException.class, () -> LocalTimeNanoOfDayAdapter.INSTANCE.fromWire(-1L));
        assertThrows(ProtoException.class, () -> LocalTimeNanoOfDayAdapter.INSTANCE.fromWire(86_400_000_000_000L));
    }

    @Test
    void localDateTimeEpochMilliRoundTrip() {
        LocalDateTime ldt = LocalDateTime.of(2026, 8, 18, 15, 30, 0, 123_000_000);
        long wire = LocalDateTimeEpochMilliAdapter.INSTANCE.toWire(ldt);
        assertEquals(ldt, LocalDateTimeEpochMilliAdapter.INSTANCE.fromWire(wire));
    }

    @Test
    void zonedDateTimeEpochMilliRoundTrip() {
        ZonedDateTime zdt = ZonedDateTime.of(2026, 8, 18, 15, 30, 0, 0, ZoneOffset.UTC);
        long wire = ZonedDateTimeEpochMilliAdapter.INSTANCE.toWire(zdt);
        assertEquals(zdt, ZonedDateTimeEpochMilliAdapter.INSTANCE.fromWire(wire));

        ZonedDateTime shanghai = ZonedDateTime.of(2026, 8, 18, 15, 30, 0, 0, ZoneId.of("Asia/Shanghai"));
        long wireSh = ZonedDateTimeEpochMilliAdapter.INSTANCE.toWire(shanghai);
        ZonedDateTime restoredUtc = ZonedDateTimeEpochMilliAdapter.INSTANCE.fromWire(wireSh);
        assertEquals(shanghai.toInstant(), restoredUtc.toInstant());
        assertEquals(ZoneOffset.UTC, restoredUtc.getZone());
    }

    @Test
    void offsetDateTimeEpochMilliRoundTrip() {
        OffsetDateTime odt = OffsetDateTime.of(2026, 8, 18, 15, 30, 0, 0, ZoneOffset.UTC);
        long wire = OffsetDateTimeEpochMilliAdapter.INSTANCE.toWire(odt);
        assertEquals(odt, OffsetDateTimeEpochMilliAdapter.INSTANCE.fromWire(wire));
    }

    @Test
    void zonedDateTimeIsoStringRoundTrip() {
        ZonedDateTime zdt = ZonedDateTime.of(2026, 8, 18, 15, 30, 0, 123_000_000, ZoneId.of("Asia/Shanghai"));
        String wire = ZonedDateTimeIsoStringAdapter.INSTANCE.toWire(zdt);
        assertEquals(zdt, ZonedDateTimeIsoStringAdapter.INSTANCE.fromWire(wire));

        assertThrows(ProtoException.class, () -> ZonedDateTimeIsoStringAdapter.INSTANCE.fromWire("invalid-zdt"));
    }

    @Test
    void offsetDateTimeIsoStringRoundTrip() {
        OffsetDateTime odt = OffsetDateTime.of(2026, 8, 18, 15, 30, 0, 123_000_000, ZoneOffset.ofHours(8));
        String wire = OffsetDateTimeIsoStringAdapter.INSTANCE.toWire(odt);
        assertEquals(odt, OffsetDateTimeIsoStringAdapter.INSTANCE.fromWire(wire));

        assertThrows(ProtoException.class, () -> OffsetDateTimeIsoStringAdapter.INSTANCE.fromWire("invalid-odt"));
    }

    @Test
    void yearMonthEpochMonthRoundTrip() {
        YearMonth ym = YearMonth.of(2026, 8);
        int wire = YearMonthEpochMonthAdapter.INSTANCE.toWire(ym);
        assertEquals(ym, YearMonthEpochMonthAdapter.INSTANCE.fromWire(wire));

        YearMonth epoch = YearMonth.of(1970, 1);
        assertEquals(23640, YearMonthEpochMonthAdapter.INSTANCE.toWire(epoch));
        assertEquals(epoch, YearMonthEpochMonthAdapter.INSTANCE.fromWire(23640));

        YearMonth bc = YearMonth.of(-1, 12);
        int bcWire = YearMonthEpochMonthAdapter.INSTANCE.toWire(bc);
        assertEquals(bc, YearMonthEpochMonthAdapter.INSTANCE.fromWire(bcWire));
    }

    @Test
    void yearInt32RoundTrip() {
        Year y = Year.of(2026);
        assertEquals(2026, YearInt32Adapter.INSTANCE.toWire(y));
        assertEquals(y, YearInt32Adapter.INSTANCE.fromWire(2026));

        assertThrows(ProtoException.class, () -> YearInt32Adapter.INSTANCE.fromWire((int) 1e10));
    }

    @Test
    void periodIsoStringRoundTrip() {
        Period period = Period.of(1, 2, 3);
        String wire = PeriodIsoStringAdapter.INSTANCE.toWire(period);
        assertEquals("P1Y2M3D", wire);
        assertEquals(period, PeriodIsoStringAdapter.INSTANCE.fromWire(wire));

        assertThrows(ProtoException.class, () -> PeriodIsoStringAdapter.INSTANCE.fromWire("invalid-period"));
    }

    @Test
    void zoneIdStringRoundTrip() {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        String wire = ZoneIdStringAdapter.INSTANCE.toWire(zone);
        assertEquals("Asia/Shanghai", wire);
        assertEquals(zone, ZoneIdStringAdapter.INSTANCE.fromWire(wire));

        assertThrows(ProtoException.class, () -> ZoneIdStringAdapter.INSTANCE.fromWire("Invalid/Zone_ID"));
    }

    @Test
    void zoneOffsetSecondsRoundTrip() {
        ZoneOffset offset = ZoneOffset.ofHours(8);
        int wire = ZoneOffsetSecondsAdapter.INSTANCE.toWire(offset);
        assertEquals(28800, wire);
        assertEquals(offset, ZoneOffsetSecondsAdapter.INSTANCE.fromWire(wire));

        assertThrows(ProtoException.class, () -> ZoneOffsetSecondsAdapter.INSTANCE.fromWire(1000000));
    }

    @Test
    void dateEpochMilliRoundTrip() {
        Date date = new Date(1700000000000L);
        long wire = DateEpochMilliAdapter.INSTANCE.toWire(date);
        assertEquals(1700000000000L, wire);
        assertEquals(date, DateEpochMilliAdapter.INSTANCE.fromWire(wire));
    }

    @Test
    void bigDecimalStringRoundTrip() {
        BigDecimal dec = new BigDecimal("12345678901234567890.123456789");
        String wire = BigDecimalStringAdapter.INSTANCE.toWire(dec);
        assertEquals("12345678901234567890.123456789", wire);
        assertEquals(dec, BigDecimalStringAdapter.INSTANCE.fromWire(wire));

        assertThrows(ProtoException.class, () -> BigDecimalStringAdapter.INSTANCE.fromWire("abc"));
    }

    @Test
    void bigIntegerStringRoundTrip() {
        BigInteger bi = new BigInteger("987654321098765432109876543210");
        String wire = BigIntegerStringAdapter.INSTANCE.toWire(bi);
        assertEquals("987654321098765432109876543210", wire);
        assertEquals(bi, BigIntegerStringAdapter.INSTANCE.fromWire(wire));

        assertThrows(ProtoException.class, () -> BigIntegerStringAdapter.INSTANCE.fromWire("xyz"));
    }

    @Test
    void inetAddressBytesRoundTrip() throws Exception {
        InetAddress v4 = InetAddress.getByName("192.168.1.1");
        byte[] wireV4 = InetAddressBytesAdapter.INSTANCE.toWire(v4);
        assertEquals(4, wireV4.length);
        assertEquals(v4, InetAddressBytesAdapter.INSTANCE.fromWire(wireV4));

        InetAddress v6 = InetAddress.getByName("2001:db8::1");
        byte[] wireV6 = InetAddressBytesAdapter.INSTANCE.toWire(v6);
        assertEquals(16, wireV6.length);
        assertEquals(v6, InetAddressBytesAdapter.INSTANCE.fromWire(wireV6));

        assertThrows(ProtoException.class, () -> InetAddressBytesAdapter.INSTANCE.fromWire(new byte[5]));
    }

    @Test
    void inetAddressStringRoundTrip() throws Exception {
        InetAddress v4 = InetAddress.getByName("192.168.1.1");
        String wireV4 = InetAddressStringAdapter.INSTANCE.toWire(v4);
        assertEquals("192.168.1.1", wireV4);
        assertEquals(v4, InetAddressStringAdapter.INSTANCE.fromWire(wireV4));

        assertThrows(ProtoException.class, () -> InetAddressStringAdapter.INSTANCE.fromWire("999.999.999.999"));
    }

    @Test
    void uriStringRoundTrip() {
        URI uri = URI.create("https://example.com/api/v1?query=protovia#top");
        String wire = UriStringAdapter.INSTANCE.toWire(uri);
        assertEquals(uri.toString(), wire);
        assertEquals(uri, UriStringAdapter.INSTANCE.fromWire(wire));

        assertThrows(ProtoException.class, () -> UriStringAdapter.INSTANCE.fromWire("http:// invalid url with spaces"));
    }
}
