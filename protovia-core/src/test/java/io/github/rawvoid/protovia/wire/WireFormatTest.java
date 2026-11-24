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
import io.github.rawvoid.protovia.codec.ProtoCodec;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Rawvoid
 */
class WireFormatTest {

    @Test
    void int32_150_field1() {
        assertHex("08 96 01", write(w -> w.writeInt32(1, 150)));
        ProtoReader r = reader(bytes(0x08, 0x96, 0x01));
        assertEquals(8, r.readTag());
        assertEquals(150, r.readInt32());
        assertEquals(0, r.readTag());
    }

    @Test
    void string_testing_field2() {
        // 12 07 74 65 73 74 69 6e 67
        byte[] actual = write(w -> w.writeString(2, "testing"));
        assertHex("12 07 74 65 73 74 69 6e 67", actual);
        ProtoReader r = reader(actual);
        assertEquals(WireType.tag(2, WireType.LEN), r.readTag());
        assertEquals("testing", r.readString());
    }

    @Test
    void sint32_negativeOne() {
        byte[] actual = write(w -> w.writeSInt32(1, -1));
        assertHex("08 01", actual);
        ProtoReader r = reader(actual);
        r.readTag();
        assertEquals(-1, r.readSInt32());
    }

    @Test
    void sint64_negativeTwo() {
        byte[] actual = write(w -> w.writeSInt64(1, -2));
        assertHex("08 03", actual);
        ProtoReader r = reader(actual);
        r.readTag();
        assertEquals(-2, r.readSInt64());
    }

    @Test
    void negativeInt32_isTenByteVarint() {
        byte[] actual = write(w -> w.writeInt32(1, -1));
        assertEquals(1 + 10, actual.length);
        assertEquals((byte) 0x08, actual[0]);
        ProtoReader r = reader(actual);
        r.readTag();
        assertEquals(-1, r.readInt32());
    }

    @Test
    void varintRoundTripAllWidths() {
        int[] ints = {0, 1, 127, 128, 0x3FFF, 0x4000, 0x1FFFFF, 0x200000, 0x0FFFFFFF, 0x10000000, -1, Integer.MIN_VALUE};
        for (int value : ints) {
            byte[] actual = write(w -> w.writeUInt32(1, value));
            ProtoReader r = reader(actual);
            r.readTag();
            assertEquals(value, r.readUInt32(), Integer.toHexString(value));
        }
        long[] longs = {0L, 1L, 127L, 128L, 1L << 28, 1L << 35, 1L << 42, 1L << 49, 1L << 56, -1L, Long.MIN_VALUE};
        for (long value : longs) {
            byte[] actual = write(w -> w.writeUInt64(1, value));
            ProtoReader r = reader(actual);
            r.readTag();
            assertEquals(value, r.readUInt64(), Long.toHexString(value));
        }
    }

    @Test
    void varintSlowPathNearLimit() {
        ProtoWriter w = ProtoWriter.growing();
        w.writeUInt32NoTag(300);
        byte[] encoded = w.toByteArray();
        byte[] padded = new byte[encoded.length];
        System.arraycopy(encoded, 0, padded, 0, encoded.length);
        ProtoReader r = new ProtoReader(padded, 0, encoded.length);
        assertEquals(300, r.readUInt32());
        assertEquals(0, r.remaining());
    }

    @Test
    void bool_true() {
        assertHex("08 01", write(w -> w.writeBool(1, true)));
        ProtoReader r = reader(bytes(0x08, 0x01));
        r.readTag();
        assertTrue(r.readBool());
    }

    @Test
    void fixed32_and_float() {
        byte[] fixed = write(w -> w.writeFixed32(1, 0x01020304));
        ProtoReader r = reader(fixed);
        r.readTag();
        assertEquals(0x01020304, r.readFixed32());

        float f = 1.5f;
        byte[] fl = write(w -> w.writeFloat(1, f));
        ProtoReader rf = reader(fl);
        rf.readTag();
        assertEquals(f, rf.readFloat());
    }

