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

package io.github.rawvoid.protovia.wire;

import io.github.rawvoid.protovia.ProtoException;
import io.github.rawvoid.protovia.UnknownFields;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Rawvoid
 */
class UnknownFieldsTest {

    @Test
    void mergeUnknownInt32AndRewrite() {
        ProtoWriter in = ProtoWriter.growing();
        in.writeString(1, "a");
        in.writeInt32(15, 7);
        byte[] input = in.toByteArray();

        ProtoReader r = new ProtoReader(input);
        String name = null;
        UnknownFields unknown = UnknownFields.EMPTY;
        int tag;
        while ((tag = r.readTag()) != 0) {
            if (tag == WireType.tag(1, WireType.LEN)) {
                name = r.readString();
            } else {
                unknown = UnknownFields.merge(unknown, r);
            }
        }
        assertEquals("a", name);

        int size = CodedSize.string(1, name) + unknown.serializedSize();
        ProtoWriter out = new ProtoWriter(size);
        out.writeString(1, name);
        unknown.writeTo(out);

        ProtoReader back = new ProtoReader(out.finish());
        int extra = 0;
        while ((tag = back.readTag()) != 0) {
            if (tag == WireType.tag(1, WireType.LEN)) {
                assertEquals("a", back.readString());
            } else if (tag == WireType.tag(15, WireType.VARINT)) {
                extra = back.readInt32();
            } else {
                back.skipField();
            }
        }
        assertEquals(7, extra);
    }

    @Test
    void captureLengthDelimitedUnknown() {
        ProtoWriter in = ProtoWriter.growing();
        in.writeString(9, "xyz");
        byte[] input = in.toByteArray();

        ProtoReader r = new ProtoReader(input);
        assertEquals(WireType.tag(9, WireType.LEN), r.readTag());
        UnknownFields unknown = UnknownFields.merge(UnknownFields.EMPTY, r);
        assertEquals(input.length, unknown.serializedSize());

        ProtoWriter out = new ProtoWriter(unknown.serializedSize());
        unknown.writeTo(out);
        assertArrayEquals(input, out.finish());
    }

    @Test
    void mergeVarintUsesInt32EncodingForNegatives() {
        UnknownFields unknown = UnknownFields.mergeVarint(UnknownFields.EMPTY, 56, -1);
        assertEquals(CodedSize.uint32(56) + CodedSize.int32(-1), unknown.serializedSize());
        assertEquals(11, unknown.serializedSize());
        ProtoWriter out = new ProtoWriter(unknown.serializedSize());
        unknown.writeTo(out);
        ProtoReader back = new ProtoReader(out.finish());
        assertEquals(56, back.readTag());
        assertEquals(-1, back.readInt32());
    }

    @Test
    void emptyIsNoop() {
        assertTrue(UnknownFields.EMPTY.isEmpty());
        assertEquals(0, UnknownFields.EMPTY.serializedSize());
        ProtoWriter w = new ProtoWriter(0);
        UnknownFields.EMPTY.writeTo(w);
        assertArrayEquals(new byte[0], w.finish());
    }

    @Test
    void builderFiftyThousandUnknownFieldsIsLinearTimeAndRoundTrips() {
        final int fieldCount = 50_000;
        ProtoWriter in = ProtoWriter.growing();
        for (int i = 0; i < fieldCount; i++) {
            int fieldNumber = (i % 500) + 10;
            if ((i & 1) == 0) {
                in.writeInt32(fieldNumber, i - 25_000);
            } else {
                in.writeString(fieldNumber, "v" + i);
            }
        }
        byte[] input = in.toByteArray();

        ProtoReader reader = new ProtoReader(input);
        UnknownFields.Builder builder = UnknownFields.newBuilder();
        long startNanos = System.nanoTime();
        while (reader.readTag() != 0) {
            builder.merge(reader);
        }
        UnknownFields unknown = builder.build();
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;

        assertTrue(elapsedMs < 1_000L,
            "Capturing " + fieldCount + " unknown fields took " + elapsedMs + " ms (expected O(N) < 1000 ms)");
        assertFalse(unknown.isEmpty());
        assertEquals(input.length, unknown.serializedSize());

        ProtoWriter out = new ProtoWriter(unknown.serializedSize());
        unknown.writeTo(out);
        byte[] written = out.finish();
        assertArrayEquals(input, written);

        // Verify all 50,000 fields parse back identically
        ProtoReader verify = new ProtoReader(written);
        int seen = 0;
        int tag;
        while ((tag = verify.readTag()) != 0) {
            int expectedField = (seen % 500) + 10;
            assertEquals(expectedField, WireType.fieldNumber(tag));
            if ((seen & 1) == 0) {
                assertEquals(WireType.VARINT, WireType.getWireType(tag));
                assertEquals(seen - 25_000, verify.readInt32());
            } else {
                assertEquals(WireType.LEN, WireType.getWireType(tag));
                assertEquals("v" + seen, verify.readString());
            }
            seen++;
        }
        assertEquals(fieldCount, seen);
    }

