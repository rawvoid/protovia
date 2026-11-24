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
import io.github.rawvoid.protovia.UnknownFields;
import io.github.rawvoid.protovia.itest.model.Address;
import io.github.rawvoid.protovia.itest.model.Born;
import io.github.rawvoid.protovia.itest.model.ByteBuffersAndChunksPojo;
import io.github.rawvoid.protovia.itest.model.ByteBuffersAndChunksRecord;
import io.github.rawvoid.protovia.itest.model.CollidingNamesPojo;
import io.github.rawvoid.protovia.itest.model.CollidingNamesRecord;
import io.github.rawvoid.protovia.itest.model.Contact;
import io.github.rawvoid.protovia.itest.model.CrossPackageEnumsHolder;
import io.github.rawvoid.protovia.itest.model.DatedOneof;
import io.github.rawvoid.protovia.itest.model.Email;
import io.github.rawvoid.protovia.itest.model.Home;
import io.github.rawvoid.protovia.itest.model.Kind;
import io.github.rawvoid.protovia.itest.model.OptionalMessageEnumPojo;
import io.github.rawvoid.protovia.itest.model.OptionalMessageEnumRecord;
import io.github.rawvoid.protovia.itest.model.Picker;
import io.github.rawvoid.protovia.itest.model.Status;
import io.github.rawvoid.protovia.wire.ProtoWriter;
import io.github.rawvoid.protovia.wire.WireType;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end round-trip tests for codegen namespace isolation, Optional message/enum,
 * Map&lt;K, ByteBuffer&gt;, byte[][], cross-package enums, and oneof null payload guards.
 *
 * @author Rawvoid
 */
class CodegenEdgeCasesRoundTripTest {

    @Test
    void optionalMessageAndEnumPojoRoundTripAndMerge() {
        OptionalMessageEnumPojo empty = new OptionalMessageEnumPojo();
        assertEquals(0, Protovia.sizeOf(empty));
        assertEquals(0, Protovia.toBytes(empty).length);
        OptionalMessageEnumPojo emptyBack = Protovia.fromBytes(new byte[0], OptionalMessageEnumPojo.class);
        assertEquals(Optional.empty(), emptyBack.addr);
        assertEquals(Optional.empty(), emptyBack.status);
        assertEquals(Optional.empty(), emptyBack.kind);
        assertEquals(Optional.empty(), emptyBack.at);

        // Explicit presence: zero enum values (Status.UNKNOWN = 0, Kind.UNSPECIFIED = 0) MUST be written to wire.
        OptionalMessageEnumPojo zeroPresent = new OptionalMessageEnumPojo();
        zeroPresent.status = Optional.of(Status.UNKNOWN);
        zeroPresent.kind = Optional.of(Kind.UNSPECIFIED);
        byte[] zeroBytes = Protovia.toBytes(zeroPresent);
        assertEquals(zeroBytes.length, Protovia.sizeOf(zeroPresent));
        assertArrayEquals(new byte[]{0x10, 0x00, 0x18, 0x00}, zeroBytes);
        OptionalMessageEnumPojo zeroBack = Protovia.fromBytes(zeroBytes, OptionalMessageEnumPojo.class);
        assertEquals(Optional.of(Status.UNKNOWN), zeroBack.status);
        assertEquals(Optional.of(Kind.UNSPECIFIED), zeroBack.kind);
        assertEquals(Optional.empty(), zeroBack.addr);

        // UNRECOGNIZED sentinel in Optional<Status> is skipped.
        OptionalMessageEnumPojo unrecognized = new OptionalMessageEnumPojo();
        unrecognized.status = Optional.of(Status.UNRECOGNIZED);
        assertEquals(0, Protovia.sizeOf(unrecognized));
        assertEquals(0, Protovia.toBytes(unrecognized).length);

        // Unknown enum value on wire sets Optional.of(Status.UNRECOGNIZED) + unknown fields and round-trips raw varint.
        ProtoWriter unknownWire = ProtoWriter.growing();
        unknownWire.writeInt32(2, 99);
        OptionalMessageEnumPojo unknownBack = Protovia.fromBytes(unknownWire.toByteArray(), OptionalMessageEnumPojo.class);
        assertEquals(Optional.of(Status.UNRECOGNIZED), unknownBack.status);
        assertNotNull(unknownBack.unknown);
        assertFalse(unknownBack.unknown.isEmpty());
        assertArrayEquals(unknownWire.toByteArray(), Protovia.toBytes(unknownBack));

        // Full present round-trip.
        Instant now = Instant.parse("2026-05-01T12:34:56.789Z");
        OptionalMessageEnumPojo full = new OptionalMessageEnumPojo();
        full.addr = Optional.of(new Address("NYC", "5th Ave"));
        full.status = Optional.of(Status.ACTIVE);
        full.kind = Optional.of(Kind.ALPHA);
        full.at = Optional.of(now);
        byte[] fullBytes = Protovia.toBytes(full);
        assertEquals(fullBytes.length, Protovia.sizeOf(full));
        OptionalMessageEnumPojo fullBack = Protovia.fromBytes(fullBytes, OptionalMessageEnumPojo.class);
        assertEquals(Optional.of(new Address("NYC", "5th Ave")), fullBack.addr);
        assertEquals(Optional.of(Status.ACTIVE), fullBack.status);
        assertEquals(Optional.of(Kind.ALPHA), fullBack.kind);
        assertEquals(Optional.of(now), fullBack.at);

        // Merge into existing Optional<Address>: preserves city, updates street.
        OptionalMessageEnumPojo delta = new OptionalMessageEnumPojo();
        delta.addr = Optional.of(new Address(null, "Broadway"));
        OptionalMessageEnumPojo merged = Protovia.mergeFrom(Protovia.toBytes(delta), fullBack);
        assertEquals(Optional.of(new Address("NYC", "Broadway")), merged.addr);
        assertEquals(Optional.of(Status.ACTIVE), merged.status);
        assertEquals(Optional.of(now), merged.at);
    }