    @Test
    void fixed64_and_double() {
        byte[] fixed = write(w -> w.writeFixed64(1, 0x0102030405060708L));
        ProtoReader r = reader(fixed);
        r.readTag();
        assertEquals(0x0102030405060708L, r.readFixed64());

        double d = Math.PI;
        byte[] dbl = write(w -> w.writeDouble(1, d));
        ProtoReader rd = reader(dbl);
        rd.readTag();
        assertEquals(d, rd.readDouble());
    }

    @Test
    void packedRepeatedInt32() {
        // field 4 packed [3, 270] = 22 03 03 8e 02
        int dataSize = CodedSize.int32(3) + CodedSize.int32(270);
        byte[] actual = write(w -> {
            w.writeTag(4, WireType.LEN);
            w.writeUInt32NoTag(dataSize);
            w.writeInt32NoTag(3);
            w.writeInt32NoTag(270);
        });
        assertHex("22 03 03 8e 02", actual);

        ProtoReader r = reader(actual);
        assertEquals(WireType.tag(4, WireType.LEN), r.readTag());
        int old = r.pushLengthDelimited();
        assertEquals(3, r.readInt32());
        assertEquals(270, r.readInt32());
        assertEquals(0, r.remaining());
        r.popLimit(old);
    }

    @Test
    void nestedMessage() {
        NestedCodec nested = new NestedCodec();
        Nested inner = new Nested(150);
        byte[] actual = write(w -> w.writeMessage(3, nested, inner));
        // field 3, len, then field 1 int32 150: 1a 03 08 96 01
        assertHex("1a 03 08 96 01", actual);

        ProtoReader r = reader(actual);
        r.readTag();
        Nested back = r.readMessage(nested);
        assertEquals(150, back.a);
    }

    @Test
    void skipUnknownVarintAndLength() {
        // field 1 string "hi", field 2 int32 1, field 3 string "x"
        byte[] data = write(w -> {
            w.writeString(1, "hi");
            w.writeInt32(2, 1);
            w.writeString(3, "x");
        });
        ProtoReader r = reader(data);
        int tag = r.readTag();
        assertEquals(WireType.tag(1, WireType.LEN), tag);
        assertEquals("hi", r.readString());
        tag = r.readTag();
        assertEquals(WireType.tag(2, WireType.VARINT), tag);
        r.skipField();
        tag = r.readTag();
        assertEquals(WireType.tag(3, WireType.LEN), tag);
        assertEquals("x", r.readString());
        assertEquals(0, r.readTag());
    }

    @Test
    void skipGroup() {
        // start group field 5, inner int32 field 1 = 1, end group field 5
        byte[] data = write(w -> {
            w.writeTag(5, WireType.START_GROUP);
            w.writeInt32(1, 1);
            w.writeTag(5, WireType.END_GROUP);
            w.writeInt32(6, 7);
        });
        ProtoReader r = reader(data);
        int tag = r.readTag();
        assertEquals(WireType.tag(5, WireType.START_GROUP), tag);
        r.skipField();
        tag = r.readTag();
        assertEquals(WireType.tag(6, WireType.VARINT), tag);
        assertEquals(7, r.readInt32());
    }

    @Test
    void readTagRejectsFieldNumberZero() {
        // Tags 1–7 are field 0 with a non-zero wire type; official protobuf rejects them all.
        for (int wireType = 0; wireType <= 5; wireType++) {
            int tag = wireType; // field 0
            ProtoReader r = reader(bytes(tag));
            assertThrows(ProtoException.class, r::readTag, "wireType=" + wireType);
        }
    }

    @Test
    void constructorRejectsOverflowingSlice() {
        byte[] data = new byte[8];
        assertThrows(ProtoException.class, () -> new ProtoReader(data, 4, Integer.MAX_VALUE));
        assertThrows(ProtoException.class, () -> new ProtoReader(data, Integer.MAX_VALUE, 4));
    }

    @Test
    void constructorRejectsNullBufferWithNpe() {
        assertThrows(NullPointerException.class, () -> new ProtoReader(null));
        assertThrows(NullPointerException.class, () -> new ProtoReader(null, 0, 0));
        assertThrows(NullPointerException.class,
            () -> new ProtoReader(null, 0, 0, ProtoReader.DEFAULT_MAX_MESSAGE_SIZE, ProtoReader.DEFAULT_MAX_DEPTH));
    }