    @Test
    void builderSeededFromExistingReturnsSameInstanceWhenUnmodified() {
        assertTrue(UnknownFields.newBuilder().isEmpty());
        assertSame(UnknownFields.EMPTY, UnknownFields.newBuilder().build());
        assertSame(UnknownFields.EMPTY, UnknownFields.newBuilder(null).build());
        assertSame(UnknownFields.EMPTY, UnknownFields.EMPTY.toBuilder().build());

        UnknownFields existing = UnknownFields.newBuilder()
            .mergeVarint(WireType.tag(3, WireType.VARINT), 42)
            .mergeVarint(WireType.tag(5, WireType.VARINT), -9)
            .build();

        assertSame(existing, UnknownFields.newBuilder(existing).build());
        assertSame(existing, existing.toBuilder().build());
        assertSame(existing, existing.toBuilder()
            .merge((UnknownFields) null)
            .merge(UnknownFields.EMPTY)
            .removeField(0)
            .removeField(-1)
            .removeField(99)
            .removeFields()
            .removeFields((int[]) null)
            .removeFields(98, 99)
            .build());
    }

    @Test
    void builderModificationDoesNotMutateOriginalOrEarlierSnapshots() {
        UnknownFields original = UnknownFields.newBuilder()
            .mergeVarint(WireType.tag(1, WireType.VARINT), 10)
            .build();
        byte[] originalBytes = toBytes(original);

        UnknownFields.Builder builder = original.toBuilder();
        assertFalse(builder.isEmpty());
        builder.mergeVarint(WireType.tag(2, WireType.VARINT), 20);
        UnknownFields snap1 = builder.build();
        byte[] snap1Bytes = toBytes(snap1);

        assertArrayEquals(originalBytes, toBytes(original));
        assertNotEquals(original, snap1);
        assertSame(snap1, builder.build());

        // Grow to exactly 64 bytes (initial ProtoWriter.growing() capacity) to exercise zero-copy toByteArray path
        UnknownFields.Builder exact64 = UnknownFields.newBuilder();
        for (int i = 0; i < 32; i++) {
            exact64.mergeVarint(WireType.tag(1, WireType.VARINT), i);
        }
        UnknownFields snap64 = exact64.build();
        assertEquals(64, snap64.serializedSize());
        byte[] snap64Bytes = toBytes(snap64);

        exact64.mergeVarint(WireType.tag(2, WireType.VARINT), 99);
        UnknownFields snap66 = exact64.build();
        assertEquals(64, snap64.serializedSize());
        assertArrayEquals(snap64Bytes, toBytes(snap64));
        assertEquals(66, snap66.serializedSize());

        // Removing from builder after build() must not mutate earlier snapshot
        builder.removeField(1);
        UnknownFields snap2 = builder.build();
        assertArrayEquals(snap1Bytes, toBytes(snap1));
        assertEquals(2, snap2.serializedSize());
    }

    @Test
    void builderMergeVarintHandlesNegativesAndValidatesTag() {
        int tag7 = WireType.tag(7, WireType.VARINT);
        int tag100 = WireType.tag(100, WireType.VARINT);
        UnknownFields u = UnknownFields.newBuilder()
            .mergeVarint(tag7, -1)
            .mergeVarint(tag100, Integer.MIN_VALUE)
            .mergeVarint(tag7, 0)
            .mergeVarint(tag100, Integer.MAX_VALUE)
            .build();

        int expectedSize = CodedSize.uint32(tag7) + CodedSize.int32(-1)
            + CodedSize.uint32(tag100) + CodedSize.int32(Integer.MIN_VALUE)
            + CodedSize.uint32(tag7) + CodedSize.int32(0)
            + CodedSize.uint32(tag100) + CodedSize.int32(Integer.MAX_VALUE);
        assertEquals(expectedSize, u.serializedSize());

        ProtoReader back = new ProtoReader(toBytes(u));
        assertEquals(tag7, back.readTag());
        assertEquals(-1, back.readInt32());
        assertEquals(tag100, back.readTag());
        assertEquals(Integer.MIN_VALUE, back.readInt32());
        assertEquals(tag7, back.readTag());
        assertEquals(0, back.readInt32());
        assertEquals(tag100, back.readTag());
        assertEquals(Integer.MAX_VALUE, back.readInt32());
        assertEquals(0, back.readTag());

        UnknownFields.Builder b = UnknownFields.newBuilder();
        assertThrows(ProtoException.class, () -> b.mergeVarint(0, 1));
        assertThrows(ProtoException.class, () -> b.mergeVarint(WireType.tag(0, WireType.VARINT), 1));
        assertThrows(ProtoException.class, () -> b.mergeVarint(WireType.tag(7, WireType.LEN), 1));
        assertThrows(ProtoException.class, () -> UnknownFields.mergeVarint(UnknownFields.EMPTY, 0, 1));
        assertThrows(ProtoException.class, () -> UnknownFields.mergeVarint(UnknownFields.EMPTY, WireType.tag(7, WireType.I32), 1));
    }

