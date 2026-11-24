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
import io.github.rawvoid.protovia.codec.ProtoCodec;
import io.github.rawvoid.protovia.collect.DoubleArrayList;
import io.github.rawvoid.protovia.collect.FloatArrayList;
import io.github.rawvoid.protovia.collect.IntArrayList;
import io.github.rawvoid.protovia.collect.LongArrayList;
import io.github.rawvoid.protovia.itest.model.Address;
import io.github.rawvoid.protovia.itest.model.Contact;
import io.github.rawvoid.protovia.itest.model.CountingDeterministicMap;
import io.github.rawvoid.protovia.itest.model.CountingDeterministicNested;
import io.github.rawvoid.protovia.itest.model.CountingKey;
import io.github.rawvoid.protovia.itest.model.CountingKeyAdapter;
import io.github.rawvoid.protovia.itest.model.CountingSetterPojo;
import io.github.rawvoid.protovia.itest.model.CountingU64Key;
import io.github.rawvoid.protovia.itest.model.CountingU64KeyAdapter;
import io.github.rawvoid.protovia.itest.model.FlatAllFeatures;
import io.github.rawvoid.protovia.itest.model.PackedOptModel;
import io.github.rawvoid.protovia.itest.model.SortedByField;
import io.github.rawvoid.protovia.itest.model.Status;
import io.github.rawvoid.protovia.itest.model.User;
import io.github.rawvoid.protovia.itest.model.WideSplitPojo;
import io.github.rawvoid.protovia.itest.model.WideSplitRecord;
import io.github.rawvoid.protovia.itest.model.internal.WideSplitPojoProtoCodec;
import io.github.rawvoid.protovia.itest.model.internal.WideSplitRecordProtoCodec;
import io.github.rawvoid.protovia.wire.ProtoReader;
import io.github.rawvoid.protovia.wire.ProtoWriter;
import io.github.rawvoid.protovia.wire.WireType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for Commit 10 codegen optimizations.
 *
 * @author Rawvoid
 */
class CodecOptimizationTest {

    @Test
    void packedFixedMisalignedLengthsThrowProtoException() {
        // 8-byte fixed fields: 1 (doubles), 2 (fixed64), 3 (sfixed64), 9 (double[]), 11 (fixed64 long[]), 14 (DoubleArrayList)
        for (int fieldNum : new int[]{1, 2, 3, 9, 11, 14}) {
            for (int badLen : new int[]{1, 7, 9, 15}) {
                byte[] wire = packedBytes(fieldNum, new byte[badLen]);
                assertThrows(ProtoException.class,
                    () -> Protovia.fromBytes(wire, PackedOptModel.class),
                    () -> "expected ProtoException for 8-byte field " + fieldNum + " with len " + badLen);
            }
            // Empty packed chunk (len = 0) is aligned and valid.
            PackedOptModel empty = Protovia.fromBytes(packedBytes(fieldNum, new byte[0]), PackedOptModel.class);
            assertTrue(empty.doubles.isEmpty());
        }

        // 4-byte fixed fields: 4 (floats), 5 (fixed32), 6 (sfixed32), 10 (float[]), 12 (fixed32 int[])
        for (int fieldNum : new int[]{4, 5, 6, 10, 12}) {
            for (int badLen : new int[]{1, 2, 3, 5, 7}) {
                byte[] wire = packedBytes(fieldNum, new byte[badLen]);
                assertThrows(ProtoException.class,
                    () -> Protovia.fromBytes(wire, PackedOptModel.class),
                    () -> "expected ProtoException for 4-byte field " + fieldNum + " with len " + badLen);
            }
            PackedOptModel empty = Protovia.fromBytes(packedBytes(fieldNum, new byte[0]), PackedOptModel.class);
            assertTrue(empty.floats.isEmpty());
        }
    }