    @Test
    void pushLimitRejectsOverflow() {
        byte[] data = new byte[8];
        ProtoReader r = new ProtoReader(data, 4, 4, Integer.MAX_VALUE, 100);
        assertThrows(ProtoException.class, () -> r.pushLimit(Integer.MAX_VALUE));
    }

    @Test
    void malformedVarint() {
        byte[] data = new byte[11];
        for (int i = 0; i < 11; i++) {
            data[i] = (byte) 0x80;
        }
        data[0] = 0x08; // tag field 1 varint
        for (int i = 1; i < 11; i++) {
            data[i] = (byte) 0x80;
        }
        data[10] = (byte) 0x80;
        ProtoReader r = reader(data);
        r.readTag();
        assertThrows(ProtoException.class, r::readInt64);
    }

    @Test
    void truncatedMessage() {
        ProtoReader r = reader(bytes(0x0A, 0x05, 0x61)); // string field 1, len 5, only 1 byte
        r.readTag();
        assertThrows(ProtoException.class, r::readString);
    }

    @Test
    void invalidUtf8() {
        byte[] data = write(w -> {
            w.writeTag(1, WireType.LEN);
            w.writeUInt32NoTag(2);
            w.writeRawByte(0xC3);
            w.writeRawByte(0x28); // invalid continuation
        });
        ProtoReader r = reader(data);
        r.readTag();
        assertThrows(ProtoException.class, r::readString);
    }

    @Test
    void sizeOfStringMatchesWrite() {
        String s = "héllo 世界";
        int size = CodedSize.string(1, s);
        ProtoWriter w = new ProtoWriter(size);
        w.writeString(1, s);
        assertEquals(size, w.finish().length);
    }

    @Test
    void writeStringNoTagMatchesLengthPrefixedUtf8() {
        String s = "héllo 世界 𝄞";
        byte[] utf8 = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ProtoWriter w = ProtoWriter.growing();
        w.writeStringNoTag(s);
        byte[] actual = w.toByteArray();
        int lenSize = CodedSize.uint32(utf8.length);
        assertEquals(lenSize + utf8.length, actual.length);
        ProtoReader r = reader(actual);
        assertEquals(s, r.readString());
    }

    @Test
    void zigzagRoundTrip() {
        int[] values = {0, -1, 1, -2, 2, Integer.MIN_VALUE, Integer.MAX_VALUE};
        for (int v : values) {
            assertEquals(v, ZigZag.decode32(ZigZag.encode32(v)));
        }
        long[] longs = {0L, -1L, 1L, Long.MIN_VALUE, Long.MAX_VALUE};
        for (long v : longs) {
            assertEquals(v, ZigZag.decode64(ZigZag.encode64(v)));
        }
    }

    @Test
    void maxDepthExceeded() {
        NestedCodec codec = new NestedCodec();
        Nested root = new Nested(1);
        Nested cur = root;
        for (int i = 0; i < 8; i++) {
            Nested child = new Nested(i + 2);
            cur.child = child;
            cur = child;
        }
        int size = codec.computeSize(root);
        ProtoWriter w = new ProtoWriter(size);
        codec.writeTo(w, root);
        byte[] bytes = w.finish();
        ProtoReader shallow = new ProtoReader(bytes, 0, bytes.length, 64 * 1024, 3);
        assertThrows(ProtoException.class, () -> codec.readFrom(shallow));
    }