    @Test
    void builderMergeRangeCapturesMapEntryLosslesslyWithOffset() {
        // Build two map entries on field 4:
        //   entry 1: key="unknown", value=999 (unrecognized enum), extra unknown inner field #7="keep"
        //   entry 2: key="known",   value=1   (recognized enum)
        ProtoWriter entry1Body = ProtoWriter.growing();
        entry1Body.writeString(1, "unknown");
        entry1Body.writeEnum(2, 999);
        entry1Body.writeString(7, "keep");
        byte[] entry1Bytes = entry1Body.toByteArray();

        ProtoWriter entry2Body = ProtoWriter.growing();
        entry2Body.writeString(1, "known");
        entry2Body.writeEnum(2, 1);
        byte[] entry2Bytes = entry2Body.toByteArray();

        ProtoWriter msg = ProtoWriter.growing();
        msg.writeString(1, "header");
        msg.writeBytes(4, entry1Bytes);
        msg.writeBytes(4, entry2Bytes);
        msg.writeInt32(9, 123);
        byte[] msgBytes = msg.toByteArray();

        // Embed message at a non-zero offset to verify start/end bounds handling
        int offset = 7;
        byte[] padded = new byte[offset + msgBytes.length + 5];
        System.arraycopy(msgBytes, 0, padded, offset, msgBytes.length);

        ProtoReader reader = new ProtoReader(padded, offset, msgBytes.length);
        assertEquals(offset, reader.position());
        UnknownFields.Builder unknownBuilder = UnknownFields.newBuilder();
        int mapTag = WireType.tag(4, WireType.LEN);
        int tag;
        while ((tag = reader.readTag()) != 0) {
            if (tag == WireType.tag(1, WireType.LEN)) {
                assertEquals("header", reader.readString());
            } else if (tag == mapTag) {
                int entryStart = reader.position();
                int oldLimit = reader.pushLengthDelimited();
                int enumVal = 0;
                int innerTag;
                while ((innerTag = reader.readTag()) != 0) {
                    if (innerTag == WireType.tag(1, WireType.LEN)) {
                        reader.readString();
                    } else if (innerTag == WireType.tag(2, WireType.VARINT)) {
                        enumVal = reader.readEnum();
                    } else {
                        reader.skipField(innerTag);
                    }
                }
                reader.popLimit(oldLimit);
                if (enumVal == 999) {
                    unknownBuilder.mergeRange(reader, tag, entryStart, reader.position());
                }
            } else {
                unknownBuilder.merge(reader);
            }
        }
        UnknownFields captured = unknownBuilder.build();

        ProtoWriter expected = ProtoWriter.growing();
        expected.writeBytes(4, entry1Bytes);
        expected.writeInt32(9, 123);
        assertArrayEquals(expected.toByteArray(), toBytes(captured));

        // Verify range & tag validation on ProtoReader
        ProtoWriter scratch = ProtoWriter.growing();
        assertThrows(ProtoException.class, () -> reader.writeRangeTo(mapTag, offset - 1, offset, scratch));
        assertThrows(ProtoException.class, () -> reader.writeRangeTo(mapTag, offset + 2, offset + 1, scratch));
        assertThrows(ProtoException.class, () -> reader.writeRangeTo(mapTag, offset, offset + msgBytes.length + 1, scratch));
        assertThrows(ProtoException.class, () -> reader.writeRangeTo(0, offset, offset, scratch));
        assertThrows(ProtoException.class, () -> reader.writeRangeTo(WireType.tag(4, WireType.END_GROUP), offset, offset, scratch));
        assertThrows(ProtoException.class, () -> reader.writeRangeTo(WireType.tag(4, 6), offset, offset, scratch));

        // captureFieldTo before readTag() (lastTag == 0) must reject invalid tag
        ProtoReader fresh = new ProtoReader(msgBytes);
        assertThrows(ProtoException.class, () -> fresh.captureFieldTo(scratch));
        assertThrows(ProtoException.class, () -> UnknownFields.newBuilder().merge(fresh));
    }

    @Test
    void builderMergeOtherUnknownFields() {
        UnknownFields u1 = UnknownFields.newBuilder()
            .mergeVarint(WireType.tag(1, WireType.VARINT), 11)
            .build();
        UnknownFields u2 = UnknownFields.newBuilder()
            .mergeVarint(WireType.tag(2, WireType.VARINT), 22)
            .build();

        assertSame(u1, UnknownFields.newBuilder().merge(u1).build());

        UnknownFields combined = u1.toBuilder().merge(u2).build();
        ProtoWriter expected = ProtoWriter.growing();
        expected.writeInt32(1, 11);
        expected.writeInt32(2, 22);
        assertArrayEquals(expected.toByteArray(), toBytes(combined));
        assertEquals(2, u1.serializedSize());
        assertEquals(2, u2.serializedSize());
    }