    @Test
    void packedFixedPreallocatesExactCapacityAndVarintAvoidsAmplification() throws Exception {
        final int count = 100_000;

        // 100,000 packed doubles = 800,000 wire bytes -> exact backing array length 100,000 (NOT 800,000).
        ProtoWriter dblPayload = ProtoWriter.growing();
        for (int i = 0; i < count; i++) {
            dblPayload.writeDoubleNoTag(i * 0.5);
        }
        byte[] dblWire = packedBytes(1, dblPayload.toByteArray());
        PackedOptModel dblDecoded = Protovia.fromBytes(dblWire, PackedOptModel.class);
        assertEquals(count, dblDecoded.doubles.size());
        assertInstanceOf(DoubleArrayList.class, dblDecoded.doubles);
        assertEquals(count, backingArrayLength(dblDecoded.doubles));
        assertEquals(42.5, dblDecoded.doubles.get(85));

        // 100,000 packed floats = 400,000 wire bytes -> exact backing array length 100,000 (NOT 400,000).
        ProtoWriter fltPayload = ProtoWriter.growing();
        for (int i = 0; i < count; i++) {
            fltPayload.writeFloatNoTag(i * 0.25f);
        }
        byte[] fltWire = packedBytes(4, fltPayload.toByteArray());
        PackedOptModel fltDecoded = Protovia.fromBytes(fltWire, PackedOptModel.class);
        assertEquals(count, fltDecoded.floats.size());
        assertInstanceOf(FloatArrayList.class, fltDecoded.floats);
        assertEquals(count, backingArrayLength(fltDecoded.floats));

        // 100,000 packed 6-byte varint longs = 600,000 wire bytes -> geometric growth (~163,840), NOT >= 600,000.
        long sixByteValue = 1L << 38;
        ProtoWriter varintPayload = ProtoWriter.growing();
        for (int i = 0; i < count; i++) {
            varintPayload.writeUInt64NoTag(sixByteValue + i);
        }
        byte[] rawVarints = varintPayload.toByteArray();
        assertEquals(count * 6, rawVarints.length);
        byte[] varintWire = packedBytes(7, rawVarints);
        PackedOptModel varintDecoded = Protovia.fromBytes(varintWire, PackedOptModel.class);
        assertEquals(count, varintDecoded.varintLongs.size());
        assertInstanceOf(LongArrayList.class, varintDecoded.varintLongs);
        int capacity = backingArrayLength(varintDecoded.varintLongs);
        assertTrue(capacity >= count && capacity <= 200_000,
            "expected geometric backing capacity in [100000, 200000], got " + capacity);
        assertEquals(sixByteValue + 1234, varintDecoded.varintLongs.get(1234));
    }