    @Test
    void tenByteVarintValidNonCanonicalAndCanonical() {
        // 80*9 00 -> 0L
        byte[] zeroNonCanonical = bytes(0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x00);
        assertEquals(0L, reader(zeroNonCanonical).readRawVarint64());
        assertEquals(0, reader(zeroNonCanonical).readRawVarint32());

        // FF*9 00 -> Long.MAX_VALUE (63 lower bits set, bit 63 clear)
        byte[] maxLongNonCanonical = bytes(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x00);
        assertEquals(Long.MAX_VALUE, reader(maxLongNonCanonical).readRawVarint64());

        // 80*9 02 -> 0L (bit 1 of 10th byte shifted off)
        byte[] overShiftNonCanonical = bytes(0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x02);
        assertEquals(0L, reader(overShiftNonCanonical).readRawVarint64());

        // Canonical negative 10-byte varints: FF*9 01 -> -1L, 80*9 01 -> Long.MIN_VALUE
        byte[] negOne = bytes(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x01);
        assertEquals(-1L, reader(negOne).readRawVarint64());

        byte[] minLong = bytes(0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x01);
        assertEquals(Long.MIN_VALUE, reader(minLong).readRawVarint64());

        // Verify skipField agrees with readRawVarint64 on valid 10-byte varints (with trailing tag)
        for (byte[] valid : new byte[][]{zeroNonCanonical, maxLongNonCanonical, overShiftNonCanonical, negOne, minLong}) {
            byte[] framed = new byte[1 + valid.length + 2];
            framed[0] = 0x08; // field 1 varint
            System.arraycopy(valid, 0, framed, 1, valid.length);
            framed[1 + valid.length] = 0x10; // field 2 varint
            framed[2 + valid.length] = 0x2A; // 42
            ProtoReader r = reader(framed);
            assertEquals(0x08, r.readTag());
            r.skipField();
            assertEquals(0x10, r.readTag());
            assertEquals(42, r.readInt32());
            assertEquals(0, r.readTag());
        }
    }

    @Test
    void tenByteVarintRejectsMalformedConsistentWithSkip() {
        byte[][] malformedCases = {
            // FF*10
            bytes(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF),
            // 80*9 81
            bytes(0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x81),
            // 80*10
            bytes(0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80),
            // FF*11 (extra byte ensures fast path even with trailing bytes)
            bytes(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x01),
            // 80*9 81 00
            bytes(0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x80, 0x81, 0x00),
        };
        for (byte[] payload : malformedCases) {
            assertThrows(ProtoException.class, () -> reader(payload).readRawVarint64(), toHex(payload));
            assertThrows(ProtoException.class, () -> reader(payload).readRawVarint32(), toHex(payload));

            byte[] framed = new byte[1 + payload.length];
            framed[0] = 0x08;
            System.arraycopy(payload, 0, framed, 1, payload.length);
            ProtoReader r = reader(framed);
            assertEquals(0x08, r.readTag());
            assertThrows(ProtoException.class, r::skipField, "skipField: " + toHex(payload));
        }
    }

    @Test
    void requireOverflowAtMaxIntMessageSize() {
        // field 1 LEN tag (0x0A), length = Integer.MAX_VALUE (FF FF FF FF 07), only 2 payload bytes
        byte[] data = bytes(0x0A, 0xFF, 0xFF, 0xFF, 0xFF, 0x07, 0x01, 0x02);
        ProtoReader r = new ProtoReader(data, 0, data.length, Integer.MAX_VALUE, 100);
        assertEquals(WireType.tag(1, WireType.LEN), r.readTag());
        assertThrows(ProtoException.class, r::skipField);
    }