    @Test
    void builderRemoveFieldAndRemoveFieldsAcrossAllWireTypesIncludingGroups() {
        UnknownFields all = buildAllWireTypesUnknownFields();

        // Remove field 1 (VARINT, appears twice at outer level, AND once inside group #5 which must stay intact)
        UnknownFields without1 = all.toBuilder().removeField(1).build();
        ProtoWriter expectedWithout1 = ProtoWriter.growing();
        writeI64Field(expectedWithout1);
        writeLenField(expectedWithout1);
        writeI32Field(expectedWithout1);
        writeGroupField(expectedWithout1);
        writeCollidingField65(expectedWithout1);
        assertArrayEquals(expectedWithout1.toByteArray(), toBytes(without1));

        // Remove field 2 (I64)
        UnknownFields without2 = all.toBuilder().removeField(2).build();
        ProtoWriter expectedWithout2 = ProtoWriter.growing();
        writeFirstVarintField(expectedWithout2);
        writeLenField(expectedWithout2);
        writeI32Field(expectedWithout2);
        writeGroupField(expectedWithout2);
        writeSecondVarintField(expectedWithout2);
        writeCollidingField65(expectedWithout2);
        assertArrayEquals(expectedWithout2.toByteArray(), toBytes(without2));

        // Remove field 3 (LEN)
        UnknownFields without3 = all.toBuilder().removeField(3).build();
        ProtoWriter expectedWithout3 = ProtoWriter.growing();
        writeFirstVarintField(expectedWithout3);
        writeI64Field(expectedWithout3);
        writeI32Field(expectedWithout3);
        writeGroupField(expectedWithout3);
        writeSecondVarintField(expectedWithout3);
        writeCollidingField65(expectedWithout3);
        assertArrayEquals(expectedWithout3.toByteArray(), toBytes(without3));

        // Remove field 4 (I32)
        UnknownFields without4 = all.toBuilder().removeField(4).build();
        ProtoWriter expectedWithout4 = ProtoWriter.growing();
        writeFirstVarintField(expectedWithout4);
        writeI64Field(expectedWithout4);
        writeLenField(expectedWithout4);
        writeGroupField(expectedWithout4);
        writeSecondVarintField(expectedWithout4);
        writeCollidingField65(expectedWithout4);
        assertArrayEquals(expectedWithout4.toByteArray(), toBytes(without4));

        // Remove field 5 (START_GROUP / END_GROUP including nested group #6)
        UnknownFields without5 = all.toBuilder().removeField(5).build();
        ProtoWriter expectedWithout5 = ProtoWriter.growing();
        writeFirstVarintField(expectedWithout5);
        writeI64Field(expectedWithout5);
        writeLenField(expectedWithout5);
        writeI32Field(expectedWithout5);
        writeSecondVarintField(expectedWithout5);
        writeCollidingField65(expectedWithout5);
        assertArrayEquals(expectedWithout5.toByteArray(), toBytes(without5));

        // Remove multiple fields at once via removeFields(int...)
        UnknownFields without245 = all.toBuilder().removeFields(2, 4, 5).build();
        ProtoWriter expectedWithout245 = ProtoWriter.growing();
        writeFirstVarintField(expectedWithout245);
        writeLenField(expectedWithout245);
        writeSecondVarintField(expectedWithout245);
        writeCollidingField65(expectedWithout245);
        assertArrayEquals(expectedWithout245.toByteArray(), toBytes(without245));

        // Remove all fields yields EMPTY
        UnknownFields.Builder drain = all.toBuilder().removeFields(1, 2, 3, 4, 5, 65);
        assertTrue(drain.isEmpty());
        assertSame(UnknownFields.EMPTY, drain.build());
    }

    @Test
    void builderRemoveFieldHandlesBitmaskCollisionsAndRepeatedCallsFastPath() {
        // 7, 71 (= 7 + 64), and 135 (= 7 + 128) all map to the same bit (bit 7) in the 64-bit mask
        UnknownFields.Builder b = UnknownFields.newBuilder()
            .mergeVarint(WireType.tag(7, WireType.VARINT), 100)
            .mergeVarint(WireType.tag(71, WireType.VARINT), 200)
            .mergeVarint(WireType.tag(135, WireType.VARINT), 300);

        b.removeField(7);
        ProtoWriter expected71And135 = ProtoWriter.growing();
        expected71And135.writeInt32(71, 200);
        expected71And135.writeInt32(135, 300);
        assertArrayEquals(expected71And135.toByteArray(), toBytes(b.build()));

        b.removeField(71);
        ProtoWriter expected135 = ProtoWriter.growing();
        expected135.writeInt32(135, 300);
        assertArrayEquals(expected135.toByteArray(), toBytes(b.build()));

        b.removeField(135);
        assertTrue(b.isEmpty());
        assertSame(UnknownFields.EMPTY, b.build());

        // Fast-path regression: repeated removeField(7) when builder holds large field #99 must be O(1)
        ProtoWriter large = ProtoWriter.growing();
        large.writeBytes(99, new byte[16 * 1024]);
        ProtoReader reader = new ProtoReader(large.toByteArray());
        assertNotEquals(0, reader.readTag());
        UnknownFields.Builder largeBuilder = UnknownFields.newBuilder().merge(reader);
        long startNanos = System.nanoTime();
        for (int i = 0; i < 50_000; i++) {
            largeBuilder.removeField(7);
        }
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        assertTrue(elapsedMs < 500L, "50,000 absent removeField calls took " + elapsedMs + " ms");
        assertEquals(large.toByteArray().length, largeBuilder.build().serializedSize());
    }

