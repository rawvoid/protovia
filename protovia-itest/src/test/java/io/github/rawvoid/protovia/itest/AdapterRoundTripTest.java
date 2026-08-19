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

import io.github.rawvoid.protovia.ProtoException;
import io.github.rawvoid.protovia.Protovia;
import io.github.rawvoid.protovia.itest.model.*;
import io.github.rawvoid.protovia.wire.ProtoWriter;
import io.github.rawvoid.protovia.wkt.DurationCodec;
import io.github.rawvoid.protovia.wkt.TimestampCodec;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Rawvoid
 */
class AdapterRoundTripTest {

    private static final LocalDate EPOCH = LocalDate.of(1970, 1, 1);
    private static final LocalDate SAMPLE = LocalDate.of(2026, 8, 13);
    private static final UUID ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Test
    void nullLocalDateIsOmitted() {
        Dated dated = new Dated();
        byte[] bytes = Protovia.toBytes(dated);
        assertEquals(0, bytes.length);
        Dated back = Protovia.fromBytes(Dated.class, bytes);
        assertNull(back.birthDate);
    }

    @Test
    void epochDayZeroIsAlwaysWritten() {
        Dated dated = new Dated();
        dated.birthDate = EPOCH;
        byte[] bytes = Protovia.toBytes(dated);
        assertArrayEquals(new byte[]{0x18, 0x00}, bytes);
        Dated back = Protovia.fromBytes(Dated.class, bytes);
        assertEquals(EPOCH, back.birthDate);
    }

    @Test
    void sampleLocalDateRoundTrips() {
        Dated dated = new Dated();
        dated.birthDate = SAMPLE;
        Dated back = Protovia.fromBytes(Dated.class, Protovia.toBytes(dated));
        assertEquals(SAMPLE, back.birthDate);
    }

    @Test
    void recordLocalDateRoundTrips() {
        DatedRecord record = new DatedRecord("evt", SAMPLE);
        assertEquals(record, Protovia.fromBytes(DatedRecord.class, Protovia.toBytes(record)));
        DatedRecord epoch = new DatedRecord("evt", EPOCH);
        assertEquals(epoch, Protovia.fromBytes(DatedRecord.class, Protovia.toBytes(epoch)));
        DatedRecord empty = new DatedRecord(null, null);
        assertEquals(empty, Protovia.fromBytes(DatedRecord.class, Protovia.toBytes(empty)));
    }

    @Test
    void packedAndUnpackedLocalDateLists() {
        Dated dated = new Dated();
        dated.days = List.of(EPOCH, SAMPLE);
        dated.unpacked = List.of(SAMPLE, EPOCH);
        Dated back = Protovia.fromBytes(Dated.class, Protovia.toBytes(dated));
        assertEquals(List.of(EPOCH, SAMPLE), back.days);
        assertEquals(List.of(SAMPLE, EPOCH), back.unpacked);
    }

    @Test
    void mapValueOmittedZeroBecomesEpochDay() {
        Dated dated = new Dated();
        dated.dates.put("epoch", EPOCH);
        byte[] bytes = Protovia.toBytes(dated);
        Dated back = Protovia.fromBytes(Dated.class, bytes);
        assertEquals(EPOCH, back.dates.get("epoch"));
    }

    @Test
    void uuidToLocalDateMapRoundTrips() {
        Dated dated = new Dated();
        dated.byId.put(ID, SAMPLE);
        Dated back = Protovia.fromBytes(Dated.class, Protovia.toBytes(dated));
        assertEquals(1, back.byId.size());
        assertEquals(SAMPLE, back.byId.get(ID));
    }

    @Test
    void uuidFieldRoundTrips() {
        Dated dated = new Dated();
        dated.id = ID;
        Dated back = Protovia.fromBytes(Dated.class, Protovia.toBytes(dated));
        assertEquals(ID, back.id);
    }

    @Test
    void invalidUuidPayloadThrowsProtoException() {
        ProtoWriter writer = ProtoWriter.growing();
        writer.writeString(7, "not-a-uuid");
        ProtoException ex = assertThrows(ProtoException.class,
            () -> Protovia.fromBytes(Dated.class, writer.toByteArray()));
        assertTrue(ex.getMessage().contains("not-a-uuid"));
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
    }