    @Test
    void deterministicMapCachesSortAndInvokesAdapterToWireExactlyOncePerEntry() {
        final int n = 50;

        // 1. Adapted String-key map -> String value
        CountingDeterministicMap msg = new CountingDeterministicMap();
        for (int i = n - 1; i >= 0; i--) {
            String key = String.format("k%03d", (i * 17) % n);
            msg.labels.put(new CountingKey(key), "v" + key);
        }
        CountingKeyAdapter.reset();
        byte[] bytes = Protovia.toBytes(msg);
        assertEquals(n, CountingKeyAdapter.toWireCalls(), "toWire must run exactly once per entry");

        // Verify wire order is strictly ascending.
        List<String> wireKeys = readStringMapKeys(bytes, 1);
        assertEquals(n, wireKeys.size());
        for (int i = 0; i < n; i++) {
            assertEquals(String.format("k%03d", i), wireKeys.get(i));
        }
        assertEquals(msg.labels, Protovia.fromBytes(bytes, CountingDeterministicMap.class).labels);

        // Direct writeTo without SizeCache writes identical bytes.
        ProtoCodec<CountingDeterministicMap> codec = Protovia.codec(CountingDeterministicMap.class);
        ProtoWriter direct = new ProtoWriter(codec.computeSize(msg));
        codec.writeTo(direct, msg);
        assertArrayEquals(bytes, direct.finish());

        // 2. Adapted String-key map -> nested Address message value
        CountingDeterministicMap addrMsg = new CountingDeterministicMap();
        for (int i = n - 1; i >= 0; i--) {
            String key = String.format("a%03d", (i * 23) % n);
            addrMsg.addresses.put(new CountingKey(key), new Address("City-" + key, "Street-" + key));
        }
        CountingKeyAdapter.reset();
        byte[] addrBytes = Protovia.toBytes(addrMsg);
        assertEquals(n, CountingKeyAdapter.toWireCalls(), "toWire must run exactly once per entry for message map");
        assertEquals(addrMsg.addresses, Protovia.fromBytes(addrBytes, CountingDeterministicMap.class).addresses);

        ProtoWriter directAddr = new ProtoWriter(codec.computeSize(addrMsg));
        codec.writeTo(directAddr, addrMsg);
        assertArrayEquals(addrBytes, directAddr.finish());

        // 3. Adapted UINT64-key map -> verifies unsigned wire order + 1x toWire
        CountingDeterministicMap u64Msg = new CountingDeterministicMap();
        u64Msg.unsignedEntries.put(new CountingU64Key(-1L), "max-u64");
        u64Msg.unsignedEntries.put(new CountingU64Key(Long.MIN_VALUE), "mid-high");
        u64Msg.unsignedEntries.put(new CountingU64Key(42L), "forty-two");
        u64Msg.unsignedEntries.put(new CountingU64Key(0L), "zero");
        u64Msg.unsignedEntries.put(new CountingU64Key(1L), "one");
        u64Msg.unsignedEntries.put(new CountingU64Key(Long.MAX_VALUE), "max-s64");
        CountingU64KeyAdapter.reset();
        byte[] u64Bytes = Protovia.toBytes(u64Msg);
        assertEquals(6, CountingU64KeyAdapter.toWireCalls());
        List<Long> u64WireKeys = readUInt64MapKeys(u64Bytes, 3);
        assertEquals(List.of(0L, 1L, 42L, Long.MAX_VALUE, Long.MIN_VALUE, -1L), u64WireKeys);
        assertEquals(u64Msg.unsignedEntries, Protovia.fromBytes(u64Bytes, CountingDeterministicMap.class).unsignedEntries);

        // 4. Nested deterministic maps inside deterministic outer map (SizeCache object pre-order alignment)
        CountingDeterministicMap nestedMsg = new CountingDeterministicMap();
        CountingDeterministicNested innerB = new CountingDeterministicNested();
        innerB.inner.put(new CountingKey("z"), 26);
        innerB.inner.put(new CountingKey("a"), 1);
        CountingDeterministicNested innerA = new CountingDeterministicNested();
        innerA.inner.put(new CountingKey("y"), 25);
        innerA.inner.put(new CountingKey("b"), 2);
        innerA.inner.put(new CountingKey("m"), 13);
        nestedMsg.nestedMaps.put("outer-b", innerB);
        nestedMsg.nestedMaps.put("outer-a", innerA);
        CountingKeyAdapter.reset();
        byte[] nestedBytes = Protovia.toBytes(nestedMsg);
        assertEquals(5, CountingKeyAdapter.toWireCalls());
        ProtoWriter directNested = new ProtoWriter(codec.computeSize(nestedMsg));
        codec.writeTo(directNested, nestedMsg);
        assertArrayEquals(nestedBytes, directNested.finish());
        CountingDeterministicMap decodedNested = Protovia.fromBytes(nestedBytes, CountingDeterministicMap.class);
        assertEquals(innerA.inner, decodedNested.nestedMaps.get("outer-a").inner);
        assertEquals(innerB.inner, decodedNested.nestedMaps.get("outer-b").inner);
    }