    @Test
    void serializedSizeExcludingAndWriteToExcludingCoverAllWireTypesAndOverloads() {
        UnknownFields all = buildAllWireTypesUnknownFields();
        byte[] allBytes = toBytes(all);

        // EMPTY cases
        assertExcluding(UnknownFields.EMPTY, new byte[0]);
        assertExcluding(UnknownFields.EMPTY, new byte[0], 1, 2);
        assertExcludingPartial(UnknownFields.EMPTY, new byte[0], new int[]{1, 2}, 0);
        assertExcludingPartial(UnknownFields.EMPTY, new byte[0], new int[]{1, 2}, 2);

        // count == 0 fast path (varargs empty, null, and partial array count == 0)
        assertExcluding(all, allBytes);
        assertEquals(allBytes.length, all.serializedSizeExcluding((int[]) null));
        ProtoWriter nullExcludedOut = new ProtoWriter(allBytes.length);
        all.writeToExcluding(nullExcludedOut, (int[]) null);
        assertArrayEquals(allBytes, nullExcludedOut.finish());
        assertExcludingPartial(all, allBytes, new int[]{1, 2, 3}, 0);
        assertExcludingPartial(all, allBytes, null, 0);

        // Absent / non-positive excluded field numbers
        assertExcluding(all, allBytes, 99, 100);
        assertExcluding(all, allBytes, 0, -5);

        // Exclude each wire type individually: VARINT (#1), I64 (#2), LEN (#3), I32 (#4), GROUP (#5)
        ProtoWriter expectedNo1 = ProtoWriter.growing();
        writeI64Field(expectedNo1);
        writeLenField(expectedNo1);
        writeI32Field(expectedNo1);
        writeGroupField(expectedNo1);
        writeCollidingField65(expectedNo1);
        assertExcluding(all, expectedNo1.toByteArray(), 1);
        assertExcludingPartial(all, expectedNo1.toByteArray(), new int[]{1, 2, 3, 4}, 1);

        ProtoWriter expectedNo2 = ProtoWriter.growing();
        writeFirstVarintField(expectedNo2);
        writeLenField(expectedNo2);
        writeI32Field(expectedNo2);
        writeGroupField(expectedNo2);
        writeSecondVarintField(expectedNo2);
        writeCollidingField65(expectedNo2);
        assertExcluding(all, expectedNo2.toByteArray(), 2);

        ProtoWriter expectedNo3 = ProtoWriter.growing();
        writeFirstVarintField(expectedNo3);
        writeI64Field(expectedNo3);
        writeI32Field(expectedNo3);
        writeGroupField(expectedNo3);
        writeSecondVarintField(expectedNo3);
        writeCollidingField65(expectedNo3);
        assertExcluding(all, expectedNo3.toByteArray(), 3);

        ProtoWriter expectedNo4 = ProtoWriter.growing();
        writeFirstVarintField(expectedNo4);
        writeI64Field(expectedNo4);
        writeLenField(expectedNo4);
        writeGroupField(expectedNo4);
        writeSecondVarintField(expectedNo4);
        writeCollidingField65(expectedNo4);
        assertExcluding(all, expectedNo4.toByteArray(), 4);

        ProtoWriter expectedNo5 = ProtoWriter.growing();
        writeFirstVarintField(expectedNo5);
        writeI64Field(expectedNo5);
        writeLenField(expectedNo5);
        writeI32Field(expectedNo5);
        writeSecondVarintField(expectedNo5);
        writeCollidingField65(expectedNo5);
        assertExcluding(all, expectedNo5.toByteArray(), 5);

        // Exclude subset using partial array (only first 2 entries active: #1 and #5; trailing #2 and #3 ignored!)
        ProtoWriter expectedNo1And5 = ProtoWriter.growing();
        writeI64Field(expectedNo1And5);
        writeLenField(expectedNo1And5);
        writeI32Field(expectedNo1And5);
        writeCollidingField65(expectedNo1And5);
        assertExcludingPartial(all, expectedNo1And5.toByteArray(), new int[]{1, 5, 2, 3}, 2);
        assertExcluding(all, expectedNo1And5.toByteArray(), 1, 5);

        // Exclude last field only (#65) to verify bitmask collision with #1 keeps #1 intact
        ProtoWriter expectedNo65 = ProtoWriter.growing();
        writeFirstVarintField(expectedNo65);
        writeI64Field(expectedNo65);
        writeLenField(expectedNo65);
        writeI32Field(expectedNo65);
        writeGroupField(expectedNo65);
        writeSecondVarintField(expectedNo65);
        assertExcluding(all, expectedNo65.toByteArray(), 65);

        // Exclude all fields -> size 0, writes 0 bytes
        assertExcluding(all, new byte[0], 1, 2, 3, 4, 5, 65);
        assertExcludingPartial(all, new byte[0], new int[]{1, 2, 3, 4, 5, 65, 99}, 6);

        // Invalid count checks
        ProtoWriter scratch = ProtoWriter.growing();
        assertThrows(IllegalArgumentException.class, () -> all.serializedSizeExcluding(new int[]{1}, -1));
        assertThrows(IllegalArgumentException.class, () -> all.serializedSizeExcluding(new int[]{1}, 2));
        assertThrows(IllegalArgumentException.class, () -> all.serializedSizeExcluding(null, 1));
        assertThrows(IllegalArgumentException.class, () -> all.writeToExcluding(scratch, new int[]{1}, -1));
        assertThrows(IllegalArgumentException.class, () -> all.writeToExcluding(scratch, new int[]{1}, 2));
        assertThrows(IllegalArgumentException.class, () -> all.writeToExcluding(scratch, null, 1));
    }

    @Test
    void legacyStaticMergeAndMergeVarintHandleNullExisting() {
        ProtoWriter in = ProtoWriter.growing();
        in.writeInt32(8, 55);
        ProtoReader r = new ProtoReader(in.toByteArray());
        assertEquals(WireType.tag(8, WireType.VARINT), r.readTag());

        UnknownFields u1 = UnknownFields.merge(null, r);
        assertArrayEquals(in.toByteArray(), toBytes(u1));

        UnknownFields u2 = UnknownFields.mergeVarint(null, WireType.tag(9, WireType.VARINT), -3);
        ProtoWriter expected = ProtoWriter.growing();
        expected.writeInt32(9, -3);
        assertArrayEquals(expected.toByteArray(), toBytes(u2));
    }