    @Test
    void writeBytesDirectAndReadOnlyByteBufferPreservesPosition() {
        byte[] payload = {10, 20, 30, 40, 50, 60, 70, 80};

        // DirectByteBuffer with non-zero position and limit < capacity
        ByteBuffer direct = ByteBuffer.allocateDirect(12);
        direct.put(new byte[]{1, 2});
        direct.put(payload);
        direct.put(new byte[]{99, 100});
        direct.position(2);
        direct.limit(10);
        assertFalse(direct.hasArray());

        int directSize = CodedSize.bytes(1, direct);
        ProtoWriter exactDirect = new ProtoWriter(directSize);
        exactDirect.writeBytes(1, direct);
        byte[] exactDirectBytes = exactDirect.finish();
        assertEquals(2, direct.position());
        assertEquals(10, direct.limit());

        ProtoWriter growingDirect = ProtoWriter.growing();
        growingDirect.writeBytes(1, direct);
        byte[] growingDirectBytes = growingDirect.toByteArray();
        assertEquals(2, direct.position());
        assertEquals(10, direct.limit());
        assertArrayEquals(exactDirectBytes, growingDirectBytes);

        ProtoReader rd = reader(exactDirectBytes);
        assertEquals(WireType.tag(1, WireType.LEN), rd.readTag());
        assertArrayEquals(payload, rd.readBytes());
        assertEquals(0, rd.readTag());

        // Read-only ByteBuffer (hasArray() == false)
        ByteBuffer readOnly = ByteBuffer.wrap(new byte[]{0, 1, 2, 3, 4, 5, 6}).asReadOnlyBuffer();
        readOnly.position(2);
        readOnly.limit(6);
        assertFalse(readOnly.hasArray());

        int roSize = CodedSize.bytes(2, readOnly);
        ProtoWriter exactRo = new ProtoWriter(roSize);
        exactRo.writeBytes(2, readOnly);
        byte[] roBytes = exactRo.finish();
        assertEquals(2, readOnly.position());
        assertEquals(6, readOnly.limit());

        ProtoReader rro = reader(roBytes);
        assertEquals(WireType.tag(2, WireType.LEN), rro.readTag());
        assertArrayEquals(new byte[]{2, 3, 4, 5}, rro.readBytes());

        // Empty DirectByteBuffer and empty read-only ByteBuffer
        ByteBuffer emptyDirect = ByteBuffer.allocateDirect(4);
        emptyDirect.position(2).limit(2);
        ProtoWriter emptyW = new ProtoWriter(CodedSize.bytes(3, emptyDirect));
        emptyW.writeBytes(3, emptyDirect);
        byte[] emptyOut = emptyW.finish();
        assertEquals(2, emptyDirect.position());
        assertEquals(2, emptyDirect.limit());
        ProtoReader rempty = reader(emptyOut);
        assertEquals(WireType.tag(3, WireType.LEN), rempty.readTag());
        assertEquals(0, rempty.readBytes().length);

        // Large DirectByteBuffer forcing growth in growing() writer
        ByteBuffer largeDirect = ByteBuffer.allocateDirect(200);
        for (int i = 0; i < 200; i++) {
            largeDirect.put((byte) i);
        }
        largeDirect.flip();
        largeDirect.position(10);
        byte[] largeEncoded = write(w -> w.writeBytes(4, largeDirect));
        assertEquals(10, largeDirect.position());
        assertEquals(200, largeDirect.limit());
        ProtoReader rlarge = reader(largeEncoded);
        assertEquals(WireType.tag(4, WireType.LEN), rlarge.readTag());
        byte[] largeDecoded = rlarge.readBytes();
        assertEquals(190, largeDecoded.length);
        for (int i = 0; i < 190; i++) {
            assertEquals((byte) (i + 10), largeDecoded[i]);
        }
    }

    @Test
    void writeBoolParity() {
        assertHex("08 01", write(w -> w.writeBool(1, true)));
        assertHex("08 00", write(w -> w.writeBool(1, false)));

        int size = CodedSize.bool(1, true) + CodedSize.bool(2, false);
        ProtoWriter exact = new ProtoWriter(size);
        exact.writeBool(1, true);
        exact.writeBool(2, false);
        byte[] encoded = exact.finish();
        assertHex("08 01 10 00", encoded);

        ProtoReader r = reader(encoded);
        assertEquals(WireType.tag(1, WireType.VARINT), r.readTag());
        assertTrue(r.readBool());
        assertEquals(WireType.tag(2, WireType.VARINT), r.readTag());
        assertFalse(r.readBool());
        assertEquals(0, r.readTag());
    }