    @Test
    void flatMessagesSkipSizeCacheAndRoundTripCleanly() {
        assertFalse(Protovia.codec(Address.class).needsSizeCache());
        assertTrue(Protovia.codec(Address.class).cachesNestedSizes());
        Address addr = new Address("Tokyo", "Shibuya");
        assertEquals(addr, Protovia.fromBytes(Protovia.toBytes(addr), Address.class));

        assertFalse(Protovia.codec(FlatAllFeatures.class).needsSizeCache());
        assertTrue(Protovia.codec(FlatAllFeatures.class).cachesNestedSizes());
        FlatAllFeatures flat = new FlatAllFeatures();
        flat.id = 77;
        flat.name = "flat";
        flat.data = new byte[]{1, 2, 3};
        flat.status = Status.ACTIVE;
        flat.tags = List.of("a", "b");
        flat.unpackedNums = List.of(10, 20, 30);
        flat.choice = new FlatAllFeatures.Text("hello");
        FlatAllFeatures back = Protovia.fromBytes(Protovia.toBytes(flat), FlatAllFeatures.class);
        assertEquals(77, back.id);
        assertEquals("flat", back.name);
        assertArrayEquals(new byte[]{1, 2, 3}, back.data);
        assertEquals(Status.ACTIVE, back.status);
        assertEquals(List.of("a", "b"), back.tags);
        assertEquals(List.of(10, 20, 30), back.unpackedNums);
        assertEquals(new FlatAllFeatures.Text("hello"), back.choice);

        flat.choice = new FlatAllFeatures.Empty();
        assertEquals(new FlatAllFeatures.Empty(),
            Protovia.fromBytes(Protovia.toBytes(flat), FlatAllFeatures.class).choice);

        // Structured messages must keep needsSizeCache() == true.
        assertTrue(Protovia.codec(User.class).needsSizeCache());
        assertTrue(Protovia.codec(Contact.class).needsSizeCache());
        assertTrue(Protovia.codec(SortedByField.class).needsSizeCache());
        assertTrue(Protovia.codec(PackedOptModel.class).needsSizeCache());
        assertTrue(Protovia.codec(CountingDeterministicMap.class).needsSizeCache());
    }

    @Test
    void pojoDecodeCachesCollectionsAndInvokesSettersAtMostOnce() {
        ProtoWriter wire = ProtoWriter.growing();
        List<Integer> expectedInts = new ArrayList<>();
        List<String> expectedStrings = new ArrayList<>();
        Map<String, Integer> expectedScores = new LinkedHashMap<>();
        IntArrayList expectedConcrete = new IntArrayList();

        // 50 unpacked ints (field 1)
        for (int i = 0; i < 50; i++) {
            wire.writeInt32(1, i);
            expectedInts.add(i);
        }
        // Two packed chunks of 50 ints each (field 1)
        for (int chunk = 0; chunk < 2; chunk++) {
            ProtoWriter packed = ProtoWriter.growing();
            for (int i = 0; i < 50; i++) {
                int v = 100 + chunk * 50 + i;
                packed.writeInt32NoTag(v);
                expectedInts.add(v);
            }
            byte[] pBytes = packed.toByteArray();
            wire.writeTag(1, WireType.LEN);
            wire.writeBytesNoTag(pBytes);
        }
        // 50 more unpacked ints (field 1)
        for (int i = 200; i < 250; i++) {
            wire.writeInt32(1, i);
            expectedInts.add(i);
        }
        // 100 strings (field 2)
        for (int i = 0; i < 100; i++) {
            String s = "str-" + i;
            wire.writeString(2, s);
            expectedStrings.add(s);
        }
        // 50 map entries (field 3)
        for (int i = 0; i < 50; i++) {
            String k = "k" + i;
            ProtoWriter entry = ProtoWriter.growing();
            entry.writeString(1, k);
            entry.writeInt32(2, i * 10);
            wire.writeTag(3, WireType.LEN);
            wire.writeBytesNoTag(entry.toByteArray());
            expectedScores.put(k, i * 10);
        }
        // 50 unpacked + 50 packed concrete IntArrayList (field 4)
        for (int i = 0; i < 50; i++) {
            wire.writeInt32(4, i);
            expectedConcrete.addInt(i);
        }
        ProtoWriter concretePacked = ProtoWriter.growing();
        for (int i = 50; i < 100; i++) {
            concretePacked.writeInt32NoTag(i);
            expectedConcrete.addInt(i);
        }
        wire.writeTag(4, WireType.LEN);
        wire.writeBytesNoTag(concretePacked.toByteArray());

        CountingSetterPojo decoded = Protovia.fromBytes(wire.toByteArray(), CountingSetterPojo.class);
        assertEquals(1, decoded.intsSetCount());
        assertEquals(1, decoded.stringsSetCount());
        assertEquals(1, decoded.scoresSetCount());
        assertEquals(1, decoded.concreteIntsSetCount());
        assertInstanceOf(IntArrayList.class, decoded.getInts());
        assertEquals(expectedInts, decoded.getInts());
        assertEquals(expectedStrings, decoded.getStrings());
        assertEquals(expectedScores, decoded.getScores());
        assertEquals(expectedConcrete, decoded.getConcreteInts());

        // When all fields are absent on wire, zero setters are called.
        CountingSetterPojo absent = Protovia.fromBytes(new byte[0], CountingSetterPojo.class);
        assertEquals(0, absent.intsSetCount());
        assertEquals(0, absent.stringsSetCount());
        assertEquals(0, absent.scoresSetCount());
        assertEquals(0, absent.concreteIntsSetCount());
    }