    @Test
    void optionalMessageAndEnumRecordRoundTripAndMerge() {
        OptionalMessageEnumRecord empty = new OptionalMessageEnumRecord(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), UnknownFields.EMPTY);
        assertEquals(0, Protovia.sizeOf(empty));
        assertEquals(0, Protovia.toBytes(empty).length);
        OptionalMessageEnumRecord emptyBack = Protovia.fromBytes(new byte[0], OptionalMessageEnumRecord.class);
        assertEquals(Optional.empty(), emptyBack.addr());
        assertEquals(Optional.empty(), emptyBack.status());
        assertEquals(Optional.empty(), emptyBack.kind());
        assertEquals(Optional.empty(), emptyBack.at());

        // Explicit presence for zero enum values on record.
        OptionalMessageEnumRecord zeroPresent = new OptionalMessageEnumRecord(
            Optional.empty(), Optional.of(Status.UNKNOWN), Optional.of(Kind.UNSPECIFIED), Optional.empty(), UnknownFields.EMPTY);
        byte[] zeroBytes = Protovia.toBytes(zeroPresent);
        assertEquals(zeroBytes.length, Protovia.sizeOf(zeroPresent));
        assertArrayEquals(new byte[]{0x10, 0x00, 0x18, 0x00}, zeroBytes);
        OptionalMessageEnumRecord zeroBack = Protovia.fromBytes(zeroBytes, OptionalMessageEnumRecord.class);
        assertEquals(Optional.of(Status.UNKNOWN), zeroBack.status());
        assertEquals(Optional.of(Kind.UNSPECIFIED), zeroBack.kind());

        // Present round-trip & merge.
        Instant at = Instant.parse("2026-06-15T00:00:00Z");
        OptionalMessageEnumRecord full = new OptionalMessageEnumRecord(
            Optional.of(new Address("Tokyo", "Shibuya")),
            Optional.of(Status.BANNED),
            Optional.of(Kind.ALPHA),
            Optional.of(at),
            UnknownFields.EMPTY);
        byte[] fullBytes = Protovia.toBytes(full);
        assertEquals(fullBytes.length, Protovia.sizeOf(full));
        OptionalMessageEnumRecord fullBack = Protovia.fromBytes(fullBytes, OptionalMessageEnumRecord.class);
        assertEquals(full, fullBack);