    @Test
    void fixed32AndFixed64EdgeValues() {
        int[] intEdges = {0, 1, -1, 0x80000000, Integer.MAX_VALUE, 0x01020304, 0xFF00AA55};
        for (int v : intEdges) {
            byte[] encoded = write(w -> w.writeFixed32(1, v));
            assertEquals(5, encoded.length);
            assertEquals((byte) v, encoded[1]);
            assertEquals((byte) (v >>> 8), encoded[2]);
            assertEquals((byte) (v >>> 16), encoded[3]);
            assertEquals((byte) (v >>> 24), encoded[4]);
            ProtoReader r = reader(encoded);
            assertEquals(WireType.tag(1, WireType.I32), r.readTag());
            assertEquals(v, r.readFixed32());
        }

        long[] longEdges = {
            0L,
            1L,
            -1L,
            Long.MIN_VALUE,
            Long.MAX_VALUE,
            0x0102030405060708L,
            Double.doubleToRawLongBits(Double.NaN),
            Double.doubleToRawLongBits(-0.0)
        };
        for (long v : longEdges) {
            byte[] encoded = write(w -> w.writeFixed64(1, v));
            assertEquals(9, encoded.length);
            for (int b = 0; b < 8; b++) {
                assertEquals((byte) (v >>> (b * 8)), encoded[1 + b]);
            }
            ProtoReader r = reader(encoded);
            assertEquals(WireType.tag(1, WireType.I64), r.readTag());
            assertEquals(v, r.readFixed64());
        }

        // Double.NaN and -0.0 bit-exact round-trip
        byte[] doubles = write(w -> {
            w.writeDouble(1, Double.NaN);
            w.writeDouble(2, -0.0);
        });
        ProtoReader rd = reader(doubles);
        assertEquals(WireType.tag(1, WireType.I64), rd.readTag());
        assertTrue(Double.isNaN(rd.readDouble()));
        assertEquals(WireType.tag(2, WireType.I64), rd.readTag());
        assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(rd.readDouble()));
    }

    @Test
    void emptyWriterSharesZeroLengthArrayAndRejectsNegativeWriteRawBytes() {
        byte[] a = new ProtoWriter(0).finish();
        byte[] b = new ProtoWriter(0).finish();
        byte[] c = ProtoWriter.growing().toByteArray();
        assertEquals(0, a.length);
        assertSame(a, b);
        assertSame(a, c);

        ProtoWriter w = ProtoWriter.growing();
        assertThrows(ProtoException.class, () -> w.writeRawBytes(new byte[4], 0, -1));
        assertThrows(ProtoException.class, () -> w.writeBytesNoTag(new byte[4], 0, -1));
    }

    @Test
    void growingWriterWriteStringNoTagAtBufferBoundaryDoesNotCorrupt() {
        // Initial growing() buffer is 64 bytes. At pos = 60 (4 bytes remaining), a 1-char string has
        // maxUtf8 = 3, needed = 4 (fits without growing). Writing the length varint must not trigger
        // require(5) and wipe already-encoded UTF-8 bytes.
        for (String sample : new String[]{"a", "é", "中"}) {
            ProtoWriter w = ProtoWriter.growing();
            for (int i = 0; i < 60; i++) {
                w.writeRawByte(0x55);
            }
            assertEquals(60, w.position());
            w.writeStringNoTag(sample);
            byte[] out = w.toByteArray();
            byte[] utf8 = sample.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(60 + 1 + utf8.length, out.length, sample);
            for (int i = 0; i < 60; i++) {
                assertEquals((byte) 0x55, out[i]);
            }
            assertEquals((byte) utf8.length, out[60], sample);
            for (int i = 0; i < utf8.length; i++) {
                assertEquals(utf8[i], out[61 + i], sample);
            }
            ProtoReader r = new ProtoReader(out, 60, 1 + utf8.length);
            assertEquals(sample, r.readString());
            assertEquals(0, r.remaining());
        }

        // At pos = 63 (1 byte remaining), empty string has needed = 1 (fits without growing).
        ProtoWriter emptyAt63 = ProtoWriter.growing();
        for (int i = 0; i < 63; i++) {
            emptyAt63.writeRawByte(0x33);
        }
        assertEquals(63, emptyAt63.position());
        emptyAt63.writeStringNoTag("");
        byte[] emptyOut = emptyAt63.toByteArray();
        assertEquals(64, emptyOut.length);
        assertEquals((byte) 0x00, emptyOut[63]);
        ProtoReader rempty = new ProtoReader(emptyOut, 63, 1);
        assertEquals("", rempty.readString());
        assertEquals(0, rempty.remaining());

        // At pos = 61 (3 bytes remaining), 1-char string needs 4 bytes -> grows before encoding.
        ProtoWriter growAt61 = ProtoWriter.growing();
        for (int i = 0; i < 61; i++) {
            growAt61.writeRawByte(0x22);
        }
        growAt61.writeStringNoTag("中");
        byte[] growOut = growAt61.toByteArray();
        assertEquals(61 + 4, growOut.length);
        ProtoReader rgrow = new ProtoReader(growOut, 61, 4);
        assertEquals("中", rgrow.readString());
        assertEquals(0, rgrow.remaining());
    }

    @Test
    void writeBytesSlicedDirectReadOnlyAndHeapBuffersPreservePositionAndLimit() {
        byte[] raw = new byte[16];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = (byte) (i + 1);
        }
        byte[] expected = {6, 7, 8, 9}; // raw indices 5..8

        // Sliced DirectByteBuffer with non-zero position and limit < capacity
        ByteBuffer baseDirect = ByteBuffer.allocateDirect(16);
        baseDirect.put(raw);
        baseDirect.position(3).limit(11);
        ByteBuffer slicedDirect = baseDirect.slice();
        slicedDirect.position(2).limit(6);
        assertFalse(slicedDirect.hasArray());

        // Sliced read-only ByteBuffer with non-zero position and limit < capacity
        ByteBuffer slicedReadOnly = ByteBuffer.wrap(raw).position(3).limit(11).slice().asReadOnlyBuffer();
        slicedReadOnly.position(2).limit(6);
        assertFalse(slicedReadOnly.hasArray());

        // Sliced heap ByteBuffer with non-zero arrayOffset() and non-zero position()
        ByteBuffer slicedHeap = ByteBuffer.wrap(raw).position(3).limit(11).slice();
        slicedHeap.position(2).limit(6);
        assertTrue(slicedHeap.hasArray());
        assertEquals(3, slicedHeap.arrayOffset());

        for (ByteBuffer buf : new ByteBuffer[]{slicedDirect, slicedReadOnly, slicedHeap}) {
            // Exact writer
            int size = CodedSize.bytes(1, buf);
            ProtoWriter exact = new ProtoWriter(size);
            exact.writeBytes(1, buf);
            byte[] exactBytes = exact.finish();
            assertEquals(2, buf.position());
            assertEquals(6, buf.limit());

            // Growing writer
            ProtoWriter growing = ProtoWriter.growing();
            growing.writeBytes(1, buf);
            byte[] growingBytes = growing.toByteArray();
            assertEquals(2, buf.position());
            assertEquals(6, buf.limit());

            assertArrayEquals(exactBytes, growingBytes);
            ProtoReader r = reader(exactBytes);
            assertEquals(WireType.tag(1, WireType.LEN), r.readTag());
            assertArrayEquals(expected, r.readBytes());
            assertEquals(0, r.readTag());
        }
    }

    private static byte[] write(WriterOp op) {
        ProtoWriter writer = ProtoWriter.growing();
        op.write(writer);
        return writer.toByteArray();
    }

    private static ProtoReader reader(byte[] data) {
        return new ProtoReader(data);
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    private static void assertHex(String hex, byte[] actual) {
        assertArrayEquals(parseHex(hex), actual, () -> toHex(actual));
    }

    static byte[] parseHex(String hex) {
        String[] parts = hex.trim().split("\\s+");
        byte[] out = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return out;
    }

    static String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(String.format("%02x", data[i] & 0xFF));
        }
        return sb.toString();
    }

    @FunctionalInterface
    interface WriterOp {
        void write(ProtoWriter w);
    }

    static final class Nested {
        final int a;
        Nested child;

        Nested(int a) {
            this.a = a;
        }
    }

    static final class NestedCodec implements ProtoCodec<Nested> {
        @Override
        public Class<Nested> type() {
            return Nested.class;
        }

        @Override
        public int computeSize(Nested value) {
            int size = 0;
            if (value.a != 0) {
                size += CodedSize.int32(1, value.a);
            }
            if (value.child != null) {
                size += CodedSize.message(2, this, value.child);
            }
            return size;
        }

        @Override
        public void writeTo(ProtoWriter writer, Nested value) {
            if (value.a != 0) {
                writer.writeInt32(1, value.a);
            }
            if (value.child != null) {
                writer.writeMessage(2, this, value.child);
            }
        }

        @Override
        public Nested readFrom(ProtoReader reader) {
            int a = 0;
            Nested child = null;
            int tag;
            while ((tag = reader.readTag()) != 0) {
                switch (tag) {
                    case 8 -> a = reader.readInt32();
                    case 18 -> child = reader.readMessage(this);
                    default -> reader.skipField();
                }
            }
            Nested n = new Nested(a);
            n.child = child;
            return n;
        }
    }
}