    @Test
    void oneofBornEpochWritesTagAndZero() {
        DatedOneof msg = new DatedOneof();
        msg.event = new Born(EPOCH);
        byte[] bytes = Protovia.toBytes(msg);
        assertArrayEquals(new byte[]{0x50, 0x00}, bytes);
        DatedOneof back = Protovia.fromBytes(DatedOneof.class, bytes);
        assertEquals(new Born(EPOCH), back.event);
    }

    @Test
    void instantFieldOverrideLeavesSiblingAsTimestamp() {
        Instant created = Instant.ofEpochMilli(1_600_000_000_000L);
        Instant published = Instant.parse("2020-01-02T03:04:05.006Z");
        Audit audit = new Audit();
        audit.id = "a1";
        audit.created = created;
        audit.published = published;

        Audit back = Protovia.fromBytes(Audit.class, Protovia.toBytes(audit));
        assertEquals("a1", back.id);
        assertEquals(created, back.created);
        assertEquals(published, back.published);

        Audit onlyCreated = new Audit();
        onlyCreated.created = created;
        byte[] createdBytes = Protovia.toBytes(onlyCreated);
        ProtoWriter expected = ProtoWriter.growing();
        expected.writeInt64(2, created.toEpochMilli());
        assertArrayEquals(expected.toByteArray(), createdBytes);

        Audit onlyPublished = new Audit();
        onlyPublished.published = published;
        byte[] publishedBytes = Protovia.toBytes(onlyPublished);
        assertEquals((byte) 0x1A, publishedBytes[0]);
        assertArrayEquals(
            encode(TimestampCodec.INSTANCE, published),
            slice(publishedBytes, 2, publishedBytes.length));
    }

    @Test
    void classLevelInstantAndDurationOverride() {
        Instant created = Instant.ofEpochMilli(1_700_000_000_000L);
        Instant updated = Instant.ofEpochMilli(1_700_000_100_000L);
        Duration ttl = Duration.ofMillis(2500);
        Event event = new Event();
        event.created = created;
        event.updated = updated;
        event.ttl = ttl;

        Event back = Protovia.fromBytes(Event.class, Protovia.toBytes(event));
        assertEquals(created, back.created);
        assertEquals(updated, back.updated);
        assertEquals(ttl, back.ttl);

        ProtoWriter expected = ProtoWriter.growing();
        expected.writeInt64(1, created.toEpochMilli());
        expected.writeInt64(2, updated.toEpochMilli());
        expected.writeInt64(3, ttl.toMillis());
        assertArrayEquals(expected.toByteArray(), Protovia.toBytes(event));
    }

    @Test
    void timedInstantAndDurationStayWellKnownMessages() {
        Instant at = Instant.parse("2020-01-02T03:04:05.006Z");
        Duration wait = Duration.ofSeconds(-1, 500_000_000);
        Timed timed = new Timed();
        timed.at = at;
        timed.wait = wait;
        byte[] bytes = Protovia.toBytes(timed);
        assertEquals((byte) 0x0A, bytes[0]);

        Timed back = Protovia.fromBytes(Timed.class, bytes);
        assertEquals(at, back.at);
        assertEquals(wait, back.wait);
        assertArrayEquals(encode(TimestampCodec.INSTANCE, at),
            encode(TimestampCodec.INSTANCE, back.at));
        assertArrayEquals(encode(DurationCodec.INSTANCE, wait),
            encode(DurationCodec.INSTANCE, back.wait));
    }

    @Test
    void userRoundTripUnchanged() {
        User user = new User();
        user.setName("Ada");
        user.setAge(36);
        User back = Protovia.fromBytes(User.class, Protovia.toBytes(user));
        assertEquals(user, back);
    }

    private static <T> byte[] encode(io.github.rawvoid.protovia.codec.ProtoCodec<T> codec, T value) {
        int size = codec.computeSize(value);
        io.github.rawvoid.protovia.wire.ProtoWriter writer =
            new io.github.rawvoid.protovia.wire.ProtoWriter(size);
        codec.writeTo(writer, value);
        return writer.finish();
    }

    private static byte[] slice(byte[] src, int from, int to) {
        byte[] out = new byte[to - from];
        System.arraycopy(src, from, out, 0, out.length);
        return out;
    }
}
