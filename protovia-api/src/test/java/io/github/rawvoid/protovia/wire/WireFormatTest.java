package io.github.rawvoid.protovia.wire;

import io.github.rawvoid.protovia.ProtoException;
import io.github.rawvoid.protovia.codec.ProtoCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        int old = r.beginPacked();
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