    @Test
    void wideSplitPojoRoundTripAndUnknownEnumExclusion() {
        assertTrue(hasDeclaredMethod(WideSplitPojoProtoCodec.class, "_readChunk_0"),
            "WideSplitPojoProtoCodec must take the split read path");
        WideSplitPojo msg = new WideSplitPojo();
        msg.f1 = 101;
        msg.f2 = "hello";
        msg.f3 = Status.ACTIVE;
        msg.f4 = List.of(1, 2, 3);
        msg.f5 = new int[]{10, 20, 30};
        msg.f6 = new LinkedHashMap<>();
        msg.f6.put("b", 2);
        msg.f6.put("a", 1);
        msg.f7 = Optional.of("opt-val");
        msg.f8 = new Address("Paris", "Rue de Rivoli");
        msg.f9 = List.of(1.5, 2.5);
        msg.f10 = 999_999_999L;
        msg.f31 = true;
        msg.f50 = "mid";
        msg.f56 = Status.BANNED;
        msg.f57 = List.of("x", "y");
        msg.f58 = new int[]{7, 8};
        msg.f59 = Map.of("k", 42);
        msg.f60 = "tail";
        msg.f75 = 75_000_000_000L;
        msg.f85 = "chunk2-tail";

        byte[] encoded = Protovia.toBytes(msg);
        WideSplitPojo back = Protovia.fromBytes(encoded, WideSplitPojo.class);
        assertEquals(101, back.f1);
        assertEquals("hello", back.f2);
        assertEquals(Status.ACTIVE, back.f3);
        assertEquals(List.of(1, 2, 3), back.f4);
        assertArrayEquals(new int[]{10, 20, 30}, back.f5);
        assertEquals(Map.of("a", 1, "b", 2), back.f6);
        assertEquals(Optional.of("opt-val"), back.f7);
        assertEquals(new Address("Paris", "Rue de Rivoli"), back.f8);
        assertEquals(List.of(1.5, 2.5), back.f9);
        assertEquals(999_999_999L, back.f10);
        assertTrue(back.f31);
        assertEquals("mid", back.f50);
        assertEquals(Status.BANNED, back.f56);
        assertEquals(List.of("x", "y"), back.f57);
        assertArrayEquals(new int[]{7, 8}, back.f58);
        assertEquals(Map.of("k", 42), back.f59);
        assertEquals("tail", back.f60);
        assertEquals(75_000_000_000L, back.f75);
        assertEquals("chunk2-tail", back.f85);

        // Unknown fields + enum exclusion across split chunks.
        ProtoWriter wire = ProtoWriter.growing();
        wire.writeEnum(3, 99);    // unrecognized enum in chunk 0
        wire.writeEnum(56, 88);   // unrecognized enum in chunk 1
        wire.writeInt32(999, 777); // genuinely unknown field
        WideSplitPojo withUnknown = Protovia.fromBytes(wire.toByteArray(), WideSplitPojo.class);
        assertEquals(Status.UNRECOGNIZED, withUnknown.f3);
        assertEquals(Status.UNRECOGNIZED, withUnknown.f56);
        assertFalse(withUnknown.unknown.isEmpty());

        // Overwrite f3 to a known enum value while leaving stale unknown entry 3 in withUnknown.unknown.
        withUnknown.f3 = Status.ACTIVE;
        byte[] reencoded = Protovia.toBytes(withUnknown);
        WideSplitPojo afterUpdate = Protovia.fromBytes(reencoded, WideSplitPojo.class);
        assertEquals(Status.ACTIVE, afterUpdate.f3);
        assertEquals(Status.UNRECOGNIZED, afterUpdate.f56);
        assertFalse(afterUpdate.unknown.isEmpty());
        // Verify field 3 is no longer duplicated in unknown fields (only 56 and 999 remain).
        List<Integer> unknownNumbers = readAllFieldNumbers(unknownBytes(afterUpdate.unknown));
        assertFalse(unknownNumbers.contains(3));
        assertTrue(unknownNumbers.contains(56));
        assertTrue(unknownNumbers.contains(999));
    }