    @Test
    void deepNestedGroupExceedingDefaultMaxDepthCanBeFilteredAndRemoved() {
        final int depth = 120;
        ProtoWriter groupWriter = ProtoWriter.growing();
        for (int i = 0; i < depth; i++) {
            groupWriter.writeTag(i == 0 ? 5 : 6, WireType.START_GROUP);
        }
        groupWriter.writeInt32(1, 42);
        for (int i = depth - 1; i >= 0; i--) {
            groupWriter.writeTag(i == 0 ? 5 : 6, WireType.END_GROUP);
        }
        byte[] groupBytes = groupWriter.toByteArray();

        ProtoWriter siblingWriter = ProtoWriter.growing();
        siblingWriter.writeInt32(9, 77);
        byte[] siblingBytes = siblingWriter.toByteArray();

        ProtoWriter allWriter = ProtoWriter.growing();
        allWriter.writeRawBytes(groupBytes, 0, groupBytes.length);
        allWriter.writeRawBytes(siblingBytes, 0, siblingBytes.length);
        byte[] raw = allWriter.toByteArray();

        // Sanity check: default maxDepth (100) rejects depth 120
        ProtoReader defaultDepthReader = new ProtoReader(raw);
        assertEquals(WireType.tag(5, WireType.START_GROUP), defaultDepthReader.readTag());
        assertThrows(ProtoException.class, () -> UnknownFields.newBuilder().merge(defaultDepthReader));

        // Capture with higher caller-configured maxDepth = 200
        ProtoReader deepReader = new ProtoReader(raw, 0, raw.length, ProtoReader.DEFAULT_MAX_MESSAGE_SIZE, 200);
        UnknownFields.Builder builder = UnknownFields.newBuilder();
        while (deepReader.readTag() != 0) {
            builder.merge(deepReader);
        }
        UnknownFields captured = builder.build();
        assertArrayEquals(raw, toBytes(captured));

        // Filtering and removing must succeed even though depth (120) > DEFAULT_MAX_DEPTH (100)
        assertExcluding(captured, siblingBytes, 5);
        assertExcluding(captured, groupBytes, 9);
        assertExcludingPartial(captured, siblingBytes, new int[]{5, 9}, 1);
        assertExcludingPartial(captured, new byte[0], new int[]{5, 9}, 2);

        assertArrayEquals(siblingBytes, toBytes(captured.toBuilder().removeField(5).build()));
        assertArrayEquals(groupBytes, toBytes(captured.toBuilder().removeField(9).build()));
        assertArrayEquals(siblingBytes, toBytes(captured.toBuilder().removeFields(5, 99).build()));
        assertSame(UnknownFields.EMPTY, captured.toBuilder().removeFields(5, 9).build());
    }

    @Test
    void builderMergeUnknownFieldsInvalidatesPresentMask() {
        UnknownFields u1 = UnknownFields.newBuilder()
            .mergeVarint(WireType.tag(1, WireType.VARINT), 11)
            .build();
        UnknownFields u2 = UnknownFields.newBuilder()
            .mergeVarint(WireType.tag(2, WireType.VARINT), 22)
            .build();
        byte[] u1Bytes = toBytes(u1);

        // Builder with field 1 -> merge(u2 with field 2) -> removeField(2) removes field 2
        UnknownFields.Builder b1 = UnknownFields.newBuilder()
            .mergeVarint(WireType.tag(1, WireType.VARINT), 11);
        b1.merge(u2);
        b1.removeField(2);
        assertArrayEquals(u1Bytes, toBytes(b1.build()));

        // Seeded builder with tightened presentMask -> merge(u2) -> removeField(2) removes field 2
        UnknownFields.Builder bTight = u1.toBuilder();
        bTight.removeField(99); // tightens presentMask to bit 1 only
        bTight.merge(u2);       // must reset presentMask
        bTight.removeField(2);
        assertArrayEquals(u1Bytes, toBytes(bTight.build()));

        // Empty builder -> merge(u1) -> removeField(1) yields EMPTY without mutating u1
        UnknownFields.Builder bEmpty = UnknownFields.newBuilder().merge(u1);
        bEmpty.removeField(1);
        assertTrue(bEmpty.isEmpty());
        assertSame(UnknownFields.EMPTY, bEmpty.build());
        assertArrayEquals(u1Bytes, toBytes(u1));
    }