        OptionalMessageEnumRecord delta = new OptionalMessageEnumRecord(
            Optional.of(new Address(null, "Shinjuku")),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            UnknownFields.EMPTY);
        OptionalMessageEnumRecord merged = Protovia.mergeFrom(Protovia.toBytes(delta), fullBack);
        assertEquals(Optional.of(new Address("Tokyo", "Shinjuku")), merged.addr());
        assertEquals(Optional.of(Status.BANNED), merged.status());
        assertEquals(Optional.of(Kind.ALPHA), merged.kind());
        assertEquals(Optional.of(at), merged.at());
    }

    @Test
    void mapOfByteBufferAndByteArrayArrayRoundTrip() {
        ByteBuffersAndChunksPojo pojo = new ByteBuffersAndChunksPojo();
        pojo.buffers = new LinkedHashMap<>();
        pojo.buffers.put("nonEmpty", ByteBuffer.wrap(new byte[]{10, 20, 30, 40}, 1, 2));
        pojo.buffers.put("empty", ByteBuffer.allocate(0));
        pojo.chunks = new byte[][]{new byte[]{1, 2}, new byte[0], new byte[]{3, 4, 5}};

        byte[] pojoBytes = Protovia.toBytes(pojo);
        assertEquals(pojoBytes.length, Protovia.sizeOf(pojo));
        ByteBuffersAndChunksPojo pojoBack = Protovia.fromBytes(pojoBytes, ByteBuffersAndChunksPojo.class);
        assertEquals(2, pojoBack.buffers.size());
        assertArrayEquals(new byte[]{20, 30}, remainingBytes(pojoBack.buffers.get("nonEmpty")));
        assertArrayEquals(new byte[0], remainingBytes(pojoBack.buffers.get("empty")));
        assertEquals(3, pojoBack.chunks.length);
        assertArrayEquals(new byte[]{1, 2}, pojoBack.chunks[0]);
        assertArrayEquals(new byte[0], pojoBack.chunks[1]);
        assertArrayEquals(new byte[]{3, 4, 5}, pojoBack.chunks[2]);

        // Craft wire map entry with field 1 (key) only and field 2 (ByteBuffer value) omitted -> empty ByteBuffer.
        ProtoWriter entry = ProtoWriter.growing();
        entry.writeString(1, "missingVal");
        byte[] entryBytes = entry.toByteArray();
        ProtoWriter wire = ProtoWriter.growing();
        wire.writeUInt32NoTag(WireType.tag(1, WireType.LEN));
        wire.writeUInt32NoTag(entryBytes.length);
        wire.writeRawBytes(entryBytes, 0, entryBytes.length);
        ByteBuffersAndChunksPojo missingValBack = Protovia.fromBytes(wire.toByteArray(), ByteBuffersAndChunksPojo.class);
        assertTrue(missingValBack.buffers.containsKey("missingVal"));
        assertNotNull(missingValBack.buffers.get("missingVal"));
        assertEquals(0, missingValBack.buffers.get("missingVal").remaining());

        // Record round-trip + merge appends byte[][] chunks.
        ByteBuffersAndChunksRecord rec = new ByteBuffersAndChunksRecord(
            Map.of("k", ByteBuffer.wrap(new byte[]{7, 8, 9})),
            new byte[][]{new byte[]{11}, new byte[]{22, 33}});
        byte[] recBytes = Protovia.toBytes(rec);
        assertEquals(recBytes.length, Protovia.sizeOf(rec));
        ByteBuffersAndChunksRecord recBack = Protovia.fromBytes(recBytes, ByteBuffersAndChunksRecord.class);
        assertArrayEquals(new byte[]{7, 8, 9}, remainingBytes(recBack.buffers().get("k")));
        assertEquals(2, recBack.chunks().length);
        assertArrayEquals(new byte[]{11}, recBack.chunks()[0]);
        assertArrayEquals(new byte[]{22, 33}, recBack.chunks()[1]);

        ByteBuffersAndChunksRecord delta = new ByteBuffersAndChunksRecord(
            Map.of("k2", ByteBuffer.wrap(new byte[]{44})),
            new byte[][]{new byte[]{55}});
        ByteBuffersAndChunksRecord merged = Protovia.mergeFrom(Protovia.toBytes(delta), recBack);
        assertEquals(2, merged.buffers().size());
        assertArrayEquals(new byte[]{44}, remainingBytes(merged.buffers().get("k2")));
        assertEquals(3, merged.chunks().length);
        assertArrayEquals(new byte[]{11}, merged.chunks()[0]);
        assertArrayEquals(new byte[]{22, 33}, merged.chunks()[1]);
        assertArrayEquals(new byte[]{55}, merged.chunks()[2]);
    }

    @Test
    void collidingFieldNamesPojoAndRecordRoundTrip() {
        CollidingNamesPojo pojo = new CollidingNamesPojo();
        pojo.size = 42;
        pojo.value = "val";
        pojo.tag = 99;
        pojo.setMsg(List.of("m1", "m2"));
        pojo.cache = "c";
        pojo.writer = "w";
        pojo.reader = "r";
        pojo.setExisting(Map.of("k", 7));
        pojo._n = Status.ACTIVE;
        pojo.foo = List.of(1, 2, 3);
        pojo.Foo = List.of(4, 5);
        pojo.x = new Address("Paris", "Rue de Rivoli");
        pojo.xSize = 100;
        pojo.xSlot = 200;
        pojo.arr = new int[]{8, 9};
        pojo.arrBuilder = "ab";

        byte[] pojoBytes = Protovia.toBytes(pojo);
        assertEquals(pojoBytes.length, Protovia.sizeOf(pojo));
        CollidingNamesPojo pojoBack = Protovia.fromBytes(pojoBytes, CollidingNamesPojo.class);
        assertEquals(42, pojoBack.size);
        assertEquals("val", pojoBack.value);
        assertEquals(99, pojoBack.tag);
        assertEquals(List.of("m1", "m2"), pojoBack.getMsg());
        assertEquals("c", pojoBack.cache);
        assertEquals("w", pojoBack.writer);
        assertEquals("r", pojoBack.reader);
        assertEquals(Map.of("k", 7), pojoBack.getExisting());
        assertEquals(Status.ACTIVE, pojoBack._n);
        assertEquals(List.of(1, 2, 3), pojoBack.foo);
        assertEquals(List.of(4, 5), pojoBack.Foo);
        assertEquals(new Address("Paris", "Rue de Rivoli"), pojoBack.x);
        assertEquals(100, pojoBack.xSize);
        assertEquals(200, pojoBack.xSlot);
        assertArrayEquals(new int[]{8, 9}, pojoBack.arr);
        assertEquals("ab", pojoBack.arrBuilder);

        CollidingNamesRecord rec = new CollidingNamesRecord(
            10, "v", 20, "m", "ca", "nn", List.of(1, 2), List.of(3, 4), Map.of("e", 5), UnknownFields.EMPTY, 0);
        byte[] recBytes = Protovia.toBytes(rec);
        assertEquals(recBytes.length, Protovia.sizeOf(rec));
        CollidingNamesRecord recBack = Protovia.fromBytes(recBytes, CollidingNamesRecord.class);
        assertEquals(rec, recBack);
    }

    @Test
    void crossPackageSameSimpleNameEnumsRoundTrip() {
        CrossPackageEnumsHolder holder = new CrossPackageEnumsHolder();
        holder.statusA = io.github.rawvoid.protovia.itest.model.enuma.Status.ALPHA;
        holder.statusB = io.github.rawvoid.protovia.itest.model.enumb.Status.BETA;
        holder.optStatusB = Optional.of(io.github.rawvoid.protovia.itest.model.enumb.Status.UNKNOWN);
        holder.listA = List.of(
            io.github.rawvoid.protovia.itest.model.enuma.Status.UNKNOWN,
            io.github.rawvoid.protovia.itest.model.enuma.Status.ALPHA);
        holder.mapB = Map.of("k", io.github.rawvoid.protovia.itest.model.enumb.Status.BETA);

        byte[] bytes = Protovia.toBytes(holder);
        assertEquals(bytes.length, Protovia.sizeOf(holder));
        CrossPackageEnumsHolder back = Protovia.fromBytes(bytes, CrossPackageEnumsHolder.class);
        assertEquals(io.github.rawvoid.protovia.itest.model.enuma.Status.ALPHA, back.statusA);
        assertEquals(io.github.rawvoid.protovia.itest.model.enumb.Status.BETA, back.statusB);
        assertEquals(Optional.of(io.github.rawvoid.protovia.itest.model.enumb.Status.UNKNOWN), back.optStatusB);
        assertEquals(holder.listA, back.listA);
        assertEquals(holder.mapB, back.mapB);
    }

    @Test
    void oneofNullScalarAndAdaptedPayloadThrowsProtoException() {
        Contact nullEmail = new Contact();
        nullEmail.target = new Email(null);
        ProtoException sizeEmailEx = assertThrows(ProtoException.class, () -> Protovia.sizeOf(nullEmail));
        assertTrue(sizeEmailEx.getMessage().contains("payload cannot be null"));
        ProtoException writeEmailEx = assertThrows(ProtoException.class, () -> Protovia.toBytes(nullEmail));
        assertTrue(writeEmailEx.getMessage().contains("payload cannot be null"));

        DatedOneof nullBorn = new DatedOneof();
        nullBorn.event = new Born(null);
        ProtoException sizeBornEx = assertThrows(ProtoException.class, () -> Protovia.sizeOf(nullBorn));
        assertTrue(sizeBornEx.getMessage().contains("payload cannot be null"));
        ProtoException writeBornEx = assertThrows(ProtoException.class, () -> Protovia.toBytes(nullBorn));
        assertTrue(writeBornEx.getMessage().contains("payload cannot be null"));

        // Message and enum oneof wrappers with null payload continue to serialize as 0 bytes.
        Contact nullHome = new Contact();
        nullHome.target = new Home(null);
        assertEquals(0, Protovia.sizeOf(nullHome));
        assertEquals(0, Protovia.toBytes(nullHome).length);

        Picker nullStatus = new Picker();
        nullStatus.choice = new Picker.StatusPick(null);
        assertEquals(0, Protovia.sizeOf(nullStatus));
        assertEquals(0, Protovia.toBytes(nullStatus).length);
    }

    private static byte[] remainingBytes(ByteBuffer buffer) {
        ByteBuffer dup = buffer.duplicate();
        byte[] out = new byte[dup.remaining()];
        dup.get(out);
        return out;
    }
}