    @Test
    void wideSplitRecordRoundTripMergeAndUnknownEnumExclusion() {
        assertTrue(hasDeclaredMethod(WideSplitRecordProtoCodec.class, "_readChunk_0"),
            "WideSplitRecordProtoCodec must take the split read path");
        // First decode from wire containing fields across all chunks + unknown enum + unknown field.
        ProtoWriter wire1 = ProtoWriter.growing();
        wire1.writeInt32(1, 10);
        wire1.writeString(2, "first");
        wire1.writeEnum(3, 99);
        wire1.writeInt32(4, 1);
        wire1.writeInt32(5, 100);
        wire1.writeString(7, "opt1");
        wire1.writeMessage(8, Protovia.codec(Address.class), new Address("NYC", "5th Ave"));
        wire1.writeBool(31, true);
        wire1.writeString(50, "fifty");
        wire1.writeEnum(56, 1); // ACTIVE
        wire1.writeString(57, "tag1");
        wire1.writeInt32(58, 7);
        wire1.writeString(60, "end1");
        wire1.writeInt64(75, 75_000_000_000L);
        wire1.writeString(85, "rec-tail1");
        wire1.writeInt32(888, 12345);

        WideSplitRecord rec1 = Protovia.fromBytes(wire1.toByteArray(), WideSplitRecord.class);
        assertEquals(10, rec1.f1());
        assertEquals("first", rec1.f2());
        assertEquals(Status.UNRECOGNIZED, rec1.f3());
        assertEquals(List.of(1), rec1.f4());
        assertArrayEquals(new int[]{100}, rec1.f5());
        assertEquals(Optional.of("opt1"), rec1.f7());
        assertEquals(new Address("NYC", "5th Ave"), rec1.f8());
        assertTrue(rec1.f31());
        assertEquals("fifty", rec1.f50());
        assertEquals(Status.ACTIVE, rec1.f56());
        assertEquals(List.of("tag1"), rec1.f57());
        assertArrayEquals(new int[]{7}, rec1.f58());
        assertEquals("end1", rec1.f60());
        assertEquals(75_000_000_000L, rec1.f75());
        assertEquals("rec-tail1", rec1.f85());
        assertFalse(rec1.unknown().isEmpty());

        // Round-trip rec1 bytes and verify exact equality of fields.
        WideSplitRecord rt = Protovia.fromBytes(Protovia.toBytes(rec1), WideSplitRecord.class);
        assertEquals(rec1.f1(), rt.f1());
        assertEquals(rec1.f2(), rt.f2());
        assertEquals(Status.UNRECOGNIZED, rt.f3());
        assertArrayEquals(rec1.f5(), rt.f5());
        assertEquals(rec1.f60(), rt.f60());
        assertEquals(rec1.f75(), rt.f75());
        assertEquals(rec1.f85(), rt.f85());

        // Merge second wire payload into rec1: arrays & lists append, f3 becomes ACTIVE and removes stale unknown 3.
        ProtoWriter wire2 = ProtoWriter.growing();
        wire2.writeEnum(3, 1); // ACTIVE
        wire2.writeInt32(4, 2);
        wire2.writeInt32(5, 200);
        wire2.writeInt32(58, 8);
        wire2.writeString(60, "end2");
        wire2.writeString(85, "rec-tail2");

        WideSplitRecord merged = Protovia.mergeFrom(wire2.toByteArray(), rec1);
        assertEquals(10, merged.f1());
        assertEquals("first", merged.f2());
        assertEquals(Status.ACTIVE, merged.f3());
        assertEquals(List.of(1, 2), merged.f4());
        assertArrayEquals(new int[]{100, 200}, merged.f5());
        assertArrayEquals(new int[]{7, 8}, merged.f58());
        assertEquals("end2", merged.f60());
        assertEquals(75_000_000_000L, merged.f75());
        assertEquals("rec-tail2", merged.f85());
        List<Integer> mergedUnknownNums = readAllFieldNumbers(unknownBytes(merged.unknown()));
        assertFalse(mergedUnknownNums.contains(3));
        assertTrue(mergedUnknownNums.contains(888));
    }