    @Test
    void bitmaskBoundariesAndMaxFieldNumberNegativeSignedTags() {
        final int f64 = 64;
        final int f128 = 128;
        final int fMax = WireType.MAX_FIELD_NUMBER; // 536_870_911; signed tag is negative!
        assertTrue(WireType.tag(fMax, WireType.VARINT) < 0);
        assertTrue(WireType.tag(fMax, WireType.LEN) < 0);

        // Build via merge(ProtoReader) with VARINT on 64 & 128 and LEN on MAX_FIELD_NUMBER
        ProtoWriter raw = ProtoWriter.growing();
        raw.writeInt32(f64, 640);
        raw.writeInt32(f128, -1280);
        raw.writeString(fMax, "max-field");
        byte[] allBytes = raw.toByteArray();

        ProtoReader reader = new ProtoReader(allBytes);
        UnknownFields.Builder fromReader = UnknownFields.newBuilder();
        while (reader.readTag() != 0) {
            fromReader.merge(reader);
        }
        UnknownFields u = fromReader.build();
        assertArrayEquals(allBytes, toBytes(u));

        // Build via mergeVarint for 64, 128, and MAX_FIELD_NUMBER (negative signed tag + negative value)
        UnknownFields uVarints = UnknownFields.newBuilder()
            .mergeVarint(WireType.tag(f64, WireType.VARINT), 640)
            .mergeVarint(WireType.tag(f128, WireType.VARINT), -1280)
            .mergeVarint(WireType.tag(fMax, WireType.VARINT), -1)
            .build();
        ProtoWriter expectedVarints = ProtoWriter.growing();
        expectedVarints.writeInt32(f64, 640);
        expectedVarints.writeInt32(f128, -1280);
        expectedVarints.writeInt32(fMax, -1);
        assertArrayEquals(expectedVarints.toByteArray(), toBytes(uVarints));

        // Verify removeField / removeFields across bit-0 collision (64 & 128) and bit-63 (MAX_FIELD_NUMBER)
        ProtoWriter expected128AndMax = ProtoWriter.growing();
        expected128AndMax.writeInt32(f128, -1280);
        expected128AndMax.writeString(fMax, "max-field");
        assertArrayEquals(expected128AndMax.toByteArray(), toBytes(u.toBuilder().removeField(f64).build()));

        ProtoWriter expected64And128 = ProtoWriter.growing();
        expected64And128.writeInt32(f64, 640);
        expected64And128.writeInt32(f128, -1280);
        assertArrayEquals(expected64And128.toByteArray(), toBytes(u.toBuilder().removeField(fMax).build()));

        ProtoWriter expected128Only = ProtoWriter.growing();
        expected128Only.writeInt32(f128, -1280);
        assertArrayEquals(expected128Only.toByteArray(), toBytes(u.toBuilder().removeFields(f64, fMax).build()));
        assertSame(UnknownFields.EMPTY, u.toBuilder().removeFields(f64, f128, fMax).build());

        // Verify serializedSizeExcluding / writeToExcluding for 64, 128, MAX_FIELD_NUMBER
        assertExcluding(u, expected128AndMax.toByteArray(), f64);
        assertExcluding(u, expected64And128.toByteArray(), fMax);
        assertExcluding(u, expected128Only.toByteArray(), f64, fMax);
        assertExcludingPartial(u, expected128Only.toByteArray(), new int[]{f64, fMax, f128}, 2);
        assertExcluding(u, new byte[0], f64, f128, fMax);

        ProtoWriter expectedVarintsNoMax = ProtoWriter.growing();
        expectedVarintsNoMax.writeInt32(f64, 640);
        expectedVarintsNoMax.writeInt32(f128, -1280);
        assertExcluding(uVarints, expectedVarintsNoMax.toByteArray(), fMax);
        assertArrayEquals(expectedVarintsNoMax.toByteArray(), toBytes(uVarints.toBuilder().removeField(fMax).build()));
    }

    @Test
    void protoReaderCaptureAndWriteRangeEdgeCases() {
        int tag1 = WireType.tag(1, WireType.VARINT);

        // writeRangeTo with empty range (payloadStart == payloadEnd) at start and end, including non-zero offset
        byte[] padded = new byte[10];
        ProtoReader sliced = new ProtoReader(padded, 3, 4); // start=3, end=7
        ProtoWriter emptyAtStart = ProtoWriter.growing();
        sliced.writeRangeTo(tag1, 3, 3, emptyAtStart);
        ProtoWriter expectedTagOnly = ProtoWriter.growing();
        expectedTagOnly.writeUInt32NoTag(tag1);
        assertArrayEquals(expectedTagOnly.toByteArray(), emptyAtStart.toByteArray());

        ProtoWriter emptyAtEnd = ProtoWriter.growing();
        sliced.writeRangeTo(tag1, 7, 7, emptyAtEnd);
        assertArrayEquals(expectedTagOnly.toByteArray(), emptyAtEnd.toByteArray());

        // writeRangeTo with wire type 7 and out == null
        ProtoWriter scratch = ProtoWriter.growing();
        assertThrows(ProtoException.class, () -> sliced.writeRangeTo(WireType.tag(1, 7), 3, 3, scratch));
        assertThrows(NullPointerException.class, () -> sliced.writeRangeTo(tag1, 3, 3, null));

        // captureField() with lastTag == 0
        ProtoReader fresh = new ProtoReader(padded);
        assertThrows(ProtoException.class, fresh::captureField);
        assertThrows(NullPointerException.class, () -> fresh.captureFieldTo(null));

        // captureField() with START_GROUP captures exact group bytes
        ProtoWriter groupW = ProtoWriter.growing();
        writeGroupField(groupW);
        byte[] groupBytes = groupW.toByteArray();
        ProtoReader groupReader = new ProtoReader(groupBytes);
        assertEquals(WireType.tag(5, WireType.START_GROUP), groupReader.readTag());
        assertArrayEquals(groupBytes, groupReader.captureField());
        assertEquals(0, groupReader.readTag());

        // captureField(), captureFieldTo(), and Builder.merge() on END_GROUP, wire type 6, and wire type 7
        UnknownFields existing = UnknownFields.newBuilder()
            .mergeVarint(WireType.tag(1, WireType.VARINT), 7)
            .build();
        int[] badTags = {
            WireType.tag(2, WireType.END_GROUP),
            WireType.tag(2, 6),
            WireType.tag(2, 7)
        };
        for (int badTag : badTags) {
            ProtoWriter badBuf = ProtoWriter.growing();
            badBuf.writeUInt32NoTag(badTag);
            byte[] badBytes = badBuf.toByteArray();

            ProtoReader r1 = new ProtoReader(badBytes);
            assertEquals(badTag, r1.readTag());
            assertThrows(ProtoException.class, r1::captureField);

            ProtoReader r2 = new ProtoReader(badBytes);
            assertEquals(badTag, r2.readTag());
            ProtoWriter out = ProtoWriter.growing();
            assertThrows(ProtoException.class, () -> r2.captureFieldTo(out));
            assertEquals(0, out.position());

            ProtoReader r3 = new ProtoReader(badBytes);
            assertEquals(badTag, r3.readTag());
            UnknownFields.Builder seeded = existing.toBuilder();
            assertThrows(ProtoException.class, () -> seeded.merge(r3));
            // Validation failed before ensureBuffer(), so seeded base was never copied
            assertSame(existing, seeded.build());
        }
    }

