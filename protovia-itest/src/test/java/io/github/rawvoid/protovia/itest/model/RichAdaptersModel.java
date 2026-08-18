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

package io.github.rawvoid.protovia.itest.model;

import io.github.rawvoid.protovia.adapter.*;
import io.github.rawvoid.protovia.annotation.ProtoAdapters;
import io.github.rawvoid.protovia.annotation.ProtoField;
import io.github.rawvoid.protovia.annotation.ProtoMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.time.*;
import java.util.Date;
import java.util.UUID;

/**
 * @author Rawvoid
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ProtoMessage
@ProtoAdapters({
    UuidBytesAdapter.class,
    BigDecimalStringAdapter.class,
    BigIntegerStringAdapter.class,
    InetAddressBytesAdapter.class,
    UriStringAdapter.class,
    InstantEpochNanoAdapter.class,
    LocalTimeMilliOfDayAdapter.class,
    YearMonthEpochMonthAdapter.class,
    YearInt32Adapter.class,
    PeriodIsoStringAdapter.class,
    ZoneIdStringAdapter.class,
    ZoneOffsetSecondsAdapter.class,
    DateEpochMilliAdapter.class,
    ZonedDateTimeIsoStringAdapter.class,
    OffsetDateTimeIsoStringAdapter.class
})
public class RichAdaptersModel {

    @ProtoField(number = 1)
    public UUID uuidBytes;

    @ProtoField(number = 2)
    public BigDecimal amount;

    @ProtoField(number = 3)
    public BigInteger bigInt;

    @ProtoField(number = 4)
    public InetAddress ip;

    @ProtoField(number = 5)
    public URI uri;

    @ProtoField(number = 6)
    public Instant nanoInstant;

    @ProtoField(number = 7)
    public LocalTime timeOfDay;

    @ProtoField(number = 8)
    public YearMonth yearMonth;

    @ProtoField(number = 9)
    public Year year;

    @ProtoField(number = 10)
    public Period period;

    @ProtoField(number = 11)
    public ZoneId zoneId;

    @ProtoField(number = 12)
    public ZoneOffset zoneOffset;

    @ProtoField(number = 13)
    public Date date;

    @ProtoField(number = 14)
    public ZonedDateTime zonedDateTime;

    @ProtoField(number = 15)
    public OffsetDateTime offsetDateTime;
}