    private static boolean hasDeclaredMethod(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredMethods()).anyMatch(m -> m.getName().equals(name));
    }

    private static byte[] unknownBytes(UnknownFields unknown) {
        ProtoWriter out = new ProtoWriter(unknown.serializedSize());
        unknown.writeTo(out);
        return out.finish();
    }

    private static byte[] packedBytes(int fieldNumber, byte[] payload) {
        ProtoWriter out = ProtoWriter.growing();
        out.writeTag(fieldNumber, WireType.LEN);
        out.writeBytesNoTag(payload);
        return out.toByteArray();
    }

    private static int backingArrayLength(Object primitiveList) throws Exception {
        Field values = primitiveList.getClass().getDeclaredField("values");
        values.setAccessible(true);
        return Array.getLength(values.get(primitiveList));
    }

    private static List<String> readStringMapKeys(byte[] wire, int fieldNumber) {
        List<String> keys = new ArrayList<>();
        ProtoReader r = new ProtoReader(wire);
        int tag;
        while ((tag = r.readTag()) != 0) {
            if (WireType.fieldNumber(tag) == fieldNumber) {
                int old = r.pushLengthDelimited();
                String key = "";
                int innerTag;
                while ((innerTag = r.readTag()) != 0) {
                    if (WireType.fieldNumber(innerTag) == 1) {
                        key = r.readString();
                    } else {
                        r.skipField();
                    }
                }
                r.popLimit(old);
                keys.add(key);
            } else {
                r.skipField();
            }
        }
        return keys;
    }

    private static List<Long> readUInt64MapKeys(byte[] wire, int fieldNumber) {
        List<Long> keys = new ArrayList<>();
        ProtoReader r = new ProtoReader(wire);
        int tag;
        while ((tag = r.readTag()) != 0) {
            if (WireType.fieldNumber(tag) == fieldNumber) {
                int old = r.pushLengthDelimited();
                long key = 0L;
                int innerTag;
                while ((innerTag = r.readTag()) != 0) {
                    if (WireType.fieldNumber(innerTag) == 1) {
                        key = r.readUInt64();
                    } else {
                        r.skipField();
                    }
                }
                r.popLimit(old);
                keys.add(key);
            } else {
                r.skipField();
            }
        }
        return keys;
    }

    private static List<Integer> readAllFieldNumbers(byte[] wire) {
        List<Integer> nums = new ArrayList<>();
        ProtoReader r = new ProtoReader(wire);
        int tag;
        while ((tag = r.readTag()) != 0) {
            nums.add(WireType.fieldNumber(tag));
            r.skipField();
        }
        return nums;
    }
}