    @Test
    void filteringNullWriterInvalidCountOnEmptyAndDuplicateExcludedFields() {
        UnknownFields all = buildAllWireTypesUnknownFields();

        // writeToExcluding(null, ...) throws NPE on both EMPTY and non-empty
        assertThrows(NullPointerException.class, () -> UnknownFields.EMPTY.writeToExcluding(null));
        assertThrows(NullPointerException.class, () -> UnknownFields.EMPTY.writeToExcluding(null, 1));
        assertThrows(NullPointerException.class, () -> UnknownFields.EMPTY.writeToExcluding(null, new int[]{1}, 1));
        assertThrows(NullPointerException.class, () -> all.writeToExcluding(null));
        assertThrows(NullPointerException.class, () -> all.writeToExcluding(null, 1));
        assertThrows(NullPointerException.class, () -> all.writeToExcluding(null, new int[]{1}, 1));

        // Invalid count on EMPTY throws IllegalArgumentException for both size and write
        ProtoWriter scratch = ProtoWriter.growing();
        assertThrows(IllegalArgumentException.class, () -> UnknownFields.EMPTY.serializedSizeExcluding(new int[]{1}, -1));
        assertThrows(IllegalArgumentException.class, () -> UnknownFields.EMPTY.serializedSizeExcluding(new int[]{1}, 2));
        assertThrows(IllegalArgumentException.class, () -> UnknownFields.EMPTY.serializedSizeExcluding(null, 1));
        assertThrows(IllegalArgumentException.class, () -> UnknownFields.EMPTY.writeToExcluding(scratch, new int[]{1}, -1));
        assertThrows(IllegalArgumentException.class, () -> UnknownFields.EMPTY.writeToExcluding(scratch, new int[]{1}, 2));
        assertThrows(IllegalArgumentException.class, () -> UnknownFields.EMPTY.writeToExcluding(scratch, null, 1));

        // Duplicate excluded field numbers in removeFields, serializedSizeExcluding, and writeToExcluding
        ProtoWriter expectedWithout2 = ProtoWriter.growing();
        writeFirstVarintField(expectedWithout2);
        writeLenField(expectedWithout2);
        writeI32Field(expectedWithout2);
        writeGroupField(expectedWithout2);
        writeSecondVarintField(expectedWithout2);
        writeCollidingField65(expectedWithout2);
        byte[] without2Bytes = expectedWithout2.toByteArray();

        assertArrayEquals(without2Bytes, toBytes(all.toBuilder().removeFields(2, 2).build()));
        assertExcluding(all, without2Bytes, 2, 2);
        assertExcludingPartial(all, without2Bytes, new int[]{2, 2, 3}, 2);
    }

    private static UnknownFields buildAllWireTypesUnknownFields() {
        ProtoWriter w = ProtoWriter.growing();
        writeFirstVarintField(w);
        writeI64Field(w);
        writeLenField(w);
        writeI32Field(w);
        writeGroupField(w);
        writeSecondVarintField(w);
        writeCollidingField65(w);

        ProtoReader r = new ProtoReader(w.toByteArray());
        UnknownFields.Builder b = UnknownFields.newBuilder();
        while (r.readTag() != 0) {
            b.merge(r);
        }
        return b.build();
    }

    private static void writeFirstVarintField(ProtoWriter w) {
        w.writeInt32(1, 42);
    }

    private static void writeI64Field(ProtoWriter w) {
        w.writeFixed64(2, 0x0102030405060708L);
    }

    private static void writeLenField(ProtoWriter w) {
        w.writeString(3, "hello-world");
    }

    private static void writeI32Field(ProtoWriter w) {
        w.writeFixed32(4, 0x12345678);
    }

    private static void writeGroupField(ProtoWriter w) {
        w.writeTag(5, WireType.START_GROUP);
        w.writeInt32(1, 999);
        w.writeFixed64(2, 0x1122334455667788L);
        w.writeTag(6, WireType.START_GROUP);
        w.writeString(3, "nested-group");
        w.writeTag(6, WireType.END_GROUP);
        w.writeTag(5, WireType.END_GROUP);
    }

    private static void writeSecondVarintField(ProtoWriter w) {
        w.writeInt32(1, -7);
    }

    private static void writeCollidingField65(ProtoWriter w) {
        w.writeInt32(65, 650);
    }

    private static void assertExcluding(UnknownFields u, byte[] expected, int... excluded) {
        int size = u.serializedSizeExcluding(excluded);
        assertEquals(expected.length, size);
        ProtoWriter out = new ProtoWriter(size);
        u.writeToExcluding(out, excluded);
        assertArrayEquals(expected, out.finish());
    }

    private static void assertExcludingPartial(UnknownFields u, byte[] expected, int[] excluded, int count) {
        int size = u.serializedSizeExcluding(excluded, count);
        assertEquals(expected.length, size);
        ProtoWriter out = new ProtoWriter(size);
        u.writeToExcluding(out, excluded, count);
        assertArrayEquals(expected, out.finish());
    }

    private static byte[] toBytes(UnknownFields u) {
        ProtoWriter out = new ProtoWriter(u.serializedSize());
        u.writeTo(out);
        return out.finish();
    }
}
